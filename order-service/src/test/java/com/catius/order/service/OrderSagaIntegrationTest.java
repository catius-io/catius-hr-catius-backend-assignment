package com.catius.order.service;

import com.catius.order.domain.Order;
import com.catius.order.domain.OrderItem;
import com.catius.order.domain.PendingCompensation;
import com.catius.order.domain.PendingCompensationReason;
import com.catius.order.domain.PendingCompensationStatus;
import com.catius.order.repository.OrderRepository;
import com.catius.order.repository.PendingCompensationRepository;
import com.catius.order.service.exception.AmbiguousInventoryException;
import com.catius.order.service.exception.EventPublishException;
import com.catius.order.service.exception.InsufficientStockException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EmbeddedKafka(
        topics = {"order.order-confirmed.v1", "inventory.release-requested.v1"},
        partitions = 1,
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
class OrderSagaIntegrationTest {

    private static final String CONFIRMED_TOPIC = "order.order-confirmed.v1";
    private static final String RELEASE_TOPIC = "inventory.release-requested.v1";

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(options().dynamicPort())
            .build();

    @DynamicPropertySource
    static void inventoryUrl(DynamicPropertyRegistry registry) {
        registry.add("inventory.base-url", wireMock::baseUrl);
    }

    @Autowired
    OrderSagaService sagaService;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    PendingCompensationRepository pendingRepository;

    @Autowired
    CompensationRecoveryRunner recoveryRunner;

    @Autowired
    EmbeddedKafkaBroker broker;

    @Autowired
    ObjectMapper objectMapper;

    @SpyBean
    OrderEventPublisher publisher;

    @SpyBean
    OrderRepository orderRepoSpy;

    @Autowired
    MeterRegistry meterRegistry;

    private Consumer<String, String> consumer;

    @BeforeEach
    void setUp() {
        wireMock.resetAll();
        pendingRepository.deleteAllInBatch();
        orderRepository.deleteAll();

        // 신규 consumer는 broker의 모든 메시지를 읽지만, saga가 UUID로 생성한 unique orderId로
        // assertion에서 필터링 — inter-test 누설된 메시지는 무해 (다른 orderId).
        Map<String, Object> props = new HashMap<>(KafkaTestUtils.consumerProps(
                "saga-test-" + UUID.randomUUID(), "true", broker));
        consumer = new DefaultKafkaConsumerFactory<>(
                props, new StringDeserializer(), new StringDeserializer()).createConsumer();
        broker.consumeFromEmbeddedTopics(consumer, CONFIRMED_TOPIC, RELEASE_TOPIC);
    }

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.close();
        }
    }

    @Test
    void singleItem_happy_persistsOrderAndPublishesConfirmed() {
        stubSuccess(1001L);

        Order order = sagaService.createOrder(100L, List.of(OrderItem.of(1001L, 2)));

        assertThat(orderRepository.findById(order.getOrderId())).isPresent();
        assertEquals(PendingCompensationStatus.COMPLETED,
                pendingRepository.findById(order.getOrderId()).orElseThrow().getStatus());
        awaitConfirmedFor(order.getOrderId());
        assertNoReleaseFor(order.getOrderId());
    }

    @Test
    void multiItem_happy_publishesSingleConfirmed_andCallsReserveExactlyN() {
        stubSuccess(1001L);
        stubSuccess(1002L);
        stubSuccess(1003L);
        stubSuccess(1004L);
        stubSuccess(1005L);

        Order order = sagaService.createOrder(100L, List.of(
                OrderItem.of(1001L, 1), OrderItem.of(1002L, 1), OrderItem.of(1003L, 1),
                OrderItem.of(1004L, 1), OrderItem.of(1005L, 1)));

        assertThat(orderRepository.findById(order.getOrderId())).isPresent();
        wireMock.verify(5, postRequestedFor(urlEqualTo("/api/v1/inventory/reserve")));
        awaitConfirmedFor(order.getOrderId());
        assertNoReleaseFor(order.getOrderId());
    }

    @Test
    void firstReserve_insufficient_doesNotPersistOrder_andDoesNotPublishCompensation() {
        stubInsufficient(1001L);

        assertThrows(InsufficientStockException.class,
                () -> sagaService.createOrder(100L, List.of(OrderItem.of(1001L, 5))));

        assertEquals(0, orderRepository.count());
        List<PendingCompensation> all = pendingRepository.findAll();
        assertEquals(1, all.size());
        PendingCompensation comp = all.get(0);
        assertEquals(PendingCompensationStatus.COMPLETED, comp.getStatus());
        assertTrue(comp.getAttemptedItems().isEmpty());
        assertNoConfirmedFor(comp.getOrderId());
        assertNoReleaseFor(comp.getOrderId());
    }

    @Test
    void thirdReserve_insufficient_publishesCompensationForFirstTwo_andDoesNotPersistOrder() throws Exception {
        stubSuccess(1001L);
        stubSuccess(1002L);
        stubInsufficient(9999L);

        assertThrows(InsufficientStockException.class,
                () -> sagaService.createOrder(100L, List.of(
                        OrderItem.of(1001L, 1), OrderItem.of(1002L, 1),
                        OrderItem.of(9999L, 1), OrderItem.of(1004L, 1), OrderItem.of(1005L, 1))));

        assertEquals(0, orderRepository.count());
        List<PendingCompensation> all = pendingRepository.findAll();
        assertEquals(1, all.size());
        PendingCompensation comp = all.get(0);
        assertEquals(PendingCompensationStatus.PUBLISHED, comp.getStatus());
        assertEquals(PendingCompensationReason.EXPLICIT_FAILURE, comp.getReason());
        // 3번째에서 fail-fast → 4·5번째 호출 안 됨
        wireMock.verify(3, postRequestedFor(urlEqualTo("/api/v1/inventory/reserve")));

        JsonNode body = awaitReleaseFor(comp.getOrderId());
        assertEquals(2, body.get("items").size());
        assertEquals("EXPLICIT_FAILURE", body.get("reason").asText());
    }

    @Test
    void thirdReserve_serverError_publishesAmbiguousCompensationIncludingThirdItem() throws Exception {
        stubSuccess(1001L);
        stubSuccess(1002L);
        stubServerError(1003L);

        assertThrows(AmbiguousInventoryException.class,
                () -> sagaService.createOrder(100L, List.of(
                        OrderItem.of(1001L, 1), OrderItem.of(1002L, 1),
                        OrderItem.of(1003L, 1), OrderItem.of(1004L, 1), OrderItem.of(1005L, 1))));

        assertEquals(0, orderRepository.count());
        List<PendingCompensation> all = pendingRepository.findAll();
        assertEquals(1, all.size());
        PendingCompensation comp = all.get(0);
        assertEquals(PendingCompensationStatus.PUBLISHED, comp.getStatus());
        assertEquals(PendingCompensationReason.AMBIGUOUS_FAILURE, comp.getReason());

        JsonNode body = awaitReleaseFor(comp.getOrderId());
        // ambiguous: items 1+2+3 모두 포함 (현재 호출 item 보존)
        assertEquals(3, body.get("items").size());
    }

    @Test
    void persistFailure_afterAllReserves_publishesCompensationWithAllItems() throws Exception {
        stubSuccess(1001L);
        stubSuccess(1002L);

        doThrow(new RuntimeException("forced persist failure"))
                .when(orderRepoSpy).save(any(Order.class));

        assertThrows(RuntimeException.class,
                () -> sagaService.createOrder(100L, List.of(
                        OrderItem.of(1001L, 1), OrderItem.of(1002L, 1))));

        assertEquals(0, orderRepository.count());
        List<PendingCompensation> all = pendingRepository.findAll();
        assertEquals(1, all.size());
        PendingCompensation comp = all.get(0);
        assertEquals(PendingCompensationStatus.PUBLISHED, comp.getStatus());
        assertEquals(PendingCompensationReason.PERSIST_FAILURE, comp.getReason());

        JsonNode body = awaitReleaseFor(comp.getOrderId());
        assertEquals(2, body.get("items").size());
    }

    @Test
    void confirmedPublishFailure_returns201_pendingCompleted_noCompensation() {
        stubSuccess(1001L);

        doThrow(new EventPublishException("forced confirmed failure", null))
                .when(publisher).publishConfirmed(any());

        double before = meterRegistry.counter("order.saga.confirmed_dispatch_failed").count();

        // 사용자 응답 정상 — Order persisted + COMPLETED. ADR-007 회귀 방어선
        Order order = sagaService.createOrder(100L, List.of(OrderItem.of(1001L, 1)));

        assertThat(orderRepository.findById(order.getOrderId())).isPresent();
        PendingCompensation comp = pendingRepository.findById(order.getOrderId()).orElseThrow();
        assertEquals(PendingCompensationStatus.COMPLETED, comp.getStatus());
        assertNoReleaseFor(order.getOrderId());

        // 메트릭 카운터 증가 (ADR-007)
        assertThat(meterRegistry.counter("order.saga.confirmed_dispatch_failed").count())
                .isGreaterThan(before);

        // 부팅 복구도 이 row를 건드리지 않아야 함
        recoveryRunner.recover();
        assertNoReleaseFor(order.getOrderId());
        assertEquals(PendingCompensationStatus.COMPLETED,
                pendingRepository.findById(order.getOrderId()).orElseThrow().getStatus());
    }

    @Test
    void compensationPublishFailure_marksDispatchFailed_recoveryRepublishes() throws Exception {
        stubSuccess(1001L);
        stubInsufficient(9999L);

        // 첫 발행은 실패, 두 번째(복구 시점)는 정상
        doThrow(new EventPublishException("first publish fails", null))
                .doCallRealMethod()
                .when(publisher).publishCompensation(any());

        double before = meterRegistry.counter("order.saga.compensation_dispatch_failed").count();

        assertThrows(InsufficientStockException.class,
                () -> sagaService.createOrder(100L, List.of(
                        OrderItem.of(1001L, 1), OrderItem.of(9999L, 1))));

        List<PendingCompensation> all = pendingRepository.findAll();
        assertEquals(1, all.size());
        PendingCompensation comp = all.get(0);
        assertEquals(PendingCompensationStatus.DISPATCH_FAILED, comp.getStatus());

        // 메트릭 카운터 증가 (ADR-007)
        assertThat(meterRegistry.counter("order.saga.compensation_dispatch_failed").count())
                .isGreaterThan(before);

        recoveryRunner.recover();

        assertEquals(PendingCompensationStatus.PUBLISHED,
                pendingRepository.findById(comp.getOrderId()).orElseThrow().getStatus());
        JsonNode body = awaitReleaseFor(comp.getOrderId());
        assertEquals(1, body.get("items").size());
        assertEquals(1001L, body.get("items").get(0).get("productId").asLong());
    }

    @Test
    void createOrder_rejectsEmptyItems_withoutCreatingPendingRow() {
        assertThrows(IllegalArgumentException.class,
                () -> sagaService.createOrder(100L, List.of()));

        // saga 진입 자체를 차단 — pending_compensations row도 생성 안 됨
        assertEquals(0, pendingRepository.count());
        assertEquals(0, orderRepository.count());
    }

    @Test
    void createOrder_rejectsDuplicateProductIds_withoutCreatingPendingRow() {
        assertThrows(IllegalArgumentException.class,
                () -> sagaService.createOrder(100L, List.of(
                        OrderItem.of(1001L, 1), OrderItem.of(1001L, 2))));

        assertEquals(0, pendingRepository.count());
        assertEquals(0, orderRepository.count());
    }

    @Test
    void recovery_inProgressRow_marksCrashRecoveryAndPublishes() throws Exception {
        // pre-seed an IN_PROGRESS row (reserve 흐름 중간 crash 시뮬)
        String orderId = UUID.randomUUID().toString();
        PendingCompensation seed = PendingCompensation.start(orderId);
        seed.appendAttempted(new com.catius.order.domain.AttemptedItem(1001L, 2));
        seed.appendAttempted(new com.catius.order.domain.AttemptedItem(1002L, 1));
        pendingRepository.save(seed);

        recoveryRunner.recover();

        PendingCompensation after = pendingRepository.findById(orderId).orElseThrow();
        assertEquals(PendingCompensationStatus.PUBLISHED, after.getStatus());
        assertEquals(PendingCompensationReason.CRASH_RECOVERY, after.getReason());

        JsonNode body = awaitReleaseFor(orderId);
        assertEquals(2, body.get("items").size());
        assertEquals("CRASH_RECOVERY", body.get("reason").asText());
    }

    // ---- helpers ----

    private void stubSuccess(long productId) {
        wireMock.stubFor(post(urlEqualTo("/api/v1/inventory/reserve"))
                .withRequestBody(containing("\"productId\":" + productId))
                .willReturn(okJson("{\"orderId\":\"any\",\"productId\":" + productId
                        + ",\"quantity\":1,\"state\":\"RESERVED\"}")));
    }

    private void stubInsufficient(long productId) {
        wireMock.stubFor(post(urlEqualTo("/api/v1/inventory/reserve"))
                .withRequestBody(containing("\"productId\":" + productId))
                .willReturn(jsonResponse("{\"code\":\"INSUFFICIENT_STOCK\",\"message\":\"...\"}", 409)));
    }

    private void stubServerError(long productId) {
        wireMock.stubFor(post(urlEqualTo("/api/v1/inventory/reserve"))
                .withRequestBody(containing("\"productId\":" + productId))
                .willReturn(serverError()));
    }

    private JsonNode awaitReleaseFor(String orderId) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, String> r : records.records(RELEASE_TOPIC)) {
                if (r.value().contains("\"orderId\":\"" + orderId + "\"")) {
                    return objectMapper.readTree(r.value());
                }
            }
        }
        throw new AssertionError("no release event found for orderId=" + orderId);
    }

    private void assertNoReleaseFor(String orderId) {
        long deadline = System.currentTimeMillis() + 500;
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
            for (ConsumerRecord<String, String> r : records.records(RELEASE_TOPIC)) {
                if (r.value().contains("\"orderId\":\"" + orderId + "\"")) {
                    throw new AssertionError("unexpected release event for orderId=" + orderId
                            + ": " + r.value());
                }
            }
        }
    }

    private void awaitConfirmedFor(String orderId) {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, String> r : records.records(CONFIRMED_TOPIC)) {
                if (r.value().contains("\"orderId\":\"" + orderId + "\"")) {
                    return;
                }
            }
        }
        throw new AssertionError("no confirmed event found for orderId=" + orderId);
    }

    private void assertNoConfirmedFor(String orderId) {
        long deadline = System.currentTimeMillis() + 500;
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
            for (ConsumerRecord<String, String> r : records.records(CONFIRMED_TOPIC)) {
                if (r.value().contains("\"orderId\":\"" + orderId + "\"")) {
                    throw new AssertionError("unexpected confirmed event for orderId=" + orderId);
                }
            }
        }
    }
}
