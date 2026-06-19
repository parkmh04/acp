# 🗄️ 데이터베이스 스키마 설계

> **설계 원칙**: Merchant와 PSP 도메인을 물리적으로 분리하여 각각 독립적인 스키마(`merchant`, `psp`)를 소유하며, jOOQ를 통한 Type-Safe 접근으로 데이터 무결성을 보장합니다.
>
> 본 문서의 **"현재 구현된 스키마"** 섹션은 `src/main/resources/db/migration/`의 실제 SQL과 1:1로 일치합니다. 아직 구현되지 않은 설계는 마지막 **"향후 확장 설계 (미구현)"** 섹션에 별도로 분리했습니다.

---

## 📐 스키마 개요

### 현재 구현된 테이블

```
PostgreSQL 16
├── Schema: merchant    (Merchant 서버 전용)
│   ├── products
│   ├── product_images
│   ├── orders
│   ├── order_lines
│   ├── checkout_sessions
│   └── checkout_items
│
└── Schema: psp         (PSP 서버 전용)
    ├── payment_partner_meta
    ├── payments
    └── external_payment_transactions
```

### 격리 전략

- **물리적 분리**: Merchant와 PSP는 각자의 스키마만 접근
- **물리 FK 제거**: 분산 환경을 고려하여 테이블 간 물리적 외래키 제약을 제거하고, 참조 무결성은 애플리케이션 레벨에서 관리 (논리적 FK)
  - 예: `merchant.orders.payment_request_id` → `psp.payments.id`
  - 예: `merchant.checkout_items.checkout_session_id` → `merchant.checkout_sessions.id`
- **마이그레이션**: Flyway 미사용. `db/migration/`의 수동 SQL 스크립트를 직접 실행

---

## 🛒 Merchant 도메인 (Schema: `merchant`)

> 파일: `acp-merchant/src/main/resources/db/migration/`
> (`init_merchant_products_orders.sql`, `create_checkout_sessions.sql`, `add_fulfillment_option.sql`, `expand_products_spec.sql`)

### 1. `products` - 상품 정보

OpenAI Product Feed Spec 기반 상품 마스터 테이블. 기본 컬럼은 `init_merchant_products_orders.sql`에서 생성되고, 확장 필드는 `expand_products_spec.sql`에서 추가됩니다.

```sql
CREATE TABLE IF NOT EXISTS merchant.products (
    id              VARCHAR(255) PRIMARY KEY, -- SKU or UUID
    title           VARCHAR(255) NOT NULL,
    description     TEXT,
    link            TEXT NOT NULL,
    image_link      TEXT NOT NULL,
    price_amount    NUMERIC(19, 4) NOT NULL,
    currency        VARCHAR(3) NOT NULL DEFAULT 'KRW',
    availability    VARCHAR(50) NOT NULL,
    inventory_qty   INTEGER NOT NULL DEFAULT 0,
    condition       VARCHAR(50) NOT NULL DEFAULT 'new',
    category        VARCHAR(255),
    brand           VARCHAR(255),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- expand_products_spec.sql (Product Feed Spec 확장 필드)
ALTER TABLE merchant.products
    ADD COLUMN IF NOT EXISTS sale_price_amount         NUMERIC(19, 4),
    ADD COLUMN IF NOT EXISTS sale_price_effective_date VARCHAR(100),
    ADD COLUMN IF NOT EXISTS gtin                      VARCHAR(100),
    ADD COLUMN IF NOT EXISTS mpn                       VARCHAR(100),
    ADD COLUMN IF NOT EXISTS merchant_name             VARCHAR(255),
    ADD COLUMN IF NOT EXISTS merchant_url              TEXT,
    ADD COLUMN IF NOT EXISTS shipping_weight           VARCHAR(100),
    ADD COLUMN IF NOT EXISTS return_policy_days        INTEGER,
    ADD COLUMN IF NOT EXISTS reviews_average_rating    NUMERIC(3, 2),
    ADD COLUMN IF NOT EXISTS reviews_count             INTEGER DEFAULT 0;
```

---

### 2. `product_images` - 상품 이미지 (다중 이미지 지원)

`expand_products_spec.sql`에서 생성됩니다.

```sql
CREATE TABLE IF NOT EXISTS merchant.product_images (
    id              BIGSERIAL PRIMARY KEY,
    product_id      VARCHAR(255) NOT NULL, -- 논리적 FK: merchant.products(id)
    image_url       TEXT NOT NULL,
    image_type      VARCHAR(50) NOT NULL, -- main, additional, thumbnail
    display_order   INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_product_images_product_id ON merchant.product_images(product_id);
```

---

### 3. `orders` - 주문 정보

```sql
CREATE TABLE IF NOT EXISTS merchant.orders (
    id                 VARCHAR(36) PRIMARY KEY, -- UUID
    user_id            VARCHAR(255) NOT NULL,
    status             VARCHAR(50) NOT NULL, -- PENDING, AUTHORIZED, COMPLETED, CANCELED, FAILED
    total_amount       NUMERIC(19, 4) NOT NULL,
    currency           VARCHAR(3) NOT NULL DEFAULT 'KRW',
    payment_request_id VARCHAR(36), -- 논리적 FK: psp.payments.id
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_orders_user ON merchant.orders(user_id);
```

> 주문 상태 enum은 `OrderStatus.kt`(PENDING, AUTHORIZED, COMPLETED, CANCELED, FAILED)를 기준으로 합니다.

---

### 4. `order_lines` - 주문 상품 항목

```sql
CREATE TABLE IF NOT EXISTS merchant.order_lines (
    id          VARCHAR(36) PRIMARY KEY,
    order_id    VARCHAR(36) NOT NULL,  -- 논리적 FK: merchant.orders(id)
    product_id  VARCHAR(255) NOT NULL, -- 논리적 FK: merchant.products(id)
    quantity    INTEGER NOT NULL,
    unit_price  NUMERIC(19, 4) NOT NULL,
    currency    VARCHAR(3) NOT NULL DEFAULT 'KRW',
    total_price NUMERIC(19, 4) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_order_lines_order_id   ON merchant.order_lines(order_id);
CREATE INDEX IF NOT EXISTS idx_order_lines_product_id ON merchant.order_lines(product_id);
```

---

### 5. `checkout_sessions` - 체크아웃 세션 (임시 장바구니)

기본 컬럼은 `create_checkout_sessions.sql`, 배송 옵션 컬럼은 `add_fulfillment_option.sql`에서 추가됩니다.

```sql
CREATE TABLE IF NOT EXISTS merchant.checkout_sessions (
    id                           VARCHAR(255) PRIMARY KEY,
    status                       VARCHAR(50) NOT NULL, -- NOT_READY, READY, COMPLETED, CANCELED
    currency                     VARCHAR(3) NOT NULL DEFAULT 'KRW',
    total_amount                 NUMERIC(19, 4) NOT NULL DEFAULT 0,

    -- Buyer Info
    buyer_email                  VARCHAR(255),
    buyer_name                   VARCHAR(255),

    -- Fulfillment Address
    shipping_address_country     VARCHAR(10),
    shipping_address_postal_code VARCHAR(20),

    -- Next Action
    next_action_url              TEXT,

    created_at                   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at                   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at                   TIMESTAMP WITH TIME ZONE
);

-- add_fulfillment_option.sql (배송 옵션)
ALTER TABLE merchant.checkout_sessions
    ADD COLUMN IF NOT EXISTS selected_fulfillment_option VARCHAR(50);  -- standard, express, same_day
ALTER TABLE merchant.checkout_sessions
    ADD COLUMN IF NOT EXISTS shipping_cost               NUMERIC(19, 2);
```

> 세션 상태 enum은 `CheckoutStatus.kt`(NOT_READY, READY, COMPLETED, CANCELED)를 기준으로 합니다.

---

### 6. `checkout_items` - 체크아웃 세션 상품 항목

```sql
CREATE TABLE IF NOT EXISTS merchant.checkout_items (
    id                  BIGSERIAL PRIMARY KEY,
    checkout_session_id VARCHAR(255) NOT NULL, -- 논리적 FK: merchant.checkout_sessions(id)
    product_id          VARCHAR(255) NOT NULL, -- 논리적 FK: merchant.products(id)
    quantity            INTEGER NOT NULL,

    -- 추가 시점 가격 스냅샷
    unit_price          NUMERIC(19, 4) NOT NULL,
    total_price         NUMERIC(19, 4) NOT NULL,

    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_checkout_items_session_id ON merchant.checkout_items(checkout_session_id);
```

---

## 💳 PSP 도메인 (Schema: `psp`)

> 파일: `acp-psp/src/main/resources/db/migration/init_psp_payments.sql`

### 1. `payment_partner_meta` - 결제 제공자 설정

```sql
CREATE TABLE IF NOT EXISTS psp.payment_partner_meta (
    id            VARCHAR(36) PRIMARY KEY,
    provider      VARCHAR(50) NOT NULL,  -- kakaopay, stripe, toss
    client_id     VARCHAR(255),
    client_secret VARCHAR(255),          -- 암호화 대상
    merchant_cid  VARCHAR(255),          -- 가맹점 코드
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
```

---

### 2. `payments` - 결제 트랜잭션 (Immutable Ledger)

결제 상태를 갱신(UPDATE)하지 않고, 모든 이벤트(준비/승인/취소)를 **새로운 행**으로 적재하는 불변 장부입니다. 동일 `merchant_order_id`로 그룹핑하며, `org_payment_id`로 관련 트랜잭션을 연결합니다.

```sql
CREATE TABLE psp.payments (
    id                  VARCHAR(100) PRIMARY KEY, -- UUID
    merchant_order_id   VARCHAR(100) NOT NULL,    -- 가맹점 주문번호 (그룹핑 키)
    org_payment_id      VARCHAR(100),             -- 원본 결제 ID (취소→승인, 승인→준비 참조)

    -- Transaction Info
    type                VARCHAR(20) NOT NULL,     -- PREPARE, APPROVE, CANCEL
    status              VARCHAR(20) NOT NULL,     -- SUCCESS, FAIL, UNKNOWN

    -- PG Info
    pg_provider         VARCHAR(20) NOT NULL DEFAULT 'KAKAOPAY',
    pg_tid              VARCHAR(100),             -- PG사 트랜잭션 ID (암호화 저장)
    pg_token            VARCHAR(255),             -- 승인 토큰

    -- Amount
    amount              BIGINT NOT NULL,
    tax_free_amount     BIGINT DEFAULT 0,
    currency            VARCHAR(3) NOT NULL DEFAULT 'KRW',

    -- Payment Method Info (역정규화 - 승인 시 저장)
    payment_method_type VARCHAR(20),              -- CARD, MONEY
    card_issuer         VARCHAR(50),
    card_number_masked  VARCHAR(20),
    installments        INTEGER DEFAULT 0,

    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payments_merchant_order_id ON psp.payments(merchant_order_id);
CREATE INDEX idx_payments_pg_tid            ON psp.payments(pg_tid);
CREATE INDEX idx_payments_created_at        ON psp.payments(created_at);

-- 결제 준비(PREPARE) 멱등성: 주문번호당 PREPARE 1건만 허용 (add_payment_idempotency_index.sql)
CREATE UNIQUE INDEX uq_payments_prepare_per_order
    ON psp.payments (merchant_order_id) WHERE type = 'PREPARE';
```

> **멱등성**: 별도의 `idempotency_keys` 테이블 없이, `PaymentService`가 `merchant_order_id` + `type` 조합으로 기존 트랜잭션을 조회하여 중복을 방지합니다. 추가로 동시 요청 레이스(check-then-insert)에 대비해 PREPARE에 부분 unique 인덱스(`uq_payments_prepare_per_order`)를 두어 DB 레벨에서도 중복 PREPARE를 차단합니다.

---

### 3. `external_payment_transactions` - 외부 연동 로그 (Raw Log)

PG사와의 모든 통신 원본을 저장하여 추적성을 확보합니다.

```sql
CREATE TABLE psp.external_payment_transactions (
    id              BIGSERIAL PRIMARY KEY,
    payment_id      VARCHAR(100) NOT NULL, -- 논리적 FK: psp.payments(id)

    -- Request/Response Details
    provider        VARCHAR(50) NOT NULL,
    url             VARCHAR(1024) NOT NULL,
    method          VARCHAR(10) NOT NULL,
    request_header  TEXT,
    request_body    JSONB,
    response_body   JSONB,
    response_status INTEGER,

    -- Metadata
    latency_ms      BIGINT,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ext_trans_payment_id ON psp.external_payment_transactions(payment_id);
CREATE INDEX idx_ext_trans_created_at ON psp.external_payment_transactions(created_at);
```

---

## 🔐 보안 및 암호화

### 암호화 대상 필드

| 테이블 | 필드 | 알고리즘 | 구현 |
|--------|------|----------|------|
| `psp.payments` | `pg_tid` | AES-256-GCM | `AesEncryptionAdapter` (저장 시 암호화, 조회 시 복호화) |
| `psp.payment_partner_meta` | `client_secret` | AES-256-GCM | 암호화 저장 (예정) |

### 암호화 구현 (Kotlin)

```kotlin
// EncryptionPort (application/port/output)
interface EncryptionPort {
    fun encrypt(plainText: String): String
    fun decrypt(cipherText: String): String
}

// AesEncryptionAdapter (infrastructure/security)
class AesEncryptionAdapter(
    private val secretKey: SecretKey
) : EncryptionPort {
    override fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12).apply { SecureRandom().nextBytes(this) }
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val cipherText = cipher.doFinal(plainText.toByteArray())
        return Base64.getEncoder().encodeToString(iv + cipherText)
    }

    override fun decrypt(cipherText: String): String {
        val decoded = Base64.getDecoder().decode(cipherText)
        val iv = decoded.sliceArray(0..11)
        val encrypted = decoded.sliceArray(12 until decoded.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        return String(cipher.doFinal(encrypted))
    }
}
```

---

## 📊 데이터 마이그레이션 (수동)

초기 개발 단계의 유연성을 위해 Flyway 대신 수동 SQL 실행 방식을 사용합니다.

```
acp-merchant/src/main/resources/db/migration/
├── init_merchant_products_orders.sql  -- 스키마/상품/주문/주문항목
├── create_checkout_sessions.sql       -- 체크아웃 세션/항목
├── add_fulfillment_option.sql         -- 배송 옵션 컬럼 추가
└── expand_products_spec.sql           -- 상품 스펙 확장 + product_images

acp-psp/src/main/resources/db/migration/
└── init_psp_payments.sql              -- 결제 파트너/결제/외부연동로그
```

---

## 🔍 쿼리 최적화 전략

- **복합 인덱스**: 자주 함께 조회되는 컬럼 조합 (예: `merchant_order_id`)
- **논리적 FK**: 물리 제약 대신 인덱스로 조인 성능 확보
- **불변 장부 조회**: `psp.payments`는 `merchant_order_id`로 최신 트랜잭션을 조회하여 상태 판정

---

## 📈 성능 메트릭 (목표)

| 메트릭 | 목표 | 측정 방법 |
|--------|------|-----------|
| 상품 피드 조회 | P95 < 100ms | Prometheus `http_server_requests_seconds` |
| 주문 생성 | P95 < 500ms | 동일 |
| 결제 준비 | P95 < 1s | 동일 |
| DB 커넥션 풀 사용률 | < 80% | `hikaricp_connections_active` |

---

## 🧪 테스트 데이터

```sql
-- 상품 100개 생성 (개발용)
INSERT INTO merchant.products (id, title, description, link, image_link, price_amount, currency, availability, inventory_qty, brand, category)
SELECT
    'prod_' || g,
    '테스트 상품 ' || g,
    '이것은 테스트 상품입니다.',
    'https://merchant.example.com/products/prod_' || g,
    'https://cdn.example.com/images/prod_' || g || '.jpg',
    (random() * 100000 + 10000)::NUMERIC(19,4),
    'KRW',
    (ARRAY['in_stock', 'out_of_stock'])[floor(random() * 2 + 1)],
    floor(random() * 100)::INTEGER,
    (ARRAY['Nike', 'Adidas', 'Apple', 'Samsung'])[floor(random() * 4 + 1)],
    (ARRAY['신발', '전자제품', '의류', '도서'])[floor(random() * 4 + 1)]
FROM generate_series(1, 100) AS g;
```

> 개발 환경에서는 `DataInitializer`가 애플리케이션 시작 시 Mock 데이터를 적재합니다.

---

## 🧭 향후 확장 설계 (미구현)

아래 항목은 설계상 검토되었으나 **현재 코드/스키마에는 반영되지 않았습니다**. 구현 시 본 문서의 위쪽 섹션으로 이동합니다.

### 미구현 테이블

- **`merchant.product_variants`**: 색상/사이즈 등 변형 상품 (TODO Phase 1.1)
- **`psp.idempotency_keys`**: 요청 단위 멱등성 키 캐시 (현재는 `payments` 조회로 대체)

### 미구현 컬럼/기능

- `products`: `stock_quantity` 분리, `sale_price_start/end_date` 분리, `shipping_weight_grams`/치수, `fulfillment_time_min/max_days`, `return_policy_url`, `deleted_at`(Soft Delete)
- `orders`/`checkout_sessions`: 금액 분해 컬럼(`items_base_amount`, `items_discount_amount`, `subtotal_amount`, `fulfillment_amount`, `tax_amount`), 상세 배송지 컬럼, `authorized_at`/`completed_at`/`canceled_at` 타임스탬프
- `payment_partner_meta`: `secret_key`, `api_base_url`, `is_production`, `updated_at` 및 초기 시드 데이터
- **인덱스/제약**: CHECK 제약, `updated_at` 자동 갱신 트리거, 부분 인덱스, 한국어 Full-Text Search(GIN, `to_tsvector` — PostgreSQL 기본 'korean' 설정 미제공으로 별도 사전 필요)
- **파티셔닝 / 읽기 복제본**: 대용량 처리를 위한 월별 파티셔닝 및 Read Replica

---

## 📚 참고 자료

- [PostgreSQL 16 Documentation](https://www.postgresql.org/docs/16/)
- [jOOQ Best Practices](https://www.jooq.org/doc/latest/manual/sql-building/best-practices/)
- [Database Indexing Strategies](https://use-the-index-luke.com/)
- [OpenAI Product Feed Spec](https://developers.openai.com/commerce/specs/feed)
