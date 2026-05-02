package com.catius.order.infrastructure.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.kafka.test.EmbeddedKafkaZKBroker

@Configuration
@Profile("local | perf")
class LocalEmbeddedKafkaConfig {

    @Bean
    fun embeddedKafkaBroker(): EmbeddedKafkaZKBroker {
        // 9092 포트로 Embedded Kafka (with Zookeeper) 브로커를 직접 실행
        return EmbeddedKafkaZKBroker(1, true, "order.order-confirmed.v1")
            .kafkaPorts(9092)
    }
}
