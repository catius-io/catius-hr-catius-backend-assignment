-- Hibernate community SQLite dialect는 @UniqueConstraint / @Index를 DDL로 emit하지 않음.
-- Hibernate가 테이블을 만든 뒤 이 스크립트가 실행되어 (defer-datasource-initialization=true)
-- 멱등성 키를 보장하는 UNIQUE 인덱스를 보강한다.
-- 이 인덱스는 INSERT OR IGNORE 가 (order_id, product_id) 충돌을 인식하는 근거 (ADR-002).
CREATE UNIQUE INDEX IF NOT EXISTS uk_reservations_order_product
    ON reservations(order_id, product_id);
