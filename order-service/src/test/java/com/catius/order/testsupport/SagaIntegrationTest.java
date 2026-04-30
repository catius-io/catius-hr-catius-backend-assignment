package com.catius.order.testsupport;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.test.context.TestPropertySource;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Saga 통합 테스트의 *공통 셋업* 을 한 어노테이션으로 묶음. inventory 호출 (WireMock) +
 * R4J retry/CB 의 *contract 검증용 압축 설정* 을 공유.
 *
 * 클래스별 추가 설정 (@EmbeddedKafka, kafka.bootstrap-servers, kafka.listener.auto-startup 등) 은
 * 각 테스트 클래스가 자기 컨텍스트에 맞춰 @TestPropertySource 로 보강.
 *
 * inventory 측의 @WireMockInventoryTest 와는 별개 — 두 카테고리 (HTTP contract vs Saga 흐름)
 * 가 다른 의도를 가지므로 형제 어노테이션으로 유지.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureWireMock(port = 0)
@TestPropertySource(properties = {
        "inventory.base-url=http://localhost:${wiremock.server.port}",
        // contract 검증에 retry 가 영향 주지 않게 압축
        "resilience4j.retry.instances.inventoryClient.max-attempts=1",
        // 본 통합 테스트가 CB OPEN 을 trigger 하지 않게 sliding-window 충분히 크게
        "resilience4j.circuitbreaker.instances.inventoryClient.minimum-number-of-calls=100"
})
public @interface SagaIntegrationTest {
}
