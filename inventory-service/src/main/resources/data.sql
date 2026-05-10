-- 기본 시드 — README 호출 예시(productId 1001~1003)가 클린 클론 + bootRun 직후 즉시 동작하도록.
-- INSERT OR IGNORE — 재기동 시 중복 INSERT 회피.
-- k6 부하 시나리오는 application-perf.yml의 data-locations 오버라이드로 data-perf.sql만 로드.

INSERT OR IGNORE INTO inventory (product_id, quantity) VALUES
    (1001, 1000),
    (1002, 1000),
    (1003, 1000);
