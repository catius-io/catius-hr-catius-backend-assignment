package com.catius.order.infrastructure.messaging

import com.catius.order.domain.OrderDomainEvent
import com.catius.order.domain.OrderItem
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
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
import java.time.Instant
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = ["order.order-confirmed.v1"])
@ActiveProfiles("test")
class OrderEventPublisherAdapterTest {

    @Autowired
    private lateinit var embeddedKafkaBroker: EmbeddedKafkaBroker

    @Autowired
    private lateinit var publisher: OrderEventPublisherAdapter

    @Test
    @DisplayName("OrderConfirmed 이벤트를 Kafka 로 발행한다")
    fun publish_confirmed_event() {
        // given
        val orderId = 42L
        val event = OrderDomainEvent.Confirmed(
            orderId = orderId,
            customerId = 1L,
            items = listOf(OrderItem(1001L, 2)),
            occurredAt = Instant.now()
        )

        val records = subscribe("order.order-confirmed.v1")

        // when
        publisher.publish(event)

        // then
        val record = records.poll(10, TimeUnit.SECONDS)
        assertThat(record).isNotNull
        assertThat(record?.key()).isEqualTo(orderId.toString())
        
        // 역직렬화된 값 확인 (LinkedHashMap 으로 역직렬화될 수 있으므로 문자열 포함 여부로 검증)
        assertThat(record?.value().toString()).contains("orderId=42")
    }

    private fun subscribe(topic: String): BlockingQueue<ConsumerRecord<String, Any>> {
        val consumerProps = KafkaTestUtils.consumerProps("test-group", "true", embeddedKafkaBroker)
        consumerProps[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        consumerProps[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = JsonDeserializer::class.java
        
        // Use jacksonObjectMapper for Kotlin support
        val objectMapper = jacksonObjectMapper().findAndRegisterModules()
        val jsonDeserializer = JsonDeserializer<Any>(objectMapper)
        jsonDeserializer.addTrustedPackages("*")

        val cf = DefaultKafkaConsumerFactory<String, Any>(consumerProps, StringDeserializer(), jsonDeserializer)
        val containerProps = ContainerProperties(topic)
        val container = KafkaMessageListenerContainer(cf, containerProps)
        val records = LinkedBlockingQueue<ConsumerRecord<String, Any>>()
        container.setupMessageListener(MessageListener<String, Any> { record -> records.add(record) })
        container.start()
        ContainerTestUtils.waitForAssignment(container, embeddedKafkaBroker.partitionsPerTopic)
        return records
    }
}
