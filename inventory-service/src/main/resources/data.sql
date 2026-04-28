-- k6 부하 테스트용 시드 데이터
-- 상품 5종 × 10,000,000개 재고
MERGE INTO inventory (product_id, available_quantity, updated_at) KEY (product_id) VALUES (1001, 10000000, CURRENT_TIMESTAMP());
MERGE INTO inventory (product_id, available_quantity, updated_at) KEY (product_id) VALUES (1002, 10000000, CURRENT_TIMESTAMP());
MERGE INTO inventory (product_id, available_quantity, updated_at) KEY (product_id) VALUES (1003, 10000000, CURRENT_TIMESTAMP());
MERGE INTO inventory (product_id, available_quantity, updated_at) KEY (product_id) VALUES (1004, 10000000, CURRENT_TIMESTAMP());
MERGE INTO inventory (product_id, available_quantity, updated_at) KEY (product_id) VALUES (1005, 10000000, CURRENT_TIMESTAMP());
