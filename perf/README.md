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
