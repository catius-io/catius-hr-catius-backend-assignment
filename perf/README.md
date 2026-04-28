# k6 성능 테스트

이 디렉터리에 k6 스크립트를 작성합니다.

## 설치

macOS:
```bash
brew install k6
```

기타 OS: https://k6.io/docs/get-started/installation/

## 권장 시나리오 (최소 1개 이상)

- `scenarios/create-order.js` — 주문 생성 엔드포인트에 대한 부하 시나리오
  - 점진 증가(ramp-up), 일정 유지(steady), 감소(ramp-down) 단계 포함
  - 목표 처리량(rps), 허용 실패율, **p95 응답 시간 SLO** 정의
  - `thresholds` 로 SLO 위반 시 테스트가 실패하도록 구성

## 시나리오

| 파일 | 목적 | VU | 시간 |
|---|---|---|---|
| `scenarios/create-order.js` | 주문 생성 부하 테스트 | 0→10→0 | 110s |

## SLO 정의

| 지표 | 임계값 | 설명 |
|---|---|---|
| `p(95)` 응답시간 | < 500ms | 95%의 요청이 0.5초 안에 응답 |
| `p(99)` 응답시간 | < 1000ms | 99%의 요청이 1초 안에 응답 |
| 에러율 | < 1% | 4xx/5xx 합산 |
| Saga 정상 종료율 | > 90% | CONFIRMED 또는 보상 완료(CANCELLED) 상태로 종료된 주문 비율 |

## 실행

```bash
# 부하 테스트
k6 run perf/scenarios/create-order.js

# 서비스 URL 지정
BASE_URL=http://localhost:8081 INV_URL=http://localhost:8082 k6 run perf/scenarios/create-order.js

```

## 제출 시 권장 사항

- k6 출력 요약(최소 failed rate, p95, throughput)을 README 또는 이 파일 하단에 **수치 + 해석**으로 남겨주세요.
- 병목을 찾았다면 **어떤 수단(쿼리 플랜·프로파일러·캐시 히트율)** 으로 원인을 특정했는지, 어떻게 개선했는지 근거를 함께 적어주세요.

## 보너스

- GitHub Actions 에 `k6 run` 을 붙여 성능 시나리오를 CI 에서 자동화하면 가점.
  예) `actions/checkout@v4` → `grafana/setup-k6-action@v1` → `grafana/run-k6-action@v1`

--- 

## 병목 해결
- 측정 결과
  - http_req_duration p95 = 260.25ms
  - http_req_failed = 13.26%
  - status is 201 실패 = 39건

- 원인 측정
  - k6 결과상 응답시간 p95는 SLO를 만족했지만 실패율이 높아, 병목을 지연시간이 아닌 요청 실패율로 판단했다.
  - 실패 요청과 같은 시간대의 order-service 로그에서 `SQLITE_BUSY`, `database is locked` 발생 여부를 확인했다.
  - SQLite 주문 DB의 상태 분포를 조회해 `PENDING` 주문이 남는지 확인하여 Saga 상태 갱신 실패 가능성을 검증했다.
  - 재고 DB의 상품 수량을 확인해 재고 부족이 주요 실패 원인이 아님을 분리했다.

- 해결 방향
  - SQLite datasource에 WAL 모드와 `busy_timeout`을 적용했다.
  - Hikari connection pool을 단일 커넥션으로 제한해 동시 write 경합을 줄였다.
  - Saga 상태 저장 시 `SQLITE_BUSY`가 발생하면 짧게 재시도하도록 보강했다.

- 결과
  - http_req_duration p95 = 271.54ms
  - http_req_failed = 0%
