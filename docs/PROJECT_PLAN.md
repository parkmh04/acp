# 📅 ACP 프로젝트 실행 계획서

> **프로젝트 기간**: 12주 (3개월)  
> **목표**: OpenAI Agentic Commerce Protocol을 완벽히 구현하고 카카오페이 통합을 통해 실제 결제가 가능한 프로덕션 레디 시스템 구축

---

## 🎯 프로젝트 목표 및 성공 지표

### 핵심 목표

1. **ACP 스펙 100% 준수**
   - Product Feed Spec 완벽 구현
   - Checkout Spec 완벽 구현
   - OpenAI 인증 테스트 통과

2. **실제 결제 데모 가능**
   - 카카오페이 테스트 환경에서 실제 결제 완료
   - ChatGPT Custom GPT 또는 에이전트 시뮬레이터를 통한 E2E 데모

3. **프로덕션 품질 확보**
   - 테스트 커버리지 80% 이상
   - API 응답 시간 P95 < 500ms
   - 결제 성공률 > 95%
   - 보안 취약점 0건 (OWASP ZAP 스캔)

### 성공 지표 (KPI)

| 지표 | 목표 | 측정 방법 |
|------|------|-----------|
| ACP 스펙 준수율 | 100% | OpenAI 체크리스트 |
| 테스트 커버리지 | > 80% | JaCoCo |
| API 응답 시간 (P95) | < 500ms | Prometheus |
| 결제 성공률 | > 95% | 메트릭 대시보드 |
| 보안 취약점 | 0건 | OWASP ZAP |
| 문서화 완성도 | 100% | 리뷰 체크리스트 |

---

## 📆 마일스톤 및 일정

### Milestone 1: MVP (Week 1-4)

**목표**: 기본 체크아웃 플로우와 카카오페이 결제 완료

#### Week 1: 기반 구축 및 설계 검증
- [ ] **Day 1-2**: 개발 환경 설정
  - Docker Compose 실행 및 DB 초기화
  - jOOQ CodeGen 설정 및 실행
  - 환경 변수 설정 (.env 파일)
  
- [ ] **Day 3-4**: DB 스키마 구현
  - 수동 SQL 마이그레이션 스크립트 작성 (`db/migration/`, Flyway 미사용으로 결정)
  - `products`, `orders`, `payments` 테이블 생성
  - Mock 데이터 삽입 (상품 100개)
  
- [ ] **Day 5**: 헥사고날 아키텍처 뼈대 구축
  - Domain 모델 정의 (Order, Product, Payment)
  - Port 인터페이스 정의
  - Adapter 뼈대 생성

#### Week 2: Merchant 서버 - 기본 Checkout
- [ ] **Day 1-2**: Product Feed API 구현
  - `GET /feed` 엔드포인트
  - jOOQ 기반 Repository
  - Redis 캐싱 (TTL 5분)
  
- [ ] **Day 3-4**: Checkout Session API (기본)
  - `POST /checkout_sessions` (세션 생성)
  - 가격 계산 엔진 (PricingEngine)
  - 세금 계산 로직 (한국 VAT 10%)
  
- [ ] **Day 5**: 통합 테스트 작성
  - `FeedIntegrationTest`
  - `CheckoutIntegrationTest`
  - Testcontainers 설정

#### Week 3: PSP 서버 - 카카오페이 통합
- [ ] **Day 1-2**: 카카오페이 클라이언트 구현
  - `POST /online/v1/payment/ready` 연동
  - WebClient 설정 (Timeout, Retry)
  - 응답 파싱 및 에러 처리
  
- [ ] **Day 3-4**: 결제 준비 API
  - `POST /api/v1/payments/prepare`
  - 멱등성 보장 (merchant_order_id)
  - DB 저장 (payments 테이블)
  
- [ ] **Day 5**: 콜백 처리
  - `GET /api/v1/payments/success` (Merchant 수신)
  - `POST /online/v1/payment/approve` 연동
  - 결제 상태 업데이트

#### Week 4: E2E 통합 및 MVP 완성
- [ ] **Day 1-2**: Merchant ↔ PSP 연동
  - `POST /checkout_sessions/{id}/complete` 구현
  - PSP 클라이언트 호출
  - 주문 상태 관리
  
- [ ] **Day 3**: 에이전트 시뮬레이터 (기본)
  - Compose Desktop UI 구축
  - 상품 검색 → 체크아웃 → 결제 플로우
  
- [ ] **Day 4**: E2E 테스트
  - 실제 카카오페이 테스트 결제 수행
  - 전체 플로우 검증
  
- [ ] **Day 5**: MVP 리뷰 및 데모
  - 코드 리뷰
  - 데모 준비
  - 다음 단계 계획 수립

**Milestone 1 완료 기준**:
- ✅ 상품 피드 조회 가능
- ✅ 체크아웃 세션 생성 가능
- ✅ 카카오페이 테스트 결제 완료
- ✅ 에이전트 시뮬레이터로 E2E 데모 가능

---

### Milestone 2: 프로덕션 준비 (Week 5-8)

**목표**: ACP 스펙 완벽 구현, 보안 강화, 관측성 구축

#### Week 5: Product Feed 확장
- [ ] **Day 1-2**: Product Feed Spec 전체 필드 구현
  - Recommended Fields 추가 (gtin, mpn, sale_price 등)
  - Optional Fields 추가 (variants, reviews 등)
  - DB 스키마 확장 (product_variants, product_images)
  
- [ ] **Day 3-4**: 피드 포맷 지원
  - JSON Lines (.jsonl.gz) 포맷
  - CSV (.csv.gz) 포맷
  - GZIP 압축 응답
  
- [ ] **Day 5**: 피드 성능 최적화
  - 페이지네이션 (Cursor-based)
  - Full-Text Search 인덱스
  - ETag 기반 조건부 응답

#### Week 6: Checkout Flow 완성
- [ ] **Day 1-2**: Checkout Session 업데이트
  - `POST /checkout_sessions/{id}` 구현
  - 배송지 추가/변경
  - 할인 코드 적용
  
- [ ] **Day 3-4**: Fulfillment Options
  - 배송 방법 정의 (일반, 빠른, 당일)
  - 배송비 계산 로직
  - 배송 가능 지역 검증
  
- [ ] **Day 5**: 세션 관리
  - `GET /checkout_sessions/{id}` 구현
  - `POST /checkout_sessions/{id}/cancel` 구현
  - 세션 만료 처리 (30분 타임아웃)

#### Week 7: 보안 강화
- [ ] **Day 1-2**: 암호화 구현
  - AES-256-GCM 암호화 서비스
  - 민감 필드 암호화 (pg_token, tid)
  - AWS KMS 또는 Vault 연동 (선택)
  
- [ ] **Day 3-4**: 인증 및 인가
  - API Key 기반 인증
  - 요청 서명 검증 (HMAC-SHA256)
  - Rate Limiting (Redis 기반)
  
- [ ] **Day 5**: 보안 테스트
  - OWASP ZAP 스캔
  - 취약점 수정
  - 보안 체크리스트 검토

#### Week 8: 관측성 구축
- [ ] **Day 1-2**: 메트릭 구현
  - Micrometer 설정
  - 비즈니스 메트릭 (주문 수, 매출, 결제 성공률)
  - 시스템 메트릭 (응답 시간, 메모리, DB 커넥션)
  
- [ ] **Day 3-4**: 로깅 및 트레이싱
  - 구조화된 JSON 로깅
  - 민감 정보 마스킹
  - OpenTelemetry 분산 트레이싱
  
- [ ] **Day 5**: 대시보드 구축
  - Grafana 대시보드 생성
  - Prometheus Alertmanager 설정
  - 알림 규칙 정의

**Milestone 2 완료 기준**:
- ✅ ACP Product Feed Spec 100% 구현
- ✅ ACP Checkout Spec 100% 구현
- ✅ 보안 취약점 0건
- ✅ 메트릭 대시보드 운영 중
- ✅ 테스트 커버리지 > 70%

---

### Milestone 3: ChatGPT 연동 및 최적화 (Week 9-12)

**목표**: ChatGPT Custom GPT 연동, 성능 최적화, 문서화 완성

#### Week 9: Webhook 및 이벤트 처리
- [ ] **Day 1-2**: Webhook 구현
  - PSP → Merchant 웹훅 (결제 상태 변경)
  - Merchant → OpenAI 웹훅 (주문 이벤트)
  - 서명 검증 및 재시도 로직
  
- [ ] **Day 3-4**: 이벤트 기반 아키텍처
  - 주문 이벤트 발행 (order.created, order.updated)
  - 이벤트 로그 저장 (Audit Trail)
  
- [ ] **Day 5**: 통합 테스트
  - Webhook 시나리오 테스트
  - 재시도 로직 검증

#### Week 10: ChatGPT 연동
- [ ] **Day 1-2**: 외부 노출 설정
  - ngrok 또는 Cloudflare Tunnel 설정
  - HTTPS 인증서
  - CORS 설정
  
- [ ] **Day 3-4**: OpenAI Actions 설정
  - `openapi.yaml` 작성
  - Custom GPT 생성
  - Actions 등록 및 테스트
  
- [ ] **Day 5**: E2E 데모
  - ChatGPT에서 실제 구매 플로우 테스트
  - 피드백 수집 및 개선

#### Week 11: 성능 최적화
- [ ] **Day 1-2**: 부하 테스트
  - Gatling 또는 K6 시나리오 작성
  - 목표: 1000 TPS, P95 < 500ms
  - 병목 지점 파악
  
- [ ] **Day 3-4**: 최적화 작업
  - DB 쿼리 최적화 (인덱스 추가)
  - Redis 캐싱 전략 개선
  - WebClient Timeout 튜닝
  
- [ ] **Day 5**: 성능 검증
  - 재부하 테스트
  - 메트릭 확인
  - 성능 리포트 작성

#### Week 12: 문서화 및 최종 점검
- [ ] **Day 1-2**: API 문서화
  - Swagger/OpenAPI 설정
  - 모든 엔드포인트 설명 추가
  - 예제 요청/응답 추가
  
- [ ] **Day 3**: 운영 문서 작성
  - Runbook (배포, 장애 대응, 롤백)
  - FAQ (자주 발생하는 에러)
  - 모니터링 가이드
  
- [ ] **Day 4**: 최종 테스트
  - 전체 E2E 테스트
  - 보안 재검증
  - 성능 재검증
  
- [ ] **Day 5**: 프로젝트 완료 및 회고
  - 최종 데모
  - 회고 미팅
  - 다음 단계 논의

**Milestone 3 완료 기준**:
- ✅ ChatGPT Custom GPT 연동 완료
- ✅ 실제 결제 데모 가능
- ✅ 성능 목표 달성 (1000 TPS, P95 < 500ms)
- ✅ 테스트 커버리지 > 80%
- ✅ 문서화 100% 완성

---

## 👥 역할 및 책임

### 개발자 (Full-Stack)
- **담당**: 전체 시스템 개발
- **책임**:
  - Merchant 서버 구현
  - PSP 서버 구현
  - 에이전트 시뮬레이터 구현
  - 테스트 작성
  - 문서화

### DevOps (선택, 필요 시)
- **담당**: 인프라 및 배포
- **책임**:
  - Docker Compose 관리
  - CI/CD 파이프라인 구축
  - 모니터링 설정

---

## 🛠️ 개발 환경 및 도구

### 필수 도구

| 도구 | 버전 | 용도 |
|------|------|------|
| JDK | 21 | Kotlin 실행 환경 |
| Kotlin | 2.1 | 주 개발 언어 |
| Gradle | 8.x | 빌드 도구 |
| Docker | 24.x | 컨테이너 실행 |
| PostgreSQL | 16 | 데이터베이스 |
| Redis | 7.x | 캐싱 |
| IntelliJ IDEA | 2024.x | IDE |

### 개발 환경 설정 체크리스트

- [ ] JDK 21 설치 및 `JAVA_HOME` 설정
- [ ] Docker Desktop 설치 및 실행
- [ ] IntelliJ IDEA 설치 및 Kotlin 플러그인 활성화
- [ ] Git 설정 (사용자명, 이메일)
- [ ] `.env` 파일 생성 및 카카오페이 API 키 설정
- [ ] Docker Compose 실행: `docker-compose -f docker/docker-compose.yml up -d`
- [ ] DB 초기화 확인: `psql -h localhost -U user -d acp` (PW: password)
- [ ] Gradle 빌드 테스트: `./gradlew clean build`

---

## 📊 진행 상황 추적

### 주간 리뷰 미팅

- **일정**: 매주 금요일 오후 3시
- **참석자**: 전체 팀
- **안건**:
  1. 이번 주 완료 작업 리뷰
  2. 다음 주 계획 수립
  3. 블로커 및 리스크 논의
  4. 데모 (가능 시)

### 일일 스탠드업 (선택)

- **일정**: 매일 오전 10시 (15분)
- **형식**:
  - 어제 한 일
  - 오늘 할 일
  - 블로커

### 진행 상황 대시보드

GitHub Projects 또는 Jira를 사용하여 작업 추적:

```
To Do → In Progress → In Review → Done
```

---

## ⚠️ 리스크 관리

### 주요 리스크 및 대응 방안

| 리스크 | 확률 | 영향 | 대응 방안 |
|--------|------|------|-----------|
| 카카오페이 API 변경 | 중간 | 높음 | 공식 문서 주기적 확인, 버전 고정 |
| ACP 스펙 이해 부족 | 높음 | 높음 | OpenAI 문서 정독, 커뮤니티 참고 |
| 성능 목표 미달성 | 중간 | 중간 | 조기 부하 테스트, 점진적 최적화 |
| 보안 취약점 발견 | 낮음 | 높음 | OWASP 가이드 준수, 정기 스캔 |
| 일정 지연 | 중간 | 중간 | 우선순위 조정, MVP 범위 축소 |

### 대응 전략

1. **조기 경보**: 주간 리뷰에서 리스크 점검
2. **우선순위 조정**: 핵심 기능 우선 개발
3. **기술 부채 관리**: 리팩토링 시간 확보 (전체 시간의 20%)

---

## 📈 성과 측정

### 주간 메트릭

- **코드 커버리지**: JaCoCo 리포트
- **빌드 성공률**: CI/CD 대시보드
- **API 응답 시간**: Grafana 대시보드
- **결제 성공률**: 비즈니스 메트릭

### 마일스톤별 체크포인트

| Milestone | 체크포인트 | 목표 |
|-----------|------------|------|
| M1 (Week 4) | MVP 데모 | 기본 결제 플로우 완료 |
| M2 (Week 8) | 프로덕션 준비 | ACP 스펙 100%, 보안 강화 |
| M3 (Week 12) | ChatGPT 연동 | 실제 결제 데모 |

---

## 🎓 학습 자료

### 필수 읽기

1. **OpenAI ACP 문서**
   - [Get Started](https://developers.openai.com/commerce/guides/get-started)
   - [Checkout Spec](https://developers.openai.com/commerce/specs/checkout)
   - [Product Feed Spec](https://developers.openai.com/commerce/specs/feed)

2. **카카오페이 문서**
   - [단건 결제](https://developers.kakaopay.com/docs/payment/online/single-payment)
   - [API 공통 가이드](https://developers.kakaopay.com/docs/getting-started/api-common-guide/restapi)

3. **아키텍처 패턴**
   - [Hexagonal Architecture](https://alistair.cockburn.us/hexagonal-architecture/)
   - [Domain-Driven Design](https://www.domainlanguage.com/ddd/)

### 추천 읽기

- [Spring Boot 3.5.3 Reference](https://docs.spring.io/spring-boot/docs/3.5.3/reference/html/)
- [jOOQ Manual](https://www.jooq.org/doc/latest/manual/)
- [Kotlin Coroutines Guide](https://kotlinlang.org/docs/coroutines-guide.html)
- [OWASP Top 10](https://owasp.org/www-project-top-ten/)

---

## 📝 커밋 컨벤션

### 커밋 메시지 형식 (한글)

```
[타입] 제목 (50자 이내)

본문 (선택, 72자 줄바꿈)

Footer (선택)
```

### 타입

- `[기능]`: 새로운 기능 추가
- `[수정]`: 버그 수정
- `[리팩토링]`: 코드 리팩토링
- `[문서]`: 문서 수정
- `[테스트]`: 테스트 추가/수정
- `[설정]`: 설정 파일 변경

### 예시

```
[기능] 체크아웃 세션 생성 API 구현

- POST /checkout_sessions 엔드포인트 추가
- 가격 계산 엔진 구현
- 통합 테스트 작성

Closes #12
```

---

## 🎉 프로젝트 완료 기준

### Definition of Done

- [ ] 모든 기능 요구사항 구현 완료
- [ ] 테스트 커버리지 > 80%
- [ ] 보안 취약점 0건
- [ ] 성능 목표 달성 (1000 TPS, P95 < 500ms)
- [ ] API 문서화 100%
- [ ] 운영 문서 작성 완료
- [ ] ChatGPT 연동 데모 성공
- [ ] 코드 리뷰 완료
- [ ] 회고 미팅 완료

---

## 📞 연락처 및 지원

### 기술 지원

- **OpenAI 커뮤니티**: [OpenAI Developer Forum](https://community.openai.com/)
- **카카오페이 지원**: [개발자센터 포럼](https://developers.kakaopay.com/forum)
- **Stack Overflow**: `[agentic-commerce]` 태그

### 프로젝트 관리

- **GitHub Repository**: `https://github.com/your-org/acp`
- **Slack Channel**: `#acp-project`
- **이슈 트래커**: GitHub Issues

---

## 🚀 다음 단계 (프로젝트 완료 후)

### Phase 2 계획

1. **Cafe24 연동**: 실제 상품 소싱
2. **다중 PSP 지원**: Stripe, Toss 추가
3. **Kubernetes 배포**: 프로덕션 환경 구축
4. **AI 에이전트 고도화**: 자연어 처리 개선
5. **글로벌 확장**: 다국어 지원, 다중 통화

---

**프로젝트 시작일**: 2025-12-29  
**예상 완료일**: 2026-03-29  
**버전**: 1.0.0-SNAPSHOT
