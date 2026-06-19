# 📋 프로젝트 현황 요약

## ✅ 완료된 작업

1. **문서화 및 설계 고도화** (2025-12-29)
   - ✅ ARCHITECTURE.md: 중개 서버 패턴 기반 아키텍처 설계
   - ✅ TODO.md: 9개 Phase 상세 로드맵
   - ✅ DB_SCHEMA.md: 데이터베이스 스키마 설계
   - ✅ PROJECT_PLAN.md: 12주 실행 계획서
   - ✅ README.md: 프로젝트 개요 및 빠른 시작 가이드

2. **Phase 1: Product Feed 구현 완료**
   - ✅ OpenAI Product Feed Spec 확장 필드 구현 (`ProductFeedItem`)
   - ✅ Cafe24 -> ACP 변환 로직 확장 (신규 필드 매핑)
   - ✅ Merchant DB 스키마 확장 (다중 이미지, 할인 정보 등)
   - ✅ jOOQ CodeGen 재생성 및 적용
   - ✅ **통합 테스트 완료**: `FeedIntegrationTest` (Mock Server 기반)

3. **Phase 3: PSP 서버 - 카카오페이 연동 및 테스트 완료**
   - ✅ `KakaoPayProvider` 구현 (Ready/Approve API 연동)
   - ✅ `PaymentService` 리팩토링 (실제 PG 연동 및 멱등성 처리 반영)
   - ✅ **테스트 구현 및 안정화 완료**
     - `PaymentServiceTest`: 멱등성 및 로직 검사 통과
     - `KakaoPayProviderTest`: WebClient 모킹 기반 API 연동 검사 통과
     - `PaymentIntegrationTest`: 전체 결제 준비 플로우 통합 테스트 통과 (Mock Provider 사용)
   - ✅ PSP DB 스키마 및 jOOQ 설정 최적화 완료

4. **Phase 2: Merchant 서버 - Checkout Flow 구현** (2025-12-30)
   - ✅ **Checkout Session 도메인 및 DB 구현**: `CheckoutSession`, `CheckoutItem` 구현
   - ✅ **Persistence Layer**: jOOQ 기반 `CheckoutPersistenceAdapter` 구현
   - ✅ **Pricing Engine**: `PricingEngine` 도메인 서비스 구현 (상품 가격 합산 및 Tax 계산)
   - ✅ **PSP 연동**: `PaymentClient` 구현 (Merchant -> PSP `POST /prepare` 연동)
   - ✅ **Checkout API 구현 및 고도화**: 
     - `POST /checkout_sessions`: 세션 생성
     - `GET /checkout_sessions/{id}`: 세션 조회
     - `POST /checkout_sessions/{id}`: **세션 업데이트 (수량/주소 변경 시 가격 재계산)**
     - `POST /checkout_sessions/{id}/complete`: 결제 준비 및 리다이렉트 URL 발급
   - ✅ **통합 테스트 완료**: `CheckoutIntegrationTest` (세션 생성, 수정, PSP 연동 검증)

5. **시스템 안정화 및 코드 품질 개선** (2025-12-30)
   - ✅ **DB 아키텍처 개선**:
     - **Flyway 제거**: 개발 복잡도를 줄이기 위해 Flyway 의존성 제거
     - **Foreign Key 제거**: 분산 환경 고려하여 물리적 FK 제약 제거 (애플리케이션 레벨 관리)
     - **스키마 관리**: SQL 파일명 직관적으로 변경 (`init_merchant_products_orders.sql` 등)
   - ✅ **테스트 환경 최적화**:
     - `test/resources/application.yml` 분리 및 필수 프로퍼티 설정 (`psp.base-url`, `cafe24.mall-id`)
     - Java Agent 경고 제거 (`-XX:+EnableDynamicAgentLoading`)
   - ✅ **전체 빌드 및 테스트 통과**: 모든 모듈(`acp-merchant`, `acp-psp`) 정상 빌드 확인

6. **Phase 2: Merchant 서버 - 배송 및 고도화** (2025-01-04)
   - ✅ **배송 로직 구현**: `FulfillmentOption`, `ShippingCalculator` (배송비 계산, 옵션 제공)
   - ✅ **주소 검증**: `AddressValidator` (우편번호, 국가 코드 검증)
   - ✅ **API 표준화**: Enum 대문자, Snake Case 응답, 불필요한 어노테이션 제거
   - ✅ **통합 테스트 보강**: 배송 시나리오 및 JSON 응답 형식 검증

## 🚧 다음 작업 (우선순위 순)

### 즉시 착수 (Week 2) - 진행 중 🚧

1. **Merchant & PSP - 결제 승인 및 주문 생성**
   - ✅ PSP: 결제 승인 API (`POST /payments/approve`) 보완 (merchantOrderId 연동)
   - ✅ Merchant: 결제 완료 콜백 처리 엔드포인트 구현 및 `Order` 생성 로직 고도화
   - ✅ E2E: 체크아웃 -> 결제 준비 -> 승인 -> 주문 생성 전체 플로우 검증 완료 (`CheckoutIntegrationTest`)

2. **고도화 및 보안**
   - ✅ 민감 정보(pgTid 등) 암호화 저장 로직 구현 (AES-256-GCM 기반 `EncryptionPort`)
   - ✅ PSP 결제 취소 API 구현 및 Merchant 주문 취소 연동 완료
   - [ ] Cafe24 OAuth 자동 갱신 (Redis 기반 토큰 스토리지 도입 검토)

---

## 📝 상세 작업 계획

상세한 작업 내용은 다음 문서를 참조하세요:
- **[TODO.md](TODO.md)**: 9개 Phase별 상세 작업 목록
- **[PROJECT_PLAN.md](PROJECT_PLAN.md)**: 12주 실행 계획서
- **[ARCHITECTURE.md](ARCHITECTURE.md)**: 시스템 아키텍처 설계

---

**Last Updated**: 2026-01-05T22:30:00+09:00
