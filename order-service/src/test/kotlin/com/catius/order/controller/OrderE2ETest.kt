package com.catius.order.controller

import com.catius.order.controller.dto.CreateOrderRequest
import com.catius.order.controller.dto.OrderItemRequest
import com.catius.order.controller.dto.OrderResponse
import com.catius.order.domain.OrderStatus
import com.catius.order.domain.port.InventoryClient
import com.catius.order.domain.port.ReserveOutcome
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.BDDMockito.given
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.listener.KafkaMessageListenerContainer
import org.springframework.kafka.listener.MessageListener
import org.springframework.kafka.support.serializer.JsonDeserializer
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.kafka.test.utils.ContainerTestUtils
import org.springframework.kafka.test.utils.KafkaTestUtils
import org.springframework.test.context.ActiveProfiles
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EmbeddedKafka(partitions = 2, topics = ["order.order-confirmed.v1"])
@ActiveProfiles("test")
@org.springframework.test.annotation.DirtiesContext
class OrderE2ETest {

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @MockBean
    private lateinit var inventoryClient: InventoryClient

    @Autowired
    private lateinit var embeddedKafkaBroker: EmbeddedKafkaBroker

    private lateinit var records: BlockingQueue<ConsumerRecord<String, Any>>
    private lateinit var container: KafkaMessageListenerContainer<String, Any>

    @BeforeEach
    fun setup() {
        val consumerProps = KafkaTestUtils.consumerProps("test-group", "true", embeddedKafkaBroker)
        consumerProps[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        consumerProps[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = JsonDeserializer::class.java

        val objectMapper = jacksonObjectMapper().findAndRegisterModules()
        val jsonDeserializer = JsonDeserializer<Any>(objectMapper)
        jsonDeserializer.addTrustedPackages("*")

        val cf = DefaultKafkaConsumerFactory<String, Any>(consumerProps, StringDeserializer(), jsonDeserializer)
        val containerProps = ContainerProperties("order.order-confirmed.v1")
        
        container = KafkaMessageListenerContainer(cf, containerProps)
        records = LinkedBlockingQueue()
        container.setupMessageListener(MessageListener<String, Any> { record -> records.add(record) })
        container.start()
        
        ContainerTestUtils.waitForAssignment(container, embeddedKafkaBroker.partitionsPerTopic)
    }

    @org.junit.jupiter.api.AfterEach
    fun tearDown() {
        container.stop()
    }

    private fun <T> anyK(): T {
        return org.mockito.ArgumentMatchers.any() ?: null as T
    }

    @Test
    @DisplayName("정상 흐름: 재고 예약 성공 시 주문이 CONFIRMED 가 되고 Kafka 이벤트를 발행한다")
    fun order_saga_success() {
        // given
        val request = CreateOrderRequest(
            customerId = 1L,
            items = listOf(OrderItemRequest(productId = 1001L, quantity = 2))
        )
        given(inventoryClient.reserve(org.mockito.ArgumentMatchers.anyLong(), anyK())).willReturn(ReserveOutcome.Success)

        // when
        val response = restTemplate.postForEntity("/api/v1/orders", request, OrderResponse::class.java)

        // then
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body?.status).isEqualTo(OrderStatus.CONFIRMED.name)

        // Kafka 검증
        val record = records.poll(10, TimeUnit.SECONDS)
        assertThat(record).isNotNull
        assertThat(record?.value().toString()).contains("orderId=${response.body?.id}")
    }

    @Test
    @DisplayName("재고 부족: 재고 예약 실패 시 주문이 FAILED 가 되고 이벤트를 발행하지 않는다")
    fun order_saga_insufficient_stock() {
        // given
        val request = CreateOrderRequest(
            customerId = 1L,
            items = listOf(OrderItemRequest(productId = 1001L, quantity = 2))
        )
        given(inventoryClient.reserve(org.mockito.ArgumentMatchers.anyLong(), anyK())).willReturn(ReserveOutcome.InsufficientStock(1001L))

        // when
        val response = restTemplate.postForEntity("/api/v1/orders", request, OrderResponse::class.java)

        // then
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body?.status).isEqualTo(OrderStatus.FAILED.name)

        // Kafka 검증 (메시지 수신되지 않아야 함)
        val record = records.poll(2, TimeUnit.SECONDS)
        assertThat(record).isNull()
    }

    @Test
    @DisplayName("장애 상황: 재고 예약 응답이 Unavailable 일 때 release 를 호출하고 주문은 FAILED 가 된다")
    fun order_saga_unavailable() {
        // given
        val request = CreateOrderRequest(
            customerId = 1L,
            items = listOf(OrderItemRequest(productId = 1001L, quantity = 2))
        )
        given(inventoryClient.reserve(org.mockito.ArgumentMatchers.anyLong(), anyK())).willReturn(ReserveOutcome.Unavailable)

        // when
        val response = restTemplate.postForEntity("/api/v1/orders", request, OrderResponse::class.java)

        // then
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body?.status).isEqualTo(OrderStatus.FAILED.name)

        // 보상 트랜잭션(release) 호출 검증
        verify(inventoryClient, atLeastOnce()).release(org.mockito.ArgumentMatchers.anyLong(), anyK())

        // Kafka 검증 (메시지 수신되지 않아야 함)
        val record = records.poll(2, TimeUnit.SECONDS)
        assertThat(record).isNull()
    }
}
