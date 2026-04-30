package com.catius.order.testsupport;

import com.catius.order.messaging.OrderConfirmedEvent;
import com.catius.order.repository.OrderRepository;
import com.github.tomakehurst.wiremock.client.WireMock;
import lombok.experimental.UtilityClass;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.util.Map;
import java.util.UUID;

@UtilityClass
public class SagaTestSupport {

    public void resetSagaState(OrderRepository orderRepository) {
        WireMock.reset();
        orderRepository.deleteAll();
    }

    public Consumer<String, OrderConfirmedEvent> createOrderConfirmedConsumer(
            EmbeddedKafkaBroker broker, String topic) {
        // group-id 에 UUID — 다른 테스트의 offset 영향 차단
        Map<String, Object> props = KafkaTestUtils.consumerProps(
                "saga-test-" + UUID.randomUUID(), "true", broker);
        props.put("key.deserializer", StringDeserializer.class);
        props.put("value.deserializer", JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, OrderConfirmedEvent.class.getName());

        Consumer<String, OrderConfirmedEvent> consumer = new KafkaConsumer<>(props);
        broker.consumeFromAnEmbeddedTopic(consumer, topic);
        return consumer;
    }
}
