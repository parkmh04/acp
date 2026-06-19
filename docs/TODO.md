# 🗺️ ACP (Agentic Commerce Protocol) 프로젝트 로드맵

> **비전**: OpenAI의 Agentic Commerce Protocol을 완벽히 구현하여 ChatGPT와 같은 AI 에이전트가 실제 상품을 검색하고, 장바구니를 구성하며, 카카오페이를 통해 안전하게 결제를 완료할 수 있는 **프로덕션 레디** 커머스 생태계를 구축합니다.

---

## 📋 프로젝트 개요

### 핵심 목표
1. **OpenAI ACP 스펙 완벽 준수**: Product Feed Spec과 Checkout Spec을 100% 구현
2. **카카오페이 통합**: 실제 테스트 결제가 가능한 PSP 서버 구축
3. **프로덕션 품질**: SRE 원칙, 보안, 관측성, 복원력을 갖춘 엔터프라이즈급 시스템
4. **실제 데모 가능**: ChatGPT Custom GPT 또는 에이전트 시뮬레이터를 통한 E2E 데모

### 아키텍처 원칙
- **물리적 분리**: Merchant(8080)와 PSP(8081) 서버 독립 운영
- **헥사고날 아키텍처**: 도메인 순수성 유지, 포트/어댑터 패턴
- **Type-Safe**: jOOQ 기반 컴파일 타임 SQL 검증
- **Non-Blocking**: Kotlin Coroutines + Virtual Threads
- **관측 가능성**: 구조화된 로깅, 메트릭, 분산 트레이싱

---

## 🏗️ Phase 0: 기반 인프라 및 아키텍처 (완료 ✅)

### 완료된 작업
- [x] **멀티 모듈 구조**: `acp-merchant`, `acp-psp`, `acp-shared`, `acp-client` 분리
- [x] **헥사고날 아키텍처**: 도메인, 포트, 어댑터 계층 설계
- [x] **기술 스택 확정**: 
  - JDK 21, Kotlin 2.1, Spring Boot 3.5.3
  - PostgreSQL 16, jOOQ (Type-Safe SQL)
  - Kotlin Coroutines, Virtual Threads
- [x] **인프라**: Docker Compose 기반 PostgreSQL 환경
- [x] **문서화**: 아키텍처 다이어그램, DB 스키마, 시퀀스 다이어그램

### 기술 부채
- [ ] **jOOQ CodeGen 자동화**: Gradle 태스크 최적화 및 CI 통합
- [ ] **환경 변수 관리**: `.env` 파일 검증 및 안전한 시크릿 관리

---

## 🛍️ Phase 1: Merchant 서버 - Product Feed (우선순위: 높음)

> **목표**: OpenAI Product Feed Spec을 완벽히 준수하는 상품 피드 API 구현

### 1.1 Product Feed Spec 완벽 구현

#### 현재 상태
- [x] 기본 `/feed` 엔드포인트 구현
- [x] `ProductFeedItem` 모델 확장 (OpenAI Spec 준수)
- [x] DB 스키마 확장 (신규 필드 반영)
- [x] jOOQ CodeGen 재실행

#### 필수 작업
- [x] **Product Feed Spec 전체 필드 구현**
  - **Required Fields**:
    - [x] `id`, `title`, `description`, `link`, `image_link`
    - [x] `price`
    - [x] `availability` (in_stock, out_of_stock, preorder)
    - [x] `currency`
  - **Recommended Fields**:
    - [x] `additional_image_links`
    - [x] `gtin`
    - [x] `mpn`
    - [x] `sale_price`
    - [x] `merchant_name`, `merchant_url`
    - [x] `shipping_weight`
    - [x] `return_policy_days`
  - **Optional Fields**:
    - [ ] `variants` (색상, 사이즈 등 변형 상품)
    - [x] `reviews_average_rating`, `reviews_count`
    - [ ] `fulfillment_time_min_days`, `fulfillment_time_max_days`
    - [ ] `geo_targeting` (지역 타겟팅)

- [x] **데이터베이스 스키마 확장**
  - [x] `products` 테이블에 모든 필드 추가
  - [ ] `product_variants` 테이블 생성 (변형 상품)
  - [x] `product_images` 테이블 생성 (다중 이미지)
  - [x] jOOQ CodeGen 재실행

- [ ] **피드 포맷 지원**
  - [ ] JSON Lines (`.jsonl.gz`) 포맷 지원
  - [ ] CSV (`.csv.gz`) 포맷 지원
  - [ ] HTTPS 전용, GZIP 압축 응답

### 1.2 상품 소싱 전략

#### 옵션 A: Mock 데이터 (초기 개발용)
- [x] 기본 Mock 데이터 생성 (`DataInitializer`)
- [ ] **다양한 카테고리 Mock 데이터 확장**
  - [ ] 전자제품, 패션, 식품, 도서 등 10개 이상 카테고리
  - [ ] 각 카테고리당 최소 50개 상품
  - [ ] 변형 상품 (색상, 사이즈) 포함
  - [ ] 리뷰, 평점 데이터 포함

#### 옵션 B: Cafe24 Open API 연동 (프로덕션용) ✅
- [x] **Cafe24 API 클라이언트 구현** (`Cafe24ProductAdapter`)
- [x] **Cafe24 데이터 변환기 구현** (`Cafe24ToAcpConverter`)
  - [x] 모든 신규 스펙 필드 매핑 로직 추가
- [x] **상품 피드 유즈케이스 구현** (`ProductFeedService`)
- [x] **헥사고날 아키텍처 리팩토링 완료** (Merchant & PSP)
- [ ] **OAuth 2.0 인증 자동 갱신** (Refresh Token 활용)
- [ ] **이미지 URL 변환 및 CDN 최적화**

### 1.3 피드 성능 및 신뢰성
- [ ] **캐싱 전략**
  - [ ] Redis 기반 피드 캐시 (TTL 5분)
  - [ ] ETag 기반 조건부 응답 (304 Not Modified)
- [ ] **Rate Limiting**
  - [ ] IP 기반 요청 제한 (분당 100회)
  - [ ] API Key 기반 쿼터 관리
- [ ] **모니터링**
  - [ ] 피드 생성 시간 메트릭
  - [ ] 피드 크기 및 상품 수 추적
  - [ ] 에러율 모니터링

---

## 🛒 Phase 2: Merchant 서버 - Checkout Flow (우선순위: 높음)

> **목표**: OpenAI Checkout Spec을 완벽히 구현하여 ChatGPT에서 주문 생성 및 결제 가능

### 2.1 Checkout Session 생명주기 구현

#### 필수 엔드포인트
- [x] **POST /checkout_sessions** (세션 생성)
  - [x] 요청 검증 (items, buyer, fulfillment_address)
  - [x] 재고 확인(상품 존재) 및 가격 계산
  - [x] 세금 계산 (VAT 10%, PricingEngine) — GET 재조회 시에도 합계 역산으로 일관 복원
  - [x] 배송비 계산 (fulfillment_options)
  - [x] 세션 ID 생성 및 DB 저장
  - [x] 응답: `CheckoutSessionResponse` (status: not_ready_for_payment)

- [x] **POST /checkout_sessions/{id}** (세션 업데이트)
  - [ ] 수량 변경, 주소 변경, 할인 코드 적용 — 수량/주소 O, 할인 미구현
  - [ ] 실시간 가격/세금/배송비 재계산 — 가격/배송 O, 세금 미구현
  - [ ] 멱등성 보장 (Idempotency-Key 헤더)
  - [x] 응답: 업데이트된 `CheckoutSessionResponse`

- [x] **POST /checkout_sessions/{id}/complete** (주문 확정)
  - [x] PSP 서버에 결제 요청 (`POST /api/v1/payments/prepare`)
  - [x] 주문 생성 (orders 테이블) — /confirm 단계에서 생성
  - [x] 재고 차감 (트랜잭션, 원자적 차감·재고 부족 시 롤백) — confirm 단계
  - [x] 응답: `next_action_url` (카카오페이 리다이렉트 URL)

- [x] **POST /checkout_sessions/{id}/cancel** (세션 취소)
  - [x] 세션 상태를 CANCELED로 변경
  - [ ] 재고 복구 (필요 시)

- [x] **POST /checkout_sessions/{id}/confirm** (결제 승인·주문 확정)
  - [x] pg_token으로 PSP 승인 호출 및 주문 생성

- [x] **GET /checkout_sessions/{id}** (세션 조회)
  - [x] 현재 세션 상태 반환
  - [ ] 캐싱 (Redis, TTL 1분)

### 2.2 주문 상태 관리

#### 주문 상태 머신
```
PENDING → AUTHORIZED → COMPLETED
   ↓           ↓
CANCELED    FAILED
```

- [ ] **상태 전이 로직 구현**
  - [x] `PENDING`: 체크아웃 세션 생성 시
  - [x] `AUTHORIZED`: PSP에서 결제 승인 완료 시
  - [ ] `COMPLETED`: 상품 발송 완료 시 (발송 로직 없음)
  - [x] `CANCELED`: 사용자 취소
  - [ ] `FAILED`: 결제 실패 또는 재고 부족

- [ ] **상태 전이 검증**
  - [ ] 불가능한 전이 차단 (예: COMPLETED → PENDING)
  - [ ] 동시성 제어 (Optimistic Locking)

### 2.3 가격 계산 엔진

- [ ] **Line Item 계산**
  - [x] `base_amount = unit_price × quantity`
  - [ ] `discount` 적용 (할인 코드, 프로모션) — 미구현
  - [x] `subtotal = base_amount - discount`
  - [x] `tax` 계산 (VAT 10%, PricingEngine)
  - [x] `total = subtotal + tax`

- [ ] **Totals 계산**
  - [x] `items_base_amount`: 모든 상품 기본 금액 합계
  - [ ] `items_discount`: 상품 할인 합계 — 미구현
  - [x] `subtotal`: 상품 소계
  - [x] `fulfillment`: 배송비
  - [x] `tax`: 세금 (VAT 10%)
  - [x] `total`: 최종 결제 금액

- [ ] **세금 계산 로직**
  - [x] 한국: VAT 10% (PricingEngine)
  - [ ] 미국: 주별 Sales Tax (외부 API 연동 고려)
  - [ ] 유럽: VAT (국가별)

### 2.4 Fulfillment Options (배송 옵션)

- [x] **배송 방법 정의** (FulfillmentOption.standard/express/sameDay)
  - [x] 일반 배송 (3-5일, 무료 또는 3,000원)
  - [x] 빠른 배송 (1-2일, 5,000원)
  - [x] 당일 배송 (지역 제한, 10,000원)

- [x] **배송비 계산**
  - [x] 주소 기반 배송 가능 여부 확인
  - [x] 무게/부피 기반 배송비 계산 (기본 구현)
  - [x] 무료 배송 조건 (예: 50,000원 이상)

### 2.5 PSP 연동

- [ ] **WebClient 구성**
  - [x] PSP 서버 URL 설정 (`http://localhost:8081`)
  - [ ] Timeout 설정 (Connect: 3s, Read: 10s)
  - [ ] Retry 정책 (최대 3회, Exponential Backoff)
  - [ ] Circuit Breaker (Resilience4j)

- [x] **결제 준비 요청**
  - [x] `POST /api/v1/payments/prepare` 호출
  - [x] 요청: `PaymentPrepareRequest` (merchantOrderId, amount, items)
  - [x] 응답: `PaymentPrepareResponse` (paymentId, redirectUrl)

- [ ] **결제 상태 조회**
  - [ ] `GET /api/v1/payments/{id}` 호출 — 미구현
  - [ ] 주기적 폴링 (결제 완료 확인)

### 2.6 보안 및 검증

- [ ] **인증/인가**
  - [ ] API Key 기반 인증 (Authorization: Bearer)
  - [ ] 요청 서명 검증 (HMAC-SHA256)

- [ ] **입력 검증**
  - [x] 상품 ID 존재 여부 확인
  - [ ] 수량 범위 검증 (1-99)
  - [x] 주소 형식 검증 (우편번호, 국가 코드)
  - [ ] 이메일 형식 검증

- [ ] **멱등성 보장**
  - [ ] `Idempotency-Key` 헤더 처리
  - [ ] 중복 요청 감지 (Redis 캐시, TTL 24시간)
  - [ ] 동일 키로 재요청 시 기존 응답 반환

- [ ] **Rate Limiting**
  - [ ] IP 기반 제한 (분당 60회)
  - [ ] 세션 기반 제한 (초당 10회)

### 2.7 에러 처리

- [x] **표준 에러 응답 (RFC 7807)** — GlobalExceptionHandler(ProblemDetail) 적용
  ```json
  {
    "type": "https://merchant.example.com/errors/out-of-stock",
    "title": "Out of Stock",
    "status": 400,
    "detail": "Item 'item_123' is out of stock",
    "instance": "/checkout_sessions/cs_abc123"
  }
  ```

- [ ] **에러 코드 정의**
  - [ ] `out_of_stock`: 재고 부족
  - [ ] `invalid_address`: 배송 불가 주소
  - [ ] `payment_failed`: 결제 실패
  - [ ] `session_expired`: 세션 만료 (30분)

---

## 💳 Phase 3: PSP 서버 - 카카오페이 통합 (우선순위: 높음)

> **목표**: 카카오페이 단건 결제 API를 완벽히 래핑하여 실제 테스트 결제 가능

### 3.1 카카오페이 API 클라이언트 구현

#### 인증 설정
- [ ] **환경 변수 관리**
  - [x] `.env.template` 생성
  - [ ] `.env` 파일 검증 (앱 시작 시)
  - [ ] 시크릿 암호화 (Jasypt 또는 AWS Secrets Manager)

- [x] **API 클라이언트 구성**
  - [x] Base URL: `https://open-api.kakaopay.com` (단건결제 `/online/v1/payment/*` 엔드포인트)
  - [x] Authorization 헤더: `SECRET_KEY {secret_key}`
  - [x] Content-Type: `application/json`

#### 필수 API 구현

- [x] **결제 준비 (Ready)**
  - [x] 엔드포인트: `POST /online/v1/payment/ready`
  - [x] 요청 파라미터 매핑:
    - [x] `cid`, `partner_order_id`, `partner_user_id`
    - [x] `item_name` (다중 상품 처리 포함)
    - [x] `quantity`, `total_amount`, `tax_free_amount`
    - [x] `approval_url`, `cancel_url`, `fail_url`
  - [x] 응답 처리:
    - `tid`: 카카오페이 트랜잭션 ID
    - `next_redirect_pc_url`: PC 웹 결제 URL
    - `next_redirect_mobile_url`: 모바일 웹 결제 URL
    - `next_redirect_app_url`: 앱 결제 URL
  - [x] DB 저장: `payments` 테이블 (type: PREPARE)

- [x] **결제 승인 (Approve)**
  - [x] 엔드포인트: `POST /online/v1/payment/approve`
  - [x] 요청 파라미터:
    - `cid`: 가맹점 코드
    - `tid`: 카카오페이 트랜잭션 ID
    - `partner_order_id`: Merchant 주문 ID
    - `partner_user_id`: 사용자 ID
    - `pg_token`: 결제 승인 토큰 (리다이렉트 쿼리 파라미터)
  - [x] 응답 처리:
    - `aid`: 결제 승인 ID
    - `approved_at`: 승인 시각
    - `amount`: 결제 금액 정보
    - `card_info`: 카드 정보 (마스킹)
  - [x] DB 저장: `payments` 테이블 (type: APPROVE, status: SUCCESS)

- [ ] **결제 조회 (Order)**
  - [x] 엔드포인트: `POST /online/v1/payment/order` — KakaoPayProvider.checkStatus (망취소 시 사용)
  - [x] 요청 파라미터: `cid`, `tid`
  - [x] 응답: 결제 상세 정보
  - [ ] 용도: 주기적 상태 동기화 (스케줄러 미구현)

- [x] **결제 취소 (Cancel)**
  - [x] 엔드포인트: `POST /online/v1/payment/cancel`
  - [x] 요청 파라미터:
    - `cid`, `tid`
    - `cancel_amount`: 취소 금액
    - `cancel_tax_free_amount`: 취소 비과세 금액
  - [x] 부분 취소 지원 (cancel_amount)
  - [x] DB 저장: `payments` 테이블 (type: CANCEL)

### 3.2 결제 상태 머신

```
READY → IN_PROGRESS → COMPLETED
  ↓          ↓            ↓
FAILED   FAILED      CANCELED
```

- [ ] **상태 전이 구현** (불변 장부 type/status 기반)
  - [x] `READY`: 결제 준비 완료 (PREPARE/READY)
  - [ ] `IN_PROGRESS`: 사용자가 결제 URL 접속 (별도 추적 안 함)
  - [x] `COMPLETED`: 결제 승인 완료 (APPROVE/SUCCESS)
  - [x] `FAILED`: 결제 실패 (APPROVE/FAIL)
  - [x] `CANCELED`: 사용자 취소 또는 환불 (CANCEL)

- [ ] **타임아웃 처리**
  - [ ] 결제 준비 후 15분 이내 미승인 시 자동 만료
  - [ ] 스케줄러로 주기적 체크 (1분마다)

### 3.3 콜백 처리

> 콜백은 PSP가 아닌 **Merchant**(`PaymentCallbackController`, :8080)가 수신합니다.

- [x] **성공 콜백**
  - [x] `GET /api/v1/payments/success?session_id={id}&pg_token={token}`
  - [x] `pg_token` 추출 및 PSP `/approve` 호출
  - [x] `/api/v1/payments/completed`로 리다이렉트 (성공 페이지)

- [x] **취소 콜백**
  - [x] `GET /api/v1/payments/cancel?session_id={id}`
  - [x] 사용자에게 취소 페이지 표시

- [x] **실패 콜백**
  - [x] `GET /api/v1/payments/fail?session_id={id}`
  - [x] 사용자에게 실패 페이지 표시

### 3.4 멱등성 및 동시성 제어

- [x] **멱등성 보장**
  - [x] `merchant_order_id`를 Unique Key로 사용 (PREPARE)
  - [x] 동일 주문 ID로 재요청 시 기존 결제 정보 반환
  - [x] DB Unique Constraint 설정 (uq_payments_prepare_per_order)

- [ ] **동시성 제어**
  - [ ] Optimistic Locking (version 컬럼)
  - [ ] 분산 락 (Redis SETNX) — Merchant 측 confirm은 Redisson 락 사용, PSP는 DB 제약으로 대체

### 3.5 보안

- [ ] **민감 정보 보호**
  - [x] `tid` 암호화 저장 (AES-256-GCM, AesEncryptionAdapter)
  - [ ] 카드 정보 로깅 금지
  - [ ] PII 마스킹 (로그에서 이메일, 전화번호)

- [ ] **요청 검증**
  - [ ] 금액 범위 검증 (100원 ~ 10,000,000원)
  - [ ] Merchant 서버 IP 화이트리스트

### 3.6 에러 처리

- [ ] **카카오페이 에러 코드 매핑**
  - [ ] `-100`: 잘못된 파라미터 → 400 Bad Request
  - [ ] `-777`: 결제 승인 실패 → 402 Payment Required
  - [ ] `-9999`: 시스템 에러 → 503 Service Unavailable

- [ ] **재시도 전략**
  - [ ] 네트워크 오류: 최대 3회 재시도 (Exponential Backoff)
  - [ ] 타임아웃: 10초 후 재시도
  - [ ] 멱등성 보장 (동일 요청 중복 방지)

---

## 🤖 Phase 4: 에이전트 시뮬레이터 및 실제 연동 (우선순위: 중간)

> **목표**: ChatGPT 또는 커스텀 에이전트를 통해 E2E 구매 플로우 데모

### 4.1 에이전트 시뮬레이터 (Kotlin Compose Desktop)

> ✅ **CLI 시뮬레이터 구현됨** (`acp-client`): 상품 검색 → 체크아웃 세션 생성 (`./gradlew :acp-client:run --args="demo"`). 아래 Compose GUI는 예정.

- [x] **CLI 구현** (feed/demo/get, Ktor 기반 AcpApiClient)
- [ ] **UI 구현** (Compose Desktop)
  - [ ] 채팅 인터페이스 (ChatGPT 스타일)
  - [ ] 상품 검색 및 표시
  - [ ] 장바구니 관리
  - [ ] 체크아웃 플로우 시각화

- [ ] **프로토콜 디버거**
  - [ ] 원시 JSON 페이로드 표시
  - [ ] 요청/응답 타임라인
  - [ ] 에러 메시지 하이라이트

- [ ] **시나리오 테스트**
  - [ ] "나이키 운동화 구매해줘" → 상품 검색 → 체크아웃 → 결제
  - [ ] 재고 부족 시나리오
  - [ ] 결제 실패 시나리오

### 4.2 ChatGPT Custom GPT 연동

- [ ] **외부 노출**
  - [ ] ngrok 또는 Cloudflare Tunnel로 로컬 8080 포트 노출
  - [ ] HTTPS 인증서 설정
  - [ ] CORS 설정

- [ ] **OpenAI Actions 설정**
  - [ ] `openapi.yaml` 작성 (Product Feed, Checkout API)
  - [ ] Custom GPT 생성 및 Actions 등록
  - [ ] 인증 설정 (API Key)

- [ ] **테스트**
  - [ ] ChatGPT에서 "상품 추천해줘" 입력
  - [ ] 실제 결제까지 완료

### 4.3 Webhook 구현 (Merchant → OpenAI)

- [ ] **주문 이벤트 발행**
  - [ ] `order.created`: 주문 생성 시
  - [ ] `order.updated`: 주문 상태 변경 시
  - [ ] `order.completed`: 배송 완료 시
  - [ ] `order.canceled`: 주문 취소 시

- [ ] **Webhook 전송**
  - [ ] OpenAI 제공 Webhook URL로 POST 요청
  - [ ] 서명 생성 (HMAC-SHA256)
  - [ ] 재시도 로직 (최대 5회, Exponential Backoff)

---

## 📊 Phase 5: 관측성 및 SRE (우선순위: 높음)

> **목표**: 프로덕션 환경에서 시스템을 모니터링하고 문제를 빠르게 감지/해결

### 5.1 메트릭 (Micrometer + Prometheus)

- [ ] **비즈니스 메트릭**
  - [ ] `checkout_sessions_created_total`: 체크아웃 세션 생성 수
  - [ ] `orders_completed_total`: 완료된 주문 수
  - [ ] `payment_success_rate`: 결제 성공률 (%)
  - [ ] `revenue_total`: 총 매출 (KRW)
  - [ ] `average_order_value`: 평균 주문 금액

- [ ] **시스템 메트릭** (Micrometer 자동 계측)
  - [x] `http_server_requests_seconds`: API 응답 시간 (P50, P95, P99)
  - [x] `jvm_threads_live`: 스레드 수
  - [x] `jvm_memory_used_bytes`: 메모리 사용량
  - [x] `jdbc_connections_active`: DB 커넥션 풀 사용량 (HikariCP)
  - [ ] `redis_commands_duration_seconds`: Redis 명령 실행 시간 (Redisson 미계측)

- [ ] **Prometheus 설정**
  - [x] `/actuator/prometheus` 엔드포인트 노출
  - [ ] Prometheus 서버 설정 (docker-compose)
  - [ ] Scrape 간격: 15초

### 5.2 로깅 (Structured Logging)

- [ ] **Logback 설정**
  - [ ] JSON 포맷 로깅 (Logstash Encoder)
  - [ ] 로그 레벨: INFO (프로덕션), DEBUG (개발)
  - [ ] 파일 로테이션 (일별, 최대 30일 보관)

- [ ] **구조화된 로그 필드**
  - [ ] `timestamp`: ISO 8601 형식
  - [ ] `level`: INFO, WARN, ERROR
  - [ ] `logger`: 클래스명
  - [ ] `message`: 로그 메시지
  - [ ] `trace_id`: 분산 트레이싱 ID
  - [ ] `user_id`, `order_id`, `payment_id`: 컨텍스트 정보

- [ ] **민감 정보 마스킹**
  - [ ] 이메일: `user@example.com` → `u***@example.com`
  - [ ] 전화번호: `010-1234-5678` → `010-****-5678`
  - [ ] 카드 번호: `1234-5678-9012-3456` → `****-****-****-3456`

### 5.3 분산 트레이싱 (OpenTelemetry)

- [ ] **OpenTelemetry 설정**
  - [ ] Spring Boot Starter 추가
  - [ ] Jaeger 또는 Zipkin 백엔드 설정

- [ ] **Trace 전파**
  - [ ] Agent → Merchant → PSP → KakaoPay
  - [ ] HTTP 헤더: `traceparent`, `tracestate`
  - [ ] Span 생성: 각 API 호출, DB 쿼리, Redis 명령

- [ ] **Span 속성**
  - [ ] `http.method`, `http.url`, `http.status_code`
  - [ ] `db.system`, `db.statement`
  - [ ] `order.id`, `payment.id`

### 5.4 알림 (Alerting)

- [ ] **Prometheus Alertmanager 설정**
  - [ ] 결제 성공률 < 95% → Slack 알림
  - [ ] API 응답 시간 P95 > 1초 → PagerDuty
  - [ ] 에러율 > 1% → 이메일 알림

- [x] **헬스 체크**
  - [x] `/actuator/health` 엔드포인트
  - [x] DB 연결 상태, Redis 연결 상태 체크 (Actuator HealthIndicator 자동)
  - [x] Kubernetes Liveness/Readiness Probe 설정 (`/actuator/health/{liveness,readiness}`)

### 5.5 대시보드 (Grafana)

- [ ] **Grafana 설정**
  - [ ] Prometheus 데이터 소스 추가
  - [ ] 대시보드 생성:
    - **비즈니스 대시보드**: 주문 수, 매출, 전환율
    - **시스템 대시보드**: CPU, 메모리, 응답 시간
    - **에러 대시보드**: 에러율, 에러 유형별 분포

---

## 🔒 Phase 6: 보안 및 컴플라이언스 (우선순위: 높음)

> **목표**: 프로덕션 환경에서 안전하게 운영 가능한 보안 수준 확보

### 6.1 데이터 암호화

- [ ] **전송 중 암호화**
  - [ ] HTTPS 전용 (TLS 1.3)
  - [ ] HSTS 헤더 설정
  - [ ] 인증서 자동 갱신 (Let's Encrypt)

- [ ] **저장 시 암호화**
  - [ ] 민감 필드 암호화 (AES-256-GCM)
    - `pg_token`, `tid`, `card_info`
  - [ ] 암호화 키 관리 (AWS KMS 또는 HashiCorp Vault)
  - [ ] 키 로테이션 (90일마다)

### 6.2 인증 및 인가

- [ ] **API Key 관리**
  - [ ] API Key 생성 및 발급
  - [ ] Key 저장 (해시 + Salt, bcrypt)
  - [ ] Key 만료 정책 (1년)

- [ ] **요청 서명 검증**
  - [ ] HMAC-SHA256 서명 생성
  - [ ] Timestamp 검증 (5분 이내)
  - [ ] Replay Attack 방지 (Nonce)

### 6.3 입력 검증 및 SQL Injection 방지

- [ ] **입력 검증**
  - [ ] Bean Validation (`@Valid`, `@NotNull`, `@Size`)
  - [ ] 커스텀 Validator (이메일, 전화번호, 우편번호)

- [x] **SQL Injection 방지**
  - [x] jOOQ Parameterized Query 사용
  - [x] Native SQL 금지

### 6.4 OWASP Top 10 대응

- [ ] **A01: Broken Access Control**
  - [ ] 사용자별 주문 접근 제어 (user_id 검증)

- [ ] **A02: Cryptographic Failures**
  - [ ] 민감 데이터 암호화 (위 참조)

- [ ] **A03: Injection**
  - [x] SQL Injection 방지 (jOOQ parameterized)
  - [ ] XSS 방지 (Content-Security-Policy 헤더)

- [ ] **A05: Security Misconfiguration**
  - [ ] 불필요한 엔드포인트 비활성화 (`/actuator/*` 인증 필요)
  - [ ] 에러 메시지에 스택 트레이스 노출 금지

- [ ] **A07: Identification and Authentication Failures**
  - [ ] API Key 인증 (위 참조)

- [ ] **A09: Security Logging and Monitoring Failures**
  - [ ] 보안 이벤트 로깅 (로그인 실패, 권한 오류)

### 6.5 컴플라이언스

- [ ] **PCI DSS (카드 정보 보호)**
  - [ ] 카드 정보 직접 저장 금지 (카카오페이가 처리)
  - [ ] 로그에 카드 정보 노출 금지

- [ ] **GDPR (개인정보 보호)**
  - [ ] 사용자 동의 관리
  - [ ] 개인정보 삭제 요청 처리 (Right to be Forgotten)
  - [ ] 데이터 이동권 (Data Portability)

---

## 🧪 Phase 7: 테스트 전략 (우선순위: 높음)

> **목표**: 높은 테스트 커버리지로 안정성 확보

### 7.1 단위 테스트 (Unit Test)

- [ ] **도메인 로직 테스트**
  - [ ] 가격 계산 엔진 (Line Item, Totals)
  - [ ] 상태 머신 전이 (주문, 결제)
  - [ ] 세금 계산 로직

- [ ] **테스트 프레임워크**
  - [x] JUnit 5, Mockk
  - [ ] 커버리지 목표: 80% 이상 (JaCoCo) — 미구성

### 7.2 통합 테스트 (Integration Test)

- [x] **API 테스트** (FeedIntegrationTest, CheckoutIntegrationTest, PaymentIntegrationTest)
  - [x] `@SpringBootTest` + `WebTestClient`
  - [ ] 모든 엔드포인트 테스트 (일부 커버)
  - [x] 성공/실패 시나리오

- [x] **DB 테스트**
  - [x] Testcontainers (PostgreSQL)
  - [ ] 트랜잭션 롤백 (@Transactional)

- [ ] **외부 API Mock**
  - [x] 카카오페이 API Mock (mockk 기반, KakaoPayProviderTest)
  - [ ] Cafe24 API Mock

### 7.3 E2E 테스트

- [ ] **시나리오 테스트**
  - [ ] 상품 검색 → 장바구니 → 체크아웃 → 결제 → 주문 완료
  - [ ] 결제 실패 → 재시도 → 성공
  - [ ] 주문 취소 → 환불

- [ ] **성능 테스트**
  - [ ] Gatling 또는 K6
  - [ ] 목표: 1000 TPS, P95 < 500ms

### 7.4 보안 테스트

- [ ] **OWASP ZAP**
  - [ ] SQL Injection, XSS 스캔
  - [ ] 취약점 리포트 생성

---

## 🚀 Phase 8: CI/CD 및 배포 (우선순위: 중간)

> **목표**: 자동화된 빌드, 테스트, 배포 파이프라인 구축

### 8.1 CI (Continuous Integration)

- [x] **GitHub Actions 워크플로우** (`.github/workflows/ci.yml`)
  - [x] push/PR 시 자동 빌드 및 테스트 (docker compose로 스키마 자동 생성 후 `gradlew build`)
  - [ ] 코드 커버리지 리포트 (Codecov)
  - [ ] 정적 분석 (Detekt, ktlint)

- [ ] **빌드 최적화**
  - [ ] Gradle Build Cache
  - [ ] Docker Layer Caching

### 8.2 CD (Continuous Deployment)

- [ ] **Docker 이미지 빌드**
  - [ ] Multi-stage Dockerfile
  - [ ] 이미지 크기 최적화 (< 200MB)
  - [ ] Docker Hub 또는 ECR에 푸시

- [ ] **배포 전략**
  - [ ] Blue-Green Deployment
  - [ ] Canary Deployment (10% → 50% → 100%)

### 8.3 인프라 (Kubernetes)

- [ ] **Kubernetes 매니페스트**
  - [ ] Deployment, Service, Ingress
  - [ ] ConfigMap (환경 변수)
  - [ ] Secret (API Key, DB 비밀번호)

- [ ] **Auto Scaling**
  - [ ] HPA (Horizontal Pod Autoscaler)
  - [ ] 목표: CPU 70%, 메모리 80%

---

## 📚 Phase 9: 문서화 (우선순위: 중간)

> **목표**: 개발자와 운영자를 위한 완벽한 문서 제공

### 9.1 API 문서

- [ ] **Swagger/OpenAPI**
  - [ ] Springdoc OpenAPI 설정
  - [ ] `/swagger-ui.html` 엔드포인트
  - [ ] 모든 API 설명, 예제 추가

### 9.2 아키텍처 문서

- [ ] **C4 Model 다이어그램**
  - [ ] Context, Container, Component, Code
  - [ ] Mermaid 또는 PlantUML

- [ ] **ADR (Architecture Decision Records)**
  - [ ] 주요 기술 선택 이유 문서화
  - [ ] 예: "왜 jOOQ를 선택했는가?"

### 9.3 운영 문서

- [ ] **Runbook**
  - [ ] 배포 절차
  - [ ] 장애 대응 절차
  - [ ] 롤백 절차

- [ ] **FAQ**
  - [ ] 자주 발생하는 에러 및 해결 방법

---

## 🎯 우선순위 요약

### P0 (즉시 착수)
1. **Merchant: Checkout Flow 구현** (Phase 2)
2. **PSP: 카카오페이 통합** (Phase 3)
3. **보안: 암호화 및 인증** (Phase 6)
4. **테스트: 통합 테스트** (Phase 7)

### P1 (다음 단계)
1. **Merchant: Product Feed 확장** (Phase 1)
2. **관측성: 메트릭 및 로깅** (Phase 5)
3. **에이전트 시뮬레이터** (Phase 4)

### P2 (장기 목표)
1. **CI/CD 파이프라인** (Phase 8)
2. **문서화** (Phase 9)
3. **Cafe24 연동** (Phase 1.2)

---

## 📅 마일스톤

### Milestone 1: MVP (4주) — 🚧 진행 중
- [x] Merchant: 기본 Checkout Flow
- [x] PSP: 카카오페이 결제 준비/승인 (코드 구현, 실결제 미검증)
- [ ] 에이전트 시뮬레이터로 E2E 테스트 (acp-client 스캐폴드)

### Milestone 2: 프로덕션 준비 (8주) — 🚧 일부
- [ ] 전체 ACP 스펙 구현 (세금/할인/포맷 미구현)
- [ ] 보안 강화 (tid 암호화만, 인증/rate limit 미구현)
- [ ] 관측성 구축 (Actuator/Prometheus 노출 O, 로깅/트레이싱/Grafana 미구현)
- [ ] 통합 테스트 커버리지 80% (JaCoCo 미구성)

### Milestone 3: ChatGPT 연동 (12주) — 📅 미착수
- [ ] Custom GPT 연동
- [ ] 실제 결제 데모
- [ ] 성능 최적화 (1000 TPS)

---

## 🔗 참고 자료

- [OpenAI Agentic Commerce Protocol](https://developers.openai.com/commerce/guides/get-started)
- [OpenAI Checkout Spec](https://developers.openai.com/commerce/specs/checkout)
- [OpenAI Product Feed Spec](https://developers.openai.com/commerce/specs/feed)
- [카카오페이 개발자센터](https://developers.kakaopay.com/docs/payment/online/common)
- [카카오페이 단건 결제](https://developers.kakaopay.com/docs/payment/online/single-payment)
- [Spring Boot 3.5.3 문서](https://docs.spring.io/spring-boot/docs/3.5.3/reference/html/)
- [jOOQ 문서](https://www.jooq.org/doc/latest/manual/)
- [Kotlin Coroutines 가이드](https://kotlinlang.org/docs/coroutines-guide.html)