#!/bin/sh
# PostgreSQL 컨테이너 최초 기동 시 1회 실행되어 ACP 스키마/테이블을 생성합니다.
# (docker-entrypoint-initdb.d 규약: 데이터 볼륨이 비어있을 때만 실행)
#
# 마이그레이션 SQL은 실제 모듈 경로를 read-only 바인드 마운트한 것으로,
# 별도 사본 없이 단일 소스(db/migration)를 그대로 사용합니다.
# 의존성 순서가 있으므로 아래 명시적 순서로 적용합니다.
set -e

echo "[acp-init] applying schema migrations..."

for f in \
  /migrations/merchant/init_merchant_products_orders.sql \
  /migrations/merchant/create_checkout_sessions.sql \
  /migrations/merchant/add_fulfillment_option.sql \
  /migrations/merchant/expand_products_spec.sql \
  /migrations/psp/init_psp_payments.sql \
  /migrations/psp/add_payment_idempotency_index.sql
do
  echo "[acp-init]  -> ${f}"
  psql -v ON_ERROR_STOP=1 --username "${POSTGRES_USER}" --dbname "${POSTGRES_DB}" -f "${f}"
done

echo "[acp-init] done. schemas: merchant, psp"
