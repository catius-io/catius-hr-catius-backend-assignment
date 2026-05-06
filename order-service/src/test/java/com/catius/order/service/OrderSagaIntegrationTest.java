package com.catius.order.service;

import com.catius.order.client.InventoryClientFacade;
import com.catius.order.client.dto.request.InventoryRequest;
import com.catius.order.client.dto.response.InventoryResponse;
import com.catius.order.domain.Order;
import com.catius.order.domain.OrderItem;
import com.catius.order.domain.OrderStatus;
import com.catius.order.event.OrderConfirmedEvent;
import com.catius.order.event.OrderSagaOrchestrator;
import com.catius.order.repository.OrderRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:file:order-saga-integration-test?mode=memory&cache=shared",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.kafka.producer.properties.max.block.ms=5000",
        "spring.kafka.producer.properties.request.timeout.ms=3000",
        "spring.kafka.producer.properties.delivery.timeout.ms=5000",
        "catius.kafka.topics.order-confirmed=order-service.order.confirm"
})
@EmbeddedKafka(
        partitions = 1,
        topics = "order-service.order.confirm",
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
class OrderSagaIntegrationTest {

    private static final String TOPIC = "order-service.order.confirm";

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderSagaOrchestrator orderSagaOrchestrator;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @MockBean
    private InventoryClientFacade inventoryClientFacade;

    @Test
    @DisplayName("Saga 통합 테스트 - 재고 차감 성공 시 주문 확정 이벤트를 Kafka로 발행")
    void execute_성공_주문확정_Kafka이벤트발행() {
        given(inventoryClientFacade.reserve(any(InventoryRequest.class)))
                .willReturn(new InventoryResponse(1001L, "테스트 상품", 8));

        Order order = orderRepository.save(Order.create(1L, List.of(OrderItem.of(1001L, 2))));

        try (Consumer<String, OrderConfirmedEvent> consumer = createConsumer()) {
            embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, TOPIC);

            orderSagaOrchestrator.execute(order);

            ConsumerRecord<String, OrderConfirmedEvent> record =
                    KafkaTestUtils.getSingleRecord(consumer, TOPIC);

            Order savedOrder = orderRepository.findById(order.getId()).orElseThrow();
            OrderConfirmedEvent event = record.value();

            assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(record.key()).isEqualTo(order.getId().toString());
            assertThat(event.getOrderId()).isEqualTo(order.getId());
            assertThat(event.getCustomerId()).isEqualTo(1L);
            assertThat(event.getItems()).hasSize(1);
            assertThat(event.getItems().get(0).getProductId()).isEqualTo(1001L);
            assertThat(event.getItems().get(0).getQuantity()).isEqualTo(2);
            assertThat(event.getStatus()).isEqualTo(OrderStatus.CONFIRMED.name());
            assertThat(event.getTimestamp()).isNotNull();

            then(inventoryClientFacade).should().reserve(new InventoryRequest(1001L, 2));
        }
    }

    private Consumer<String, OrderConfirmedEvent> createConsumer() {
        Map<String, Object> props = KafkaTestUtils.consumerProps(
                "order-saga-integration-test",
                "false",
                embeddedKafkaBroker
        );
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.catius.order.event");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, OrderConfirmedEvent.class.getName());

        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                new JsonDeserializer<>(OrderConfirmedEvent.class, false)
        ).createConsumer();
    }
}
