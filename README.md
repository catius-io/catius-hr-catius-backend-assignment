# 카티어스 백엔드 과제 템플릿 — 주문-재고 미니 MSA

> 이 저장소는 카티어스 백엔드 개발자 채용의 **과제 전형 스캐폴드**입니다.
> 지원자는 이 저장소를 **Fork** 하여 본인의 저장소에서 구현을 완성한 뒤, 본인 Fork 내에서 PR을 올리고 PR URL을 제출합니다.

---

## 스캐폴드 구성

```
catius-backend-assignment/
├── build.gradle
├── settings.gradle
├── gradle.properties
├── gradlew / gradlew.bat / gradle/wrapper/
├── order-service/
│   ├── build.gradle
│   └── src/
│       ├── main/
│       │   ├── java/com/catius/order/
│       │   │   ├── controller/    ← REST 엔드포인트를 여기에
│       │   │   ├── service/       ← 비즈니스 로직, Feign 호출, Saga 오케스트레이션, 이벤트 발행
│       │   │   ├── domain/        ← 엔티티·값 객체·도메인 이벤트
│       │   │   ├── repository/    ← Spring Data JPA 리포지토리
│       │   │   └── OrderServiceApplication.java
│       │   └── resources/application.yml
│       └── test/java/com/catius/order/
├── inventory-service/  (동일 구조)
└── perf/               (k6 스크립트 위치)
```

현재 네 개의 패키지는 **비어 있습니다.** 각 패키지의 책임을 따라 지원자가 직접 클래스를 생성해 구현합니다.

---

## 기술 스택 (고정)

- **Java 21** 또는 **Kotlin 1.9+** (본 스캐폴드는 Java 21)
- **Spring Boot 3.3.x**
- **Spring Data JPA + SQLite** (community dialect)
- **OpenFeign** + **Resilience4j**
- **Spring for Apache Kafka** + **Embedded Kafka** (테스트)
- **k6** (부하 테스트)
- **Gradle 멀티 프로젝트**

---

## 실행

### 최초 1회: Gradle Wrapper 생성

이 스캐폴드에는 wrapper jar 파일이 포함되어 있지 않습니다. 저장소를 Fork 받은 뒤, 로컬에 설치된 Gradle(또는 IntelliJ의 내장 Gradle)을 이용해 wrapper를 한 번 생성해주세요.

```bash
# 로컬에 gradle 이 설치돼 있다면
gradle wrapper --gradle-version 8.10.2

# 또는 IntelliJ 에서: Gradle 창 → "Reload All Gradle Projects" 후 wrapper 자동 생성
```

생성된 `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar` 는 **첫 커밋에 포함시켜 주세요.** 이후에는 `./gradlew` 로 바로 실행할 수 있습니다.

### 전체 빌드·테스트
```bash
./gradlew build
```

### 개별 서비스 기동
```bash
./gradlew :order-service:bootRun
./gradlew :inventory-service:bootRun
```

- order-service 기본 포트: **8081**
- inventory-service 기본 포트: **8082**

각 서비스는 SQLite 파일(`order-service.db`, `inventory-service.db`)을 현재 디렉터리에 생성합니다. `.gitignore`에 포함되어 있어 커밋되지 않습니다.

### Kafka
- **테스트 환경**: `spring-kafka-test`의 `@EmbeddedKafka`를 활용해 외부 브로커 없이 동작
- **로컬 전체 기동 시**: 지원자가 선택
  - (권장) 테스트로만 검증하고, 로컬 `bootRun` 시 `spring.kafka.listener.auto-startup=false` 등으로 컨슈머 비활성화
  - 필요 시 Embedded Kafka를 애플리케이션 시작 시 수동으로 부트스트랩하는 구성을 직접 추가
  - 또는 로컬에서 간이 `docker-compose.yml`을 제작해 Kafka 브로커를 띄움 (제출물에 포함 가능)

---

## 구현해야 할 것

전체 요구사항, 평가 기준, 제출 절차는 과제 안내서를 따릅니다. 이 README는 **스캐폴드 사용 안내**만 다룹니다.

실제 과제 명세: `카티어스_백엔드_과제전형.md` (채용 공고 메일에 동봉)

---

## 제출 흐름 (요약)

1. 이 저장소를 **Fork**
2. 본인 Fork에서 `feature/*` 브랜치로 작업, 기능 단위로 PR 생성
3. 최종 PR(들)을 본인 Fork의 `main`으로 머지 요청 상태로 유지
4. Fork URL + PR URL 목록 + 최종 커밋 SHA를 제출 메일로 회신

---

## 문의

질문이 있다면 이슈를 남기지 마시고, 채용 담당자에게 메일로 문의해 주세요.
