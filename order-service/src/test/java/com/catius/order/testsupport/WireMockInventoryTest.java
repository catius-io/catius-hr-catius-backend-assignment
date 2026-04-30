package com.catius.order.testsupport;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.test.context.TestPropertySource;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * inventory 호출 검증을 위한 WireMock 기반 통합 테스트의 *공통 셋업*.
 *
 * 포함:
 * - @SpringBootTest + @AutoConfigureWireMock(port = 0)
 * - inventory.base-url 을 WireMock 의 동적 포트로 redirect
 * - spring.kafka.listener.auto-startup=false (본 테스트들은 Kafka 와 무관)
 *
 * R4J 설정은 *클래스별로 의도가 다름* (retry 시나리오 vs CB 시나리오 등) 이라 본 어노테이션에
 * 박지 않고, 각 테스트 클래스가 @TestPropertySource 로 자기 셋업을 보강.
 *
 * Saga 통합 테스트의 @SagaIntegrationTest 와는 별개 — 두 카테고리 (HTTP contract vs Saga 흐름)
 * 가 다른 의도를 가지므로 형제 어노테이션으로 유지.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureWireMock(port = 0)
@TestPropertySource(properties = {
        "inventory.base-url=http://localhost:${wiremock.server.port}",
        "spring.kafka.listener.auto-startup=false"
})
public @interface WireMockInventoryTest {
}
