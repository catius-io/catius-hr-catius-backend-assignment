# k6 성능 테스트

본 디렉터리는 **수동 perf 실행 전용** 산출물이다. 빌드(`./gradlew build`)는 외부 의존(Kafka·k6) 없이 통과하며, 본 시나리오들은 ADR-004의 Resilience4j 수치를 실측 기반으로 보정·확정하기 위한 도구.

## 시나리오

| 파일 | 의도 | 관찰 대상 |
|---|---|---|
| `scenarios/baseline.js` | 정상 부하의 처리량·지연 측정 | RPS, p95/p99, 실패율 (SLO threshold 단언) |
| `scenarios/inventory-delay.js` | inventory 의도적 지연 → slow-call CB | CB state 전이, ambiguous 보상 발행 |
| `scenarios/inventory-error.js` | inventory 의도적 5xx → failure-rate CB | CB OPEN 진입, 보상 이벤트 수, half-open 복귀 |

## 사전 준비

### 1. k6 설치

```bash
brew install k6                                # macOS
# 또는 https://k6.io/docs/get-started/installation/
```

### 2. Kafka broker

본 시나리오는 실제 Kafka가 필요하다 (Saga가 confirmed/release 이벤트를 발행).
빌드·테스트 경로는 Embedded Kafka로 충분하지만, k6 부하 시나리오는 5초 단위 publish timeout이 누적되면 측정이 왜곡되므로 운영급 broker를 권장.

가장 간단한 방법 — docker 한 줄 (Apache 공식 이미지, KRaft 단일 노드):
```bash
docker run -d --name perf-kafka -p 9092:9092 \
  -e KAFKA_NODE_ID=1 \
  -e KAFKA_PROCESS_ROLES=broker,controller \
  -e KAFKA_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9093 \
  -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 \
  -e KAFKA_CONTROLLER_QUORUM_VOTERS=1@localhost:9093 \
  -e KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER \
  -e KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT \
  -e KAFKA_INTER_BROKER_LISTENER_NAME=PLAINTEXT \
  -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 \
  -e KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1 \
  -e KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=1 \
  apache/kafka:latest
```

종료/정리:
```bash
docker stop perf-kafka && docker rm perf-kafka
```

또는 외부 broker가 있으면 `KAFKA_BROKERS=host:9092` 환경변수로 두 서비스 기동 시 주입.

### 3. inventory-service 기동

**baseline 시나리오용** (정상 동작):
```bash
./gradlew :inventory-service:bootRun --args='--spring.profiles.active=perf'
```

**inventory-delay 시나리오용** (지연 1500ms 주입):
```bash
FAULT_DELAY_MS=1500 ./gradlew :inventory-service:bootRun \
  --args='--spring.profiles.active=perf,fault-injection'
```

**inventory-error 시나리오용** (5xx 30% 주입):
```bash
FAULT_ERROR_RATE=0.3 ./gradlew :inventory-service:bootRun \
  --args='--spring.profiles.active=perf,fault-injection'
```

`perf` 프로파일은 `data-perf.sql`로 productId 1001~1100에 충분한 재고를 seed.
`fault-injection` 프로파일은 `FaultInjectionFilter`를 활성화 (격리되어 있어 본 프로파일 미지정 시 영향 없음).

### 4. order-service 기동

```bash
./gradlew :order-service:bootRun
```

기본 8081 포트에서 listen. `KAFKA_BROKERS` 환경변수로 broker 주소 조정 가능.

## 실행

각 시나리오는 독립적으로:
```bash
k6 run perf/scenarios/baseline.js
k6 run perf/scenarios/inventory-delay.js
k6 run perf/scenarios/inventory-error.js
```

## 관찰 포인트

k6 출력만으로 검증되지 않는 부분 — 별도 도구로 함께 관찰:

| 관찰 대상 | 도구 |
|---|---|
| Resilience4j CB 상태 / 전이 카운터 | `curl http://localhost:8081/actuator/circuitbreakers` |
| Prometheus 메트릭 (CB·retry·메트릭 카운터) | `curl http://localhost:8081/actuator/prometheus \| grep -E 'circuit\|retry\|saga'` |
| 보상 이벤트 발행 수 | `kafka-console-consumer --topic inventory.release-requested.v1 --from-beginning` |
| inventory release 처리 로그 | inventory-service 콘솔 (`InventoryReleaseRequestedListener` 로그) |

## 결과 기록 양식 (ADR-004 보정 근거)

실행 후 아래 표를 채워 ADR-004 검증 절에 반영하면 "잠정 → 확정" 전환 가능:

```
### baseline
- 환경: <macOS / Java 21 / 실행 머신 사양>
- 실행 시각: <YYYY-MM-DD>
- RPS (steady): <값>
- p95: <값>ms / p99: <값>ms
- 실패율: <%>
- 해석: <ADR-004의 read-timeout 1000ms·CB 수치가 정상 부하에 적합한지>

### inventory-delay (FAULT_DELAY_MS=1500)
- 관찰: CB가 N번째 호출 전후로 OPEN 전이 (slow-call rate ?% / failure rate ?%)
- 503 응답 시점: <시간>
- 발행된 보상 이벤트 수: <값>
- 해석: <slow-call-duration-threshold 800ms / wait-duration-in-open 5s가 적절한지>

### inventory-error (FAULT_ERROR_RATE=0.3)
- 관찰: failure rate ?% 도달 → CB OPEN 시점
- half-open 복귀 후 거동: <CLOSED 복귀 / 다시 OPEN>
- 해석: <failure-rate-threshold 50% / sliding-window-size 20이 적절한지>
```

## 보너스

GitHub Actions에 `k6 run` 스모크를 추가하면 가점. 다만 본 시나리오는 실제 Kafka·서비스 기동이 필요해 CI 통합이 단순하지 않음 — `baseline`만 docker-compose 기반 CI 잡으로 분리하는 정도가 현실적.
