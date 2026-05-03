package com.catius.order.service.feign;

import com.catius.order.service.InventoryClient;
import com.catius.order.service.InventoryView;
import com.catius.order.service.exception.AlreadyCompensatedException;
import com.catius.order.service.exception.AmbiguousInventoryException;
import com.catius.order.service.exception.InsufficientStockException;
import com.catius.order.service.exception.ProductNotFoundException;
import com.catius.order.service.exception.ReservationConflictException;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class InventoryClientIntegrationTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(options().dynamicPort())
            .build();

    @DynamicPropertySource
    static void overrideBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("inventory.base-url", wireMock::baseUrl);
    }

    @Autowired
    InventoryClient client;

    @Autowired
    CircuitBreakerRegistry cbRegistry;

    @BeforeEach
    void resetState() {
        cbRegistry.circuitBreaker("inventoryClient").reset();
        wireMock.resetAll();
    }

    @Test
    void getInventory_returnsInventoryView() {
        wireMock.stubFor(get(urlEqualTo("/api/v1/inventory/1001"))
                .willReturn(okJson("""
                        {"productId":1001,"quantity":50}""")));

        InventoryView v = client.getInventory(1001L);

        assertEquals(1001L, v.productId());
        assertEquals(50, v.quantity());
    }

    @Test
    void getInventory_404_mapsToProductNotFoundException() {
        wireMock.stubFor(get(urlEqualTo("/api/v1/inventory/9999"))
                .willReturn(jsonResponse("""
                        {"code":"PRODUCT_NOT_FOUND","message":"product not found: productId=9999"}""", 404)));

        assertThrows(ProductNotFoundException.class,
                () -> client.getInventory(9999L));
    }

    @Test
    void reserve_succeeds() {
        wireMock.stubFor(post(urlEqualTo("/api/v1/inventory/reserve"))
                .willReturn(okJson("""
                        {"orderId":"order-1","productId":1001,"quantity":2,"state":"RESERVED"}""")));

        client.reserve("order-1", 1001L, 2);

        wireMock.verify(1, postRequestedFor(urlEqualTo("/api/v1/inventory/reserve")));
    }

    @Test
    void reserve_409InsufficientStock_throwsExplicitException() {
        wireMock.stubFor(post(urlEqualTo("/api/v1/inventory/reserve"))
                .willReturn(jsonResponse("""
                        {"code":"INSUFFICIENT_STOCK","message":"insufficient"}""", 409)));

        assertThrows(InsufficientStockException.class,
                () -> client.reserve("order-1", 1001L, 5));
    }

    @Test
    void reserve_409AlreadyCompensated_throwsExplicitException() {
        wireMock.stubFor(post(urlEqualTo("/api/v1/inventory/reserve"))
                .willReturn(jsonResponse("""
                        {"code":"ALREADY_COMPENSATED","message":"tombstone"}""", 409)));

        assertThrows(AlreadyCompensatedException.class,
                () -> client.reserve("order-1", 1001L, 1));
    }

    @Test
    void reserve_409ReservationConflict_throwsExplicitException() {
        wireMock.stubFor(post(urlEqualTo("/api/v1/inventory/reserve"))
                .willReturn(jsonResponse("""
                        {"code":"RESERVATION_CONFLICT","message":"drift"}""", 409)));

        assertThrows(ReservationConflictException.class,
                () -> client.reserve("order-1", 1001L, 5));
    }

    @Test
    void reserve_4xxNotRetried_singleHttpCall() {
        wireMock.stubFor(post(urlEqualTo("/api/v1/inventory/reserve"))
                .willReturn(jsonResponse("""
                        {"code":"INSUFFICIENT_STOCK","message":"..."}""", 409)));

        assertThrows(InsufficientStockException.class,
                () -> client.reserve("order-1", 1001L, 5));

        // 명시적 4xx은 ignore-exceptions로 retry 대상에서 제외 — 1회만 호출
        wireMock.verify(1, postRequestedFor(urlEqualTo("/api/v1/inventory/reserve")));
    }

    @Test
    void reserve_5xx_ambiguous_andRetried() {
        wireMock.stubFor(post(urlEqualTo("/api/v1/inventory/reserve"))
                .willReturn(serverError()));

        assertThrows(AmbiguousInventoryException.class,
                () -> client.reserve("order-1", 1001L, 1));

        // ambiguous 5xx는 retry 대상 — max-attempts=2이므로 2회 호출
        wireMock.verify(2, postRequestedFor(urlEqualTo("/api/v1/inventory/reserve")));
    }

    @Test
    void reserve_decodeFailure_throwsAmbiguous() {
        // 회귀 방어: inventory가 reserve를 실제로 commit한 뒤 응답 body가 깨졌다고 가정.
        // adapter 경계에서 RuntimeException을 ambiguous로 감싸지 않으면 Saga가 차감 여부 불명확
        // 케이스에서 보상 분기를 못 타고 재고 leak 발생.
        wireMock.stubFor(post(urlEqualTo("/api/v1/inventory/reserve"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("not-json-{")));

        assertThrows(AmbiguousInventoryException.class,
                () -> client.reserve("order-1", 1001L, 1));
    }

    @Test
    void getInventory_decodeFailure_throwsAmbiguous() {
        wireMock.stubFor(get(urlEqualTo("/api/v1/inventory/1001"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"productId\":\"abc\",\"quantity\":50}")));

        assertThrows(AmbiguousInventoryException.class,
                () -> client.getInventory(1001L));
    }

    @Test
    void reserve_timeout_throwsAmbiguous() {
        // 테스트 read-timeout은 500ms → 1500ms 지연이면 timeout 트리거
        wireMock.stubFor(post(urlEqualTo("/api/v1/inventory/reserve"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(1500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"orderId":"order-1","productId":1001,"quantity":1,"state":"RESERVED"}""")));

        assertThrows(AmbiguousInventoryException.class,
                () -> client.reserve("order-1", 1001L, 1));
    }

    @Test
    void release_succeeds() {
        wireMock.stubFor(post(urlEqualTo("/api/v1/inventory/release"))
                .willReturn(okJson("""
                        {"orderId":"order-1","productId":1001,"outcome":"RELEASED"}""")));

        client.release("order-1", 1001L);

        wireMock.verify(1, postRequestedFor(urlEqualTo("/api/v1/inventory/release")));
    }

    @Test
    void circuitBreaker_opensAfterRepeatedFailures_andFastFailsSubsequentCalls() throws InterruptedException {
        wireMock.stubFor(post(urlEqualTo("/api/v1/inventory/reserve"))
                .willReturn(serverError()));

        // 충분한 실패를 누적해 CB OPEN으로 전이.
        // sliding-window-size=5, minimum-number-of-calls=3, threshold=50%.
        // 1 logical call = 2 HTTP records (5xx + retry). 3~5 logical calls이면 충분.
        for (int i = 0; i < 5; i++) {
            try {
                client.reserve("order-" + i, 1001L, 1);
            } catch (Exception e) {
                // expected: AmbiguousInventoryException 또는 CallNotPermittedException
            }
        }

        // 후속 호출은 CB OPEN으로 fast-fail — WireMock에 도달하지 않아야 함
        wireMock.resetRequests();

        assertThrows(CallNotPermittedException.class,
                () -> client.reserve("order-final", 1001L, 1));

        wireMock.verify(0, postRequestedFor(urlEqualTo("/api/v1/inventory/reserve")));
    }

    @Test
    void circuitBreaker_explicit4xx_doesNotOpen() {
        wireMock.stubFor(post(urlEqualTo("/api/v1/inventory/reserve"))
                .willReturn(jsonResponse("""
                        {"code":"INSUFFICIENT_STOCK","message":"..."}""", 409)));

        // 명시적 4xx은 ignore-exceptions로 CB record에서 제외 — 많이 호출해도 CB CLOSED 유지
        for (int i = 0; i < 10; i++) {
            try {
                client.reserve("order-" + i, 1001L, 1);
            } catch (InsufficientStockException e) {
                // expected
            }
        }

        // 다음 호출도 정상적으로 WireMock에 도달 (CB가 막지 않음)
        wireMock.resetRequests();
        assertThrows(InsufficientStockException.class,
                () -> client.reserve("order-final", 1001L, 1));
        wireMock.verify(1, postRequestedFor(urlEqualTo("/api/v1/inventory/reserve")));
    }
}
