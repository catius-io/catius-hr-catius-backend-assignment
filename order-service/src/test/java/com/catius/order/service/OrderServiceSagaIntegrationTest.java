package com.catius.order.service;

import com.catius.order.client.exception.InsufficientStockException;
import com.catius.order.client.exception.ProductNotFoundException;
import com.catius.order.domain.Order;
import com.catius.order.domain.OrderStatus;
import com.catius.order.messaging.OrderConfirmedEvent;
import com.catius.order.repository.OrderRepository;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Saga 통합 — 정상 흐름 + reserve 자체 실패. 실제 Kafka publisher + EmbeddedKafka 사용.
 *
 * publish 실패 보상 시나리오는 OrderServiceSagaPublishFailureTest 로 분리:
 * - @MockBean OrderEventPublisher 가 컨텍스트를 별도로 만들어 WireMock 인스턴스/포트가 static
 *   stubFor 와 매칭되지 않는 함정 회피.
 * - 본 클래스는 *real publisher* 위주의 검증, 분리 클래스는 *mocked publisher* 위주.
 */
@SagaIntegrationTest
@EmbeddedKafka(partitions = 1, topics = {"order.order-confirmed.v1"})
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
@DisplayName("OrderService Saga 통합 — 정상 / reserve 실패 (real publisher + EmbeddedKafka)")
class OrderServiceSagaIntegrationTest {

    private static final long PRODUCT_ID = 9001L;
    private static final String RESERVE_PATH = "/api/v1/inventory/reserve";
    private static final String RELEASE_PATH = "/api/v1/inventory/release";
    private static final String TOPIC = "order.order-confirmed.v1";

    @Autowired
    private OrderService service;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    private Consumer<String, OrderConfirmedEvent> consumer;

    @BeforeEach
    void setUp() {
        WireMock.reset();
        orderRepository.deleteAll();

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(
                "saga-test-" + UUID.randomUUID(), "true", embeddedKafka);
        consumerProps.put("key.deserializer", StringDeserializer.class);
        consumerProps.put("value.deserializer", JsonDeserializer.class);
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        consumerProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, OrderConfirmedEvent.class.getName());
        consumer = new KafkaConsumer<>(consumerProps);
        embeddedKafka.consumeFromAnEmbeddedTopic(consumer, TOPIC);
    }

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.close();
        }
    }

    @Nested
    @DisplayName("A. 정상 흐름")
    class HappyPath {

        @Test
        void reserve_OK_confirm_OK_publish_OK_시_Order_는_CONFIRMED_와_이벤트_1건() {
            stubFor(post(urlEqualTo(RESERVE_PATH)).willReturn(aResponse().withStatus(204)));

            Order created = service.createOrder(PRODUCT_ID, 3);

            // Order 상태
            Order persisted = orderRepository.findById(created.getId()).orElseThrow();
            assertThat(persisted.getStatus()).isEqualTo(OrderStatus.CONFIRMED);

            // Kafka 이벤트
            ConsumerRecord<String, OrderConfirmedEvent> record =
                    KafkaTestUtils.getSingleRecord(consumer, TOPIC, Duration.ofSeconds(5));
            assertThat(record.value().orderId()).isEqualTo(created.getId());
            assertThat(record.value().productId()).isEqualTo(PRODUCT_ID);
            assertThat(record.value().quantity()).isEqualTo(3);
            assertThat(record.value().eventId()).isNotBlank();

            // release 호출되지 않음 (정상 흐름)
            WireMock.verify(0, postRequestedFor(urlEqualTo(RELEASE_PATH)));
        }
    }

    @Nested
    @DisplayName("B. reserve 자체 실패 — 보상 없음")
    class ReserveFailed {

        @Test
        void reserve_409_INSUFFICIENT_STOCK_시_Order_는_저장되지_않고_release_호출_없음() {
            stubFor(post(urlEqualTo(RESERVE_PATH)).willReturn(aResponse()
                    .withStatus(409)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                            {"code":"INSUFFICIENT_STOCK","message":"Insufficient stock"}
                            """)));

            assertThatThrownBy(() -> service.createOrder(PRODUCT_ID, 999))
                    .isInstanceOf(InsufficientStockException.class);

            assertThat(orderRepository.count()).isZero();
            WireMock.verify(0, postRequestedFor(urlEqualTo(RELEASE_PATH)));
        }

        @Test
        void reserve_404_PRODUCT_NOT_FOUND_시_Order_는_저장되지_않고_release_호출_없음() {
            stubFor(post(urlEqualTo(RESERVE_PATH)).willReturn(aResponse()
                    .withStatus(404)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                            {"code":"PRODUCT_NOT_FOUND","message":"Product not found"}
                            """)));

            assertThatThrownBy(() -> service.createOrder(PRODUCT_ID, 1))
                    .isInstanceOf(ProductNotFoundException.class);

            assertThat(orderRepository.count()).isZero();
            WireMock.verify(0, postRequestedFor(urlEqualTo(RELEASE_PATH)));
        }
    }
}
