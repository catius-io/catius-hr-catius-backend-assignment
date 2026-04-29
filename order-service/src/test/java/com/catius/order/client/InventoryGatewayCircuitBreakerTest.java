package com.catius.order.client;

import com.catius.order.client.exception.InsufficientStockException;
import com.catius.order.client.exception.InventoryClientException;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.test.context.TestPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CircuitBreaker 동작에 집중한 별도 테스트.
 *
 * 본 테스트는 CB 가 *진짜 OPEN/CLOSED* 로 전이되는 것을 검증해야 하므로 sliding-window 가 작게
 * 설정되어야 한다. retry 검증용 InventoryGatewayWireMockTest 의 큰 window (100) 와 충돌하므로
 * 별도 클래스로 분리.
 */
@SpringBootTest(properties = {
        "inventory.base-url=http://localhost:${wiremock.server.port}",
        "spring.kafka.listener.auto-startup=false"
})
@AutoConfigureWireMock(port = 0)
@TestPropertySource(properties = {
        // CB 가 빠르게 OPEN/CLOSED 로 전이되도록 작은 window. retry 도 1회로 압축해 호출 수 통제.
        "resilience4j.circuitbreaker.instances.inventoryClient.sliding-window-size=4",
        "resilience4j.circuitbreaker.instances.inventoryClient.minimum-number-of-calls=4",
        "resilience4j.circuitbreaker.instances.inventoryClient.failure-rate-threshold=50",
        "resilience4j.circuitbreaker.instances.inventoryClient.wait-duration-in-open-state=10s",
        "resilience4j.retry.instances.inventoryClient.max-attempts=1"
})
@DisplayName("InventoryGateway — CircuitBreaker 동작 검증 (WireMock)")
class InventoryGatewayCircuitBreakerTest {

    private static final long PRODUCT_ID = 9001L;
    private static final String RESERVE_PATH = "/api/v1/inventory/reserve";
    private static final String CB_NAME = "inventoryClient";

    @Autowired
    private InventoryGateway gateway;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void setUp() {
        WireMock.reset();
        // 매 테스트마다 CB 상태 격리 — 다른 테스트가 남긴 카운터/state 가 본 테스트에 영향 주지 않게.
        circuitBreakerRegistry.circuitBreaker(CB_NAME).reset();
    }

    @Nested
    @DisplayName("OPEN 전이")
    class OpenTransition {

        @Test
        void 일시실패_5xx_가_threshold_를_넘으면_CB_OPEN_되어_이후_호출은_inventory_까지_도달하지_않음() {
            stubFor(post(urlEqualTo(RESERVE_PATH)).willReturn(aResponse().withStatus(500)));

            // window=4, threshold=50% → 4번 중 2번 이상 실패하면 OPEN.
            // 4번 모두 실패시켜 확실히 OPEN 트리거.
            for (int i = 0; i < 4; i++) {
                assertThatThrownBy(() -> gateway.reserve(PRODUCT_ID, 1))
                        .isInstanceOf(InventoryClientException.class);
            }

            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(CB_NAME);
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

            // OPEN 상태에서 다음 호출은 inventory 에 도달하지 않고 빠른 실패.
            // R4J 의 CallNotPermittedException 이 InventoryClientException 으로 wrap 안 되고 그대로 throw.
            // (Gateway 의 wrapTransportErrors 는 FeignException 만 잡으므로 통과.)
            WireMock.resetAllRequests();
            assertThatThrownBy(() -> gateway.reserve(PRODUCT_ID, 1))
                    .isInstanceOf(CallNotPermittedException.class);

            verify(exactly(0), postRequestedFor(urlEqualTo(RESERVE_PATH)));
        }
    }

    @Nested
    @DisplayName("ignore-exceptions 정책 회귀 가드")
    class IgnoreExceptionsPolicy {

        @Test
        void 영구실패_409_는_CB_failure_카운트에서_제외되어_OPEN_트리거하지_않음() {
            stubFor(post(urlEqualTo(RESERVE_PATH)).willReturn(aResponse()
                    .withStatus(409)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                            {"code":"INSUFFICIENT_STOCK","message":"Insufficient stock"}
                            """)));

            // window=4 의 두 배인 8번을 모두 409 로 받아도 — ignore-exceptions 정책이 동작하면 CB CLOSED 유지.
            for (int i = 0; i < 8; i++) {
                assertThatThrownBy(() -> gateway.reserve(PRODUCT_ID, 1))
                        .isInstanceOf(InsufficientStockException.class);
            }

            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(CB_NAME);
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

            // 정책이 깨졌다면 (409 가 failure 로 카운트되면) 이미 OPEN 상태일 것 — 다음 호출이 CallNotPermitted 로 떨어졌을 것.
            // 회귀 가드: yml 의 ignore-exceptions 항목이 사라지면 본 테스트가 빨간불.
        }
    }
}
