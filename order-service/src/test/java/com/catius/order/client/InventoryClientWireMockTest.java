package com.catius.order.client;

import com.catius.order.client.dto.ReleaseInventoryRequest;
import com.catius.order.client.dto.ReserveInventoryRequest;
import com.catius.order.client.exception.InsufficientStockException;
import com.catius.order.client.exception.InventoryClientException;
import com.catius.order.client.exception.ProductNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.test.context.TestPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "inventory.base-url=http://localhost:${wiremock.server.port}",
        "spring.kafka.listener.auto-startup=false"
})
@AutoConfigureWireMock(port = 0)
@TestPropertySource(properties = {
        // Resilience4j 의 retry 가 본 contract 테스트에 영향을 주지 않게 disable. R4J 시나리오는 별도 PR.
        "resilience4j.retry.instances.inventoryClient.max-attempts=1"
})
@DisplayName("InventoryClient — Feign + ErrorDecoder contract (WireMock)")
class InventoryClientWireMockTest {

    private static final long PRODUCT_ID = 9001L;

    @Autowired
    private InventoryClient client;

    @Nested
    @DisplayName("reserve")
    class Reserve {

        @Test
        void 정상_204_는_예외_없이_종료() {
            stubFor(post(urlEqualTo("/api/v1/inventory/reserve"))
                    .willReturn(aResponse().withStatus(204)));

            assertThatNoException().isThrownBy(() ->
                    client.reserve(new ReserveInventoryRequest(PRODUCT_ID, 3)));

            verify(postRequestedFor(urlEqualTo("/api/v1/inventory/reserve"))
                    .withRequestBody(equalToJson("""
                            {"productId": 9001, "quantity": 3}
                            """)));
        }

        @Test
        void 응답_409_는_InsufficientStockException_으로_변환된다() {
            stubFor(post(urlEqualTo("/api/v1/inventory/reserve"))
                    .willReturn(aResponse()
                            .withStatus(409)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {"code":"INSUFFICIENT_STOCK","message":"Insufficient stock for product 9001: current=1, requested=3"}
                                    """)));

            assertThatThrownBy(() -> client.reserve(new ReserveInventoryRequest(PRODUCT_ID, 3)))
                    .isInstanceOf(InsufficientStockException.class)
                    .satisfies(ex -> assertThat(ex.getMessage())
                            .contains("Insufficient stock"));
        }

        @Test
        void 응답_404_는_ProductNotFoundException_으로_변환된다() {
            stubFor(post(urlEqualTo("/api/v1/inventory/reserve"))
                    .willReturn(aResponse()
                            .withStatus(404)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {"code":"PRODUCT_NOT_FOUND","message":"Product not found: 9001"}
                                    """)));

            assertThatThrownBy(() -> client.reserve(new ReserveInventoryRequest(PRODUCT_ID, 3)))
                    .isInstanceOf(ProductNotFoundException.class);
        }

        @Test
        void 그_외_5xx_는_generic_InventoryClientException() {
            stubFor(post(urlEqualTo("/api/v1/inventory/reserve"))
                    .willReturn(aResponse().withStatus(500)));

            assertThatThrownBy(() -> client.reserve(new ReserveInventoryRequest(PRODUCT_ID, 3)))
                    .isInstanceOf(InventoryClientException.class)
                    .isNotInstanceOf(InsufficientStockException.class)
                    .isNotInstanceOf(ProductNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("release")
    class Release {

        @Test
        void 정상_204_는_예외_없이_종료() {
            stubFor(post(urlEqualTo("/api/v1/inventory/release"))
                    .willReturn(aResponse().withStatus(204)));

            assertThatNoException().isThrownBy(() ->
                    client.release(new ReleaseInventoryRequest(PRODUCT_ID, 3)));

            verify(postRequestedFor(urlEqualTo("/api/v1/inventory/release"))
                    .withRequestBody(equalToJson("""
                            {"productId": 9001, "quantity": 3}
                            """)));
        }

        @Test
        void 응답_404_는_ProductNotFoundException() {
            stubFor(post(urlEqualTo("/api/v1/inventory/release"))
                    .willReturn(aResponse()
                            .withStatus(404)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {"code":"PRODUCT_NOT_FOUND","message":"Product not found: 9001"}
                                    """)));

            assertThatThrownBy(() -> client.release(new ReleaseInventoryRequest(PRODUCT_ID, 3)))
                    .isInstanceOf(ProductNotFoundException.class);
        }
    }
}
