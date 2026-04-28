package com.catius.order.infrastructure.client

import com.catius.order.domain.OrderItem
import com.catius.order.domain.port.ReserveOutcome
import com.github.tomakehurst.wiremock.client.WireMock.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = ["inventory.base-url=http://localhost:\${wiremock.server.port}"]
)
@AutoConfigureWireMock(port = 0)
@ActiveProfiles("test")
class InventoryClientAdapterTest {

    @Autowired
    private lateinit var adapter: InventoryClientAdapter

    private val items = listOf(OrderItem(1001L, 2))

    @Test
    @DisplayName("200 OK 응답 시 Success 반환")
    fun reserve_success() {
        stubFor(
            post(urlEqualTo("/api/v1/inventory/reserve"))
                .willReturn(aResponse().withStatus(200))
        )

        val outcome = adapter.reserve(1L, items)

        assertThat(outcome).isEqualTo(ReserveOutcome.Success)
    }

    @Test
    @DisplayName("409 Conflict 응답 시 InsufficientStock 반환")
    fun reserve_insufficient_stock() {
        stubFor(
            post(urlEqualTo("/api/v1/inventory/reserve"))
                .willReturn(aResponse().withStatus(409))
        )

        val outcome = adapter.reserve(1L, items)

        assertThat(outcome).isInstanceOf(ReserveOutcome.InsufficientStock::class.java)
        assertThat((outcome as ReserveOutcome.InsufficientStock).productId).isEqualTo(1001L)
    }

    @Test
    @DisplayName("500 Internal Server Error 발생 시 Unavailable 반환 (Fallback 동작)")
    fun reserve_server_error() {
        stubFor(
            post(urlEqualTo("/api/v1/inventory/reserve"))
                .willReturn(aResponse().withStatus(500))
        )

        val outcome = adapter.reserve(1L, items)

        assertThat(outcome).isEqualTo(ReserveOutcome.Unavailable)
    }

    @Test
    @DisplayName("타임아웃 발생 시 Unavailable 반환 (Fallback 동작)")
    fun reserve_timeout() {
        stubFor(
            post(urlEqualTo("/api/v1/inventory/reserve"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withFixedDelay(3000) // application.yml 설정이 2s 이므로 타임아웃 발생 유도
                )
        )

        val outcome = adapter.reserve(1L, items)

        assertThat(outcome).isEqualTo(ReserveOutcome.Unavailable)
    }
}
