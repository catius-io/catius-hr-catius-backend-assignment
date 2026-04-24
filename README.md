# 카티어스 백엔드 개발자 채용 — 과제 전형 (4년차)

> **과제명**: 주문-재고 처리 미니 MSA 구현
> **소요 시간 가이드**: 10~14시간 (주말 1~2회 권장)
> **제출 기한**: 과제 수령 후 **7일 이내**

---

## 1. 이 과제로 보고자 하는 것

카티어스 백엔드 팀이 일상적으로 다루는 문제의 **축소판**을 직접 구현해주시면 됩니다. 완성도보다 **설계 결정의 근거·트레이드오프 설명·품질에 대한 태도**를 더 비중 있게 봅니다.

구체적으로는 다음을 확인합니다.

- **MSA 환경에서 서비스 간 통신 설계** — Feign, 서킷 브레이커, 타임아웃, 재시도
- **장애·지연에 대한 기본 대응** — 외부 서비스가 느리거나 실패할 때 내 서비스가 함께 무너지지 않도록 방어
- **성능을 숫자로 말하는 태도** — k6로 시나리오를 직접 작성·실행·해석
- **코드 품질과 테스트 습관** — 레이어 분리, 단위·통합 테스트
- **설계를 글로 남기는 능력** — README의 구조와 트레이드오프 기술

> Kafka 이벤트 발행, Saga 보상 트랜잭션은 **보너스 영역**입니다. 필수 구현에 먼저 집중해 주세요.

---

## 2. 도메인 시나리오

커머스 도메인의 일부를 축소해 구현합니다. **Order 서비스**가 주문을 받으면 **Inventory 서비스**에 재고 차감을 요청하고, 재고 차감 결과에 따라 주문을 확정하거나 취소합니다.

```
[Client] ──POST /orders──> [Order Service] ──Feign──> [Inventory Service]
                                  │                          │
                                  │                          └── (재고 차감)
                                  │
                                  └── 상태 저장 (CONFIRMED / CANCELED)
```

### 성공 흐름
1. 클라이언트 → Order: 주문 생성 요청
2. Order → Inventory (Feign): 재고 차감 요청
3. Inventory: 재고 차감 성공 응답
4. Order: 주문 상태를 `CONFIRMED`로 저장

### 실패 흐름
- Inventory 장애 / 타임아웃 / 재고 부족 시 → Order는 **서킷 브레이커로 보호받으며** 주문 상태를 `CANCELED`로 저장
- 서킷이 열린 상태의 후속 요청은 즉시 실패 처리 (fallback 동작)

---

## 3. 필수 구현 사항 (Must)

### 3.1 서비스 구성
- **두 개의 독립된 Spring Boot 애플리케이션**: `order-service`, `inventory-service`
- Gradle 멀티 프로젝트 구조
- 각 서비스는 각자의 **SQLite DB** 파일 사용 (예: `order-service.db`, `inventory-service.db`)

### 3.2 API
- **Order 서비스**
  - `POST /api/v1/orders` — 주문 생성
  - `GET /api/v1/orders/{id}` — 주문 조회
- **Inventory 서비스**
  - `POST /api/v1/inventory/reserve` — 재고 차감
  - `GET /api/v1/inventory/{productId}` — 재고 조회

(정확한 요청·응답 스키마는 본인이 설계하고 README에 기재)

### 3.3 서비스 간 통신
- Order → Inventory 재고 차감 호출은 **Feign Client** 사용
- Feign 호출에 **Resilience4j 서킷 브레이커 + 타임아웃** 적용 (재시도는 필요하다고 판단되면 추가)
- 서킷 브레이커 설정값(실패율 임계치, 슬라이딩 윈도우 크기, open 유지 시간 등)은 본인이 근거를 가지고 정하고 README에 간단히 설명
- 서킷이 open 되었을 때의 **fallback 동작**을 정의 (예: 주문을 `CANCELED` 상태로 저장)

### 3.4 영속성
- **SQLite** (`org.xerial:sqlite-jdbc`) + **Spring Data JPA** (Hibernate dialect: `org.hibernate.community.dialect.SQLiteDialect`)
- 스키마 관리는 자동 DDL(`ddl-auto`) 또는 Flyway 중 선택 (선택 이유를 README에 한 줄 기재)

### 3.5 테스트
- **단위 테스트**: Order·Inventory의 핵심 도메인 로직 (주문 상태 전이, 재고 차감 규칙 등)
- **통합 테스트 (최소 1개)**: Feign 호출 실패 또는 타임아웃 시 서킷 브레이커·fallback이 동작하는지 검증
  - WireMock, MockWebServer, 또는 로컬 모의 서버를 활용해도 됩니다

### 3.6 성능 테스트
- **k6 스크립트 1개** 작성 (`perf/` 디렉터리 권장)
- 주문 생성 시나리오로 **목표 처리량, 허용 실패율, p95 응답 시간**을 본인이 정의하고 검증
- 실행 결과를 README에 요약 (수치 + 한 문단 해석)

### 3.7 실행 편의성
- `./gradlew bootRun` 또는 동등한 명령으로 두 서비스가 **외부 인프라 의존 없이** 기동되어야 함
- SQLite 덕분에 Docker·별도 DB 설치 불필요

---

## 4. 보너스 구현 (Nice to have)

시간이 남는 경우에 한해 하나 이상 도전해 보세요. **필수 항목을 다 끝낸 뒤에 시도**하시길 권장합니다.

- **Kafka 이벤트 발행** — 주문 확정 시 `OrderConfirmed` 이벤트를 Kafka 토픽에 발행, 간단한 컨슈머로 수신 후 로그 기록. 로컬 실행 시 외부 브로커 없이 동작하도록 **Embedded Kafka** (`spring-kafka-test`) 활용
- **Saga 보상 트랜잭션** — 재고 차감은 성공했으나 주문 저장이 실패하는 상황에서 재고를 복원하는 보상 로직 포함. 이 경우 Inventory에 `POST /api/v1/inventory/release` 엔드포인트 추가
- **커스텀 메트릭** — `/actuator/prometheus` 엔드포인트에 주문·재고 관련 카운터 1개 이상 노출
- **GitHub Actions** — 빌드·테스트 자동 실행 워크플로우 1개

보너스는 **적용의 깊이**로 평가합니다. 구색 맞추기식 구현보다, 하나를 제대로 설명할 수 있는 쪽이 더 좋습니다.

---

## 5. 스캐폴드 저장소 구조

카티어스가 공개 템플릿 저장소로 제공하는 **초기 구조**입니다. 클래스 구현은 **전부 비어 있고**, 지원자가 채웁니다.

```
catius-backend-assignment/
├── README.md                       ← 지원자가 설계·실행법·트레이드오프 기술
├── settings.gradle
├── build.gradle                    ← 공통 플러그인·의존성(Spring Boot, Feign, Resilience4j, spring-kafka, sqlite-jdbc, hibernate-community-dialects, spring-kafka-test 등)
├── order-service/
│   ├── build.gradle
│   └── src/
│       ├── main/
│       │   ├── java/com/catius/order/
│       │   │   ├── controller/     ← .gitkeep  (REST 엔드포인트)
│       │   │   ├── service/        ← .gitkeep  (비즈니스 로직, Feign 호출, 상태 전이)
│       │   │   ├── domain/         ← .gitkeep  (엔티티·값 객체)
│       │   │   ├── repository/     ← .gitkeep  (Spring Data JPA 리포지토리)
│       │   │   └── OrderServiceApplication.java   ← @SpringBootApplication 한 줄
│       │   └── resources/
│       │       └── application.yml ← SQLite·Feign·Resilience4j·Kafka 기본 설정 예시
│       └── test/
│           └── java/com/catius/order/
│               └── .gitkeep
├── inventory-service/
│   └── (order-service와 동일 구조)
└── perf/
    ├── .gitkeep                    ← k6 스크립트 위치 예시
    └── README.md                   ← k6 실행 방법 안내
```

> 스캐폴드의 `build.gradle`에는 Kafka 관련 의존성이 미리 포함되어 있습니다. **보너스로 Kafka를 시도하지 않는 지원자도 빌드에는 영향 없습니다.**

---

## 6. 기술 스택 (고정)

| 영역 | 사용 기술 |
|---|---|
| 언어 | **Java 21** 또는 **Kotlin 1.9+** |
| 프레임워크 | **Spring Boot 3.x** |
| DB | **SQLite** + Spring Data JPA |
| 서비스 간 통신 | **OpenFeign** |
| 서킷 브레이커 | **Resilience4j** |
| 메시징 (보너스) | **Spring for Apache Kafka** + **Embedded Kafka** (`spring-kafka-test`) |
| 빌드 | **Gradle** |
| 부하 테스트 | **k6** |

---

## 7. 제출 방식 (GitHub Fork + Pull Request)

1. 카티어스가 제공하는 템플릿 저장소를 **Fork**합니다.
2. **본인의 Fork 저장소**에 클론하여 작업합니다.
3. 각 논리 단계마다 **feature 브랜치**를 만들어 커밋을 쌓습니다.
   - 예: `feat/order-api`, `feat/inventory-service`, `feat/feign-resilience4j`, `feat/perf-k6`
4. 최종적으로 **본인 Fork의 `main` 브랜치로 PR**을 올립니다.
   - PR 한 개에 전체 작업을 올리는 대신, **기능 단위로 여러 PR을 쌓는 것을 권장**합니다. 일부는 Draft여도 좋습니다.
   - 팀 리뷰 문화를 보여주는 방식이므로, PR 설명에 **의도·변경 요약·테스트 방법·스스로 확인한 한계**를 남겨주세요.
5. 제출 시에는 다음을 메일로 보내주세요.
   - Fork 저장소 URL
   - 핵심 PR URL 목록 (최소 하나)
   - 최종 커밋 SHA

> **주의**: 카티어스 원본 템플릿 저장소로는 PR을 올리지 마세요. 본인 Fork 내에서 완결되어야 합니다.

---

## 8. 제출물 (README에 반드시 포함)

- **실행 방법** — 커맨드 한두 줄로 빌드·기동·테스트까지
- **아키텍처 다이어그램** — 이미지 또는 ASCII 모두 OK
- **설계 결정과 트레이드오프**
  - 서비스 경계를 왜 이렇게 잡았는지
  - 서킷 브레이커 임계값을 어떻게 정했는지와 근거
  - 실패 시 상태 전이 규칙 (`CANCELED` 처리 기준)
  - 테스트 전략 요약
- **성능 테스트 결과** — k6 출력 요약 + 수치 해석 한두 문단
- **의도적으로 하지 않은 것** — 시간 제약으로 뺀 항목과 이유 (최소 1개)

---

## 9. 평가 루브릭

| 평가 영역 | 가중 | 확인 내용 |
|---|---|---|
| **기능 동작** | 25% | 성공·실패 시나리오가 end-to-end로 동작 |
| **서비스 간 통신 설계** | 20% | Feign, 서킷 브레이커, 타임아웃, fallback의 근거 있는 설정 |
| **성능 테스트의 현실성과 해석** | 15% | 시나리오 설계·목표 수치 근거·결과 해석의 깊이 |
| **코드 품질과 테스트** | 15% | 레이어 분리, 네이밍, 단위·통합 테스트 포함 |
| **설계 문서와 트레이드오프 기술** | 15% | README의 구조, 근거 있는 선택 설명 |
| **보너스** | 10% | Kafka / Saga / 메트릭 / CI 중 하나 이상의 깊이 있는 적용 |

**감점 요소**: 커밋이 한 덩어리로 몰려있음 / 테스트 전무 / README 부재 / 서킷 브레이커 누락 / PR 설명 부실.

---

## 10. 자주 묻는 질문

**Q. Inventory 서비스가 너무 단순해 보이는데 더 복잡하게 만들어야 하나요?**
A. 아니요. **핵심 요구를 모두 만족시키는 가장 단순한 구현**을 권장합니다. 남은 시간은 테스트·문서·성능 해석에 쓰세요.

**Q. 재시도는 꼭 넣어야 하나요?**
A. 필수는 아닙니다. 서킷 브레이커 + 타임아웃이 필수이고, 재시도는 **넣은 근거 / 넣지 않은 근거**만 README에 적으시면 됩니다.

**Q. Kafka 없이 제출해도 감점되나요?**
A. 아니요. 이 과제에서 Kafka는 **보너스**입니다. 필수 항목을 탄탄히 하는 쪽이 더 유리합니다.

**Q. Saga 보상은 꼭 구현해야 하나요?**
A. 아니요, 보너스입니다. 재고 차감 실패 → `CANCELED`로 기록하는 단방향 실패 처리까지가 필수입니다.

**Q. Kafka를 docker-compose로 띄워도 되나요?**
A. 보너스로 Kafka를 시도하는 경우, **Embedded Kafka를 권장**합니다. 외부 의존 없는 실행을 보여주세요. 불가피한 이유가 있다면 README에 설명.

**Q. 생성형 AI(GitHub Copilot, Claude 등) 사용은 허용되나요?**
A. 네, 현업에서도 쓰는 도구라 허용합니다. **다만 본인이 설명할 수 없는 코드는 포함시키지 마세요.** 후속 인터뷰에서 코드 한 줄 한 줄을 본인 말로 설명할 수 있어야 합니다.

**Q. 시간이 부족하면 어떻게 하나요?**
A. 억지로 모든 걸 끝내기보다, **핵심 요구 일부에 집중**하고 나머지는 README의 "의도적으로 하지 않은 것"에 솔직히 기재하세요. 판단력도 평가 대상입니다.

---

## 11. 후속 프로세스

과제 제출 후 **1차 인터뷰(기술 + 컬처 핏)**가 진행됩니다. **제출한 과제 리뷰가 기술 파트의 메인**이며, 같은 자리에서 일하는 방식·커뮤니케이션·성장 관점의 대화도 함께 나눕니다.

다음을 준비해 주세요.

- 주요 설계 결정을 2~3분 안에 구두로 설명할 수 있도록
- "다시 시작한다면 무엇을 다르게 하겠는가"에 대한 생각
- 카티어스 실제 환경(MariaDB, 실 Kafka, Kubernetes, 실제 트래픽)에서는 어떤 부분이 달라져야 하는가에 대한 감각
- 평소 어떻게 일하고, 막혔을 때 어떻게 풀어나가는지에 대한 본인만의 이야기

인터뷰는 **코드 심문이 아니라 대화**입니다. 편하게 오세요.

포지션·팀 상황에 따라 **2차 인터뷰가 추가로 진행될 수 있습니다.** 필요한 경우 1차 종료 후 별도 안내드립니다.
