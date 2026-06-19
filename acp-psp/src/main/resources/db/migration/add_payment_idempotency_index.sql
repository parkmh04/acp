-- 결제 준비(PREPARE) 멱등성 보장용 부분 unique 인덱스.
-- 불변 장부 특성상 APPROVE/CANCEL 은 여러 행이 가능하나,
-- 가맹점 주문번호(merchant_order_id)당 PREPARE 는 단 1건만 허용하여
-- 동시 요청 레이스(check-then-insert)로 인한 중복 결제 준비를 DB 레벨에서 차단한다.
CREATE UNIQUE INDEX IF NOT EXISTS uq_payments_prepare_per_order
    ON psp.payments (merchant_order_id)
    WHERE type = 'PREPARE';
