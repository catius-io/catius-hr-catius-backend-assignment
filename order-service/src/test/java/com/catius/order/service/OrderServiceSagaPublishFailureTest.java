package com.catius.order.service;

import com.catius.order.domain.Order;
import com.catius.order.domain.OrderStatus;
import com.catius.order.messaging.OrderEventPublishException;
import com.catius.order.messaging.OrderEventPublisher;
import com.catius.order.repository.OrderRepository;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.test.context.TestPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;

/**
 * Saga 의 publish 실패 → 보상 (release) 시나리오. publisher 를 @MockBean 으로 주입해 실패를 강제.
 *
 * 별도 클래스로 분리한 이유:
 * - @MockBean 은 ApplicationContext 를 새로 빌드하는데, @AutoConfigureWireMock 도 새 인스턴스를
 *   부팅하므로 다른 테스트 클래스의 static stubFor 와 인스턴스가 어긋나는 함정 발생.
 * - 본 클래스는 자기 컨텍스트 안에서만 stub/mock 을 다루어 격리.
 *
 * 본 클래스는 publisher 를 mock 하므로 @EmbeddedKafka 불필요. 실제 Kafka 토픽 검증은
 * OrderServiceSagaIntegrationTest (real publisher) 가 책임.
 */
@SpringBootTest(properties = {
        "inventory.base-url=http://localhost:${wiremock.server.port}",
        "spring.kafka.listener.auto-startup=false"
})
@AutoConfigureWireMock(port = 0)
@TestPropertySource(properties = {
        "resilience4j.retry.instances.inventoryClient.max-attempts=1",
        "resilience4j.circuitbreaker.instances.inventoryClient.minimum-number-of-calls=100"
})
@DisplayName("OrderService Saga 통합 — publish 실패 보상 (mocked publisher)")
class OrderServiceSagaPublishFailureTest {

    private static final long PRODUCT_ID = 9001L;
    private static final String RESERVE_PATH = "/api/v1/inventory/reserve";
    private static final String RELEASE_PATH = "/api/v1/inventory/release";

    @Autowired
    private OrderService service;

    @Autowired
    private OrderRepository orderRepository;

    @MockBean
    private OrderEventPublisher publisher;

    @BeforeEach
    void setUp() {
        WireMock.reset();
        orderRepository.deleteAll();
    }

    @Test
    void publish_실패_시_release_호출_되고_Order_는_COMPENSATED() {
        stubFor(post(urlEqualTo(RESERVE_PATH)).willReturn(aResponse().withStatus(204)));
        stubFor(post(urlEqualTo(RELEASE_PATH)).willReturn(aResponse().withStatus(204)));

        willThrow(new OrderEventPublishException("publish failed", new RuntimeException()))
                .given(publisher).publishOrderConfirmed(any());

        assertThatThrownBy(() -> service.createOrder(PRODUCT_ID, 2))
                .isInstanceOf(OrderEventPublishException.class);

        // release 호출됨 (보상 트리거)
        WireMock.verify(exactly(1), postRequestedFor(urlEqualTo(RELEASE_PATH)));

        // Order 는 COMPENSATED 로 마킹
        Order persisted = orderRepository.findAll().get(0);
        assertThat(persisted.getStatus()).isEqualTo(OrderStatus.COMPENSATED);
    }

    @Test
    void publish_실패_와_release_도_실패_시_원인_예외_가_우선_throw_되고_Order_는_CONFIRMED_상태_유지() {
        stubFor(post(urlEqualTo(RESERVE_PATH)).willReturn(aResponse().withStatus(204)));
        // release 가 5xx 로 실패 — 보상 자체 실패 시뮬레이션
        stubFor(post(urlEqualTo(RELEASE_PATH)).willReturn(aResponse().withStatus(500)));

        willThrow(new OrderEventPublishException("publish failed", new RuntimeException()))
                .given(publisher).publishOrderConfirmed(any());

        // 호출자에게는 *원인* 예외(publish 실패) 를 그대로 알림 — release 실패 정보는 로그
        assertThatThrownBy(() -> service.createOrder(PRODUCT_ID, 2))
                .isInstanceOf(OrderEventPublishException.class);

        // release 시도는 했음
        WireMock.verify(exactly(1), postRequestedFor(urlEqualTo(RELEASE_PATH)));

        // Order 는 CONFIRMED 인 채로 남음 — 운영 정정 영역.
        // (compensate() 가 release 성공 후에야 status 변경하므로, release 실패 시 도메인 상태 변경 안 됨)
        Order persisted = orderRepository.findAll().get(0);
        assertThat(persisted.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }
}
