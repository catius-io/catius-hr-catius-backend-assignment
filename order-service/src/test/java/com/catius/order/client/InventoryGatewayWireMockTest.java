package com.catius.order.client;

import com.catius.order.client.exception.InsufficientStockException;
import com.catius.order.client.exception.InventoryClientException;
import com.catius.order.client.exception.ProductNotFoundException;
import com.catius.order.testsupport.WireMockInventoryTest;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import static com.catius.order.testsupport.InventoryEndpoints.RESERVE_PATH;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@WireMockInventoryTest
@TestPropertySource(properties = {
        // timeout 시나리오를 빠르게 재현하기 위한 read-timeout 압축
        "spring.cloud.openfeign.client.config.inventoryClient.read-timeout=300",
        // 빠른 테스트를 위해 retry wait 압축
        "resilience4j.retry.instances.inventoryClient.wait-duration=10ms",
        // CB 가 본 retry 테스트를 trigger 하지 않도록 sliding window 를 충분히 크게
        "resilience4j.circuitbreaker.instances.inventoryClient.sliding-window-size=100",
        "resilience4j.circuitbreaker.instances.inventoryClient.minimum-number-of-calls=100"
})
@DisplayName("InventoryGateway — Resilience4j (retry/CB) 적용 검증 (WireMock)")
class InventoryGatewayWireMockTest {

    private static final long PRODUCT_ID = 9001L;

    @Autowired
    private InventoryGateway gateway;

    @BeforeEach
    void resetWireMock() {
        WireMock.reset();
    }

    @Nested
    @DisplayName("retry 분기 — 영구/일시 실패")
    class RetryPolicy {

        @Test
        void 정상_204_는_단_한번만_호출() {
            stubFor(post(urlEqualTo(RESERVE_PATH)).willReturn(aResponse().withStatus(204)));

            assertThatNoException().isThrownBy(() -> gateway.reserve(PRODUCT_ID, 3));

            verify(exactly(1), postRequestedFor(urlEqualTo(RESERVE_PATH)));
        }

        @Test
        void 영구실패_409_는_재시도_없이_즉시_실패() {
            stubFor(post(urlEqualTo(RESERVE_PATH)).willReturn(aResponse()
                    .withStatus(409)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                            {"code":"INSUFFICIENT_STOCK","message":"Insufficient stock"}
                            """)));

            assertThatThrownBy(() -> gateway.reserve(PRODUCT_ID, 3))
                    .isInstanceOf(InsufficientStockException.class);

            // ignore-exceptions 정책으로 retry 0회 — 단 1회 호출
            verify(exactly(1), postRequestedFor(urlEqualTo(RESERVE_PATH)));
        }

        @Test
        void 영구실패_404_는_재시도_없이_즉시_실패() {
            stubFor(post(urlEqualTo(RESERVE_PATH)).willReturn(aResponse()
                    .withStatus(404)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                            {"code":"PRODUCT_NOT_FOUND","message":"Product not found"}
                            """)));

            assertThatThrownBy(() -> gateway.reserve(PRODUCT_ID, 3))
                    .isInstanceOf(ProductNotFoundException.class);

            verify(exactly(1), postRequestedFor(urlEqualTo(RESERVE_PATH)));
        }

        @Test
        void 일시실패_5xx_는_max_attempts_3회_까지_재시도() {
            stubFor(post(urlEqualTo(RESERVE_PATH)).willReturn(aResponse().withStatus(500)));

            assertThatThrownBy(() -> gateway.reserve(PRODUCT_ID, 3))
                    .isInstanceOf(InventoryClientException.class)
                    .isNotInstanceOf(InsufficientStockException.class);

            // max-attempts=3 → 첫 호출 + 재시도 2회 = 총 3회
            verify(exactly(3), postRequestedFor(urlEqualTo(RESERVE_PATH)));
        }

        @Test
        void 일시실패_후_복구되면_재시도가_성공으로_종료() {
            // 1차: 500 → 2차: 204 — retry 의 *진짜 가치 (일시 결함 회복)* 시연
            stubFor(post(urlEqualTo(RESERVE_PATH))
                    .inScenario("retry-recovery")
                    .whenScenarioStateIs(Scenario.STARTED)
                    .willReturn(aResponse().withStatus(500))
                    .willSetStateTo("first-failed"));
            stubFor(post(urlEqualTo(RESERVE_PATH))
                    .inScenario("retry-recovery")
                    .whenScenarioStateIs("first-failed")
                    .willReturn(aResponse().withStatus(204)));

            assertThatNoException().isThrownBy(() -> gateway.reserve(PRODUCT_ID, 3));

            // 1차 실패 + 2차 성공 = 총 2회 호출
            verify(exactly(2), postRequestedFor(urlEqualTo(RESERVE_PATH)));
        }

        @Test
        void timeout_도_일시실패_로_간주되어_재시도() {
            // read-timeout=300ms 로 강제, fixedDelay 600ms 로 매번 timeout 유발
            stubFor(post(urlEqualTo(RESERVE_PATH)).willReturn(aResponse()
                    .withStatus(204)
                    .withFixedDelay(600)));

            assertThatThrownBy(() -> gateway.reserve(PRODUCT_ID, 3))
                    .isInstanceOf(InventoryClientException.class);

            verify(exactly(3), postRequestedFor(urlEqualTo(RESERVE_PATH)));
        }
    }
}
