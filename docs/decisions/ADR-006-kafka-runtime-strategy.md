# ADR-006: Kafka 실행 전략

- **상태**: 확정
- **결정**: Kafka는 **테스트 환경에서만 `@EmbeddedKafka`로 부트스트랩**한다. 로컬 `bootRun`은 `--spring.kafka.listener.auto-startup=false`로 리스너를 비활성화하여 외부 브로커 없이 부팅한다. docker-compose나 main 코드에서 Embedded Kafka 수동 부트스트랩은 도입하지 않는다.

## 근거

- **README FAQ 정렬**: README "자주 묻는 질문" 절의 docker-compose 항목에 "Embedded Kafka로 외부 의존 없는 실행을 보여주세요. docker-compose는 권장하지 않습니다"가 명시. 이 권고를 따른다.
- **재현성**: `./gradlew build`만으로 모든 통합 테스트(Saga·이벤트 발행)가 외부 브로커 설치·기동 단계 없이 통과. zero-config 빌드.
- **로컬 bootRun 안정성**: 리스너 자동 시작을 끄면 브로커 부재 시 발생하는 connection refused 로그가 사라져 컨트롤러 스모크 테스트(`/actuator/health`, 컨트롤러 200/201 응답) 동선이 깨끗해짐.
- **테스트 전용 위치 유지**: `@EmbeddedKafka`는 spring-kafka-test의 테스트 픽스처임이 분명한 컴포넌트. main 코드에서 호출하면 "테스트용 도구를 운영 경로에서 사용"하는 어색함이 생기고, 클래스패스 분리(testImplementation vs implementation)도 흐려짐.

## 검토한 대안

### docker-compose로 로컬 Kafka 브로커 기동
- 장점: 로컬에서 실제 Kafka의 행동(리밸런싱·offset commit·DLQ)을 사람 손으로 확인 가능.
- 기각 사유:
  - README가 명시적으로 권장하지 않음. 명시된 권고를 거스를 이유가 없음.
  - docker daemon 기동·이미지 pull·포트 충돌 점검이 빌드 외 추가 운영 단계로 발생.
  - 본 과제의 핵심 영역(Saga·통신·성능)은 통합 테스트에서 충분히 검증 가능.

### main 코드에서 Embedded Kafka 수동 부트스트랩
- 장점: `bootRun` 한 번으로 컨트롤러·이벤트 발행이 함께 동작.
- 기각 사유:
  - `spring-kafka-test`의 `EmbeddedKafkaBroker`를 main 클래스패스에 노출시켜야 함. 의존성 스코프가 흐려지고, 운영 빌드에 테스트 라이브러리가 섞이는 안티패턴.
  - 어차피 Saga 검증은 자동화 테스트(`@EmbeddedKafka`)로 진행되므로 `bootRun`에서까지 Kafka가 살아있어야 하는 시나리오가 본 과제에 없음.

## 검증과 한계

- **검증**:
  - `./gradlew build` 가 모든 통합 테스트(`@EmbeddedKafka` 사용)를 외부 의존 없이 통과.
  - `./gradlew :order-service:bootRun --args='--spring.kafka.listener.auto-startup=false'` 로 기동 후 `/actuator/health` 200 확인.
- **한계**:
  - 로컬 `bootRun`에서 실제 이벤트 흐름(주문 확정 → 메시지 발행 → 컨슈머 동작)을 사람이 직접 관찰할 수 없음. 통합 테스트로만 검증 가능.
  - Embedded Kafka는 실제 브로커와 동작이 100% 동일하지 않음(브로커 페일오버·파티션 리밸런싱 시나리오 일부 재현 한계). 본 과제 시나리오에는 영향 없음.
