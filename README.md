# 🛒 Agentic Commerce Protocol (ACP) - 레퍼런스 구현

> **프로덕션 레디, Type-Safe, AI 에이전트 시대를 위한 커머스 시스템**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.1-7F52FF?logo=kotlin)](https://kotlinlang.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.3-6DB33F?logo=spring)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql)](https://www.postgresql.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

본 프로젝트는 **OpenAI의 Agentic Commerce Protocol (ACP)**을 완벽히 구현한 레퍼런스 커머스 시스템입니다.  
AI 에이전트(ChatGPT 등)가 상품을 검색하고, 장바구니를 구성하며, **카카오페이**를 통해 실제 결제를 완료할 수 있는 **프로덕션 수준**의 시스템입니다.

---

## 🎯 프로젝트 목표

1. **OpenAI ACP 스펙 100% 준수**
   - [Product Feed Spec](https://developers.openai.com/commerce/specs/feed) 완벽 구현
   - [Checkout Spec](https://developers.openai.com/commerce/specs/checkout) 완벽 구현

2. **실제 결제 가능**
   - 카카오페이 단건 결제 API 통합
   - 테스트 환경에서 실제 결제 데모 가능

3. **프로덕션 품질**
   - 헥사고날 아키텍처 + DDD
   - Type-Safe SQL (jOOQ)
   - 보안, 관측성, 테스트 커버리지 80%+

---

## 📚 문서

| 문서 | 설명 |
|------|------|
| **[📋 TODO.md](docs/TODO.md)** | 프로젝트 로드맵 및 상세 작업 목록 (9개 Phase) |
| **[🏗️ ARCHITECTURE.md](docs/ARCHITECTURE.md)** | 시스템 아키텍처, 시퀀스 다이어그램, API 계약 |
| **[🗄️ DB_SCHEMA.md](docs/DB_SCHEMA.md)** | 데이터베이스 스키마 설계 (Merchant/PSP 분리) |
| **[📅 PROJECT_PLAN.md](docs/PROJECT_PLAN.md)** | 12주 프로젝트 실행 계획서 |
| **[📦 CAFE24_INTEGRATION.md](docs/CAFE24_INTEGRATION.md)** | Cafe24 Open API 연동 및 변환 가이드 |
| **[📊 STATUS.md](docs/STATUS.md)** | 프로젝트 현황 요약 (완료/진행 작업) |

---

## 🏗️ 시스템 아키텍처

### 2-Server 물리적 분리

```
┌─────────────────┐
│  AI Agent       │  ChatGPT 또는 에이전트 시뮬레이터
│  (ChatGPT)      │
└────────┬────────┘
         │ HTTPS (ACP Protocol)
         ▼
┌─────────────────────────────────────────────┐
│  Merchant Server (:8080)                    │
│  - 상품 피드 제공 (GET /feed)               │
│  - 체크아웃 세션 관리                       │
│  - 주문 생성 및 상태 관리                   │
└────────┬────────────────────────────────────┘
         │ HTTP (Internal)
         ▼
┌─────────────────────────────────────────────┐
│  PSP Server (:8081)                         │
│  - 결제 준비 (POST /payments/prepare)       │
│  - 카카오페이 API 래핑                      │
│  - 결제 상태 관리                           │
└────────┬────────────────────────────────────┘
         │ HTTPS (External)
         ▼
┌─────────────────────────────────────────────┐
│  KakaoPay API                               │
│  https://open-api.kakaopay.com              │
└─────────────────────────────────────────────┘
```

### 주요 특징

- **물리적 분리**: Merchant와 PSP를 독립된 프로세스로 실행하여 실제 환경 시뮬레이션
- **헥사고날 아키텍처**: 도메인 로직과 외부 의존성 완전 격리
- **Type-Safe SQL**: jOOQ를 통한 컴파일 타임 SQL 검증
- **Non-Blocking**: Kotlin Coroutines + Virtual Threads (JDK 21)

---

## 🛠️ 기술 스택

### Core
- **언어**: Kotlin 2.1
- **JVM**: OpenJDK 21 (Virtual Threads)
- **프레임워크**: Spring Boot 3.5.3, Spring WebFlux

### Database
- **RDBMS**: PostgreSQL 16
- **SQL**: jOOQ (Type-Safe Query Builder)
- **Migration**: 수동 SQL 스크립트 (`src/main/resources/db/migration/`, Flyway 미사용)
- **Cache**: Redis 7.x

### Observability
- **Metrics**: Micrometer + Prometheus
- **Logging**: Logback (Structured JSON)
- **Tracing**: OpenTelemetry (예정)
- **Dashboard**: Grafana

### Testing
- **Unit Test**: JUnit 5, Mockk
- **Integration Test**: Testcontainers, WebTestClient
- **Load Test**: Gatling (예정)

### Client
- **UI**: Kotlin Multiplatform, Compose for Desktop

---

## 🚀 빠른 시작

### 사전 요구사항

- **JDK 21** 이상
- **Docker** 및 **Docker Compose**
- **Gradle** 8.x (Wrapper 포함)

### 1. 저장소 클론

```bash
git clone https://github.com/your-org/acp.git
cd acp
```

### 2. 환경 변수 설정

`.env.template`을 복사하여 `.env` 파일을 생성하고 카카오페이 API 키를 입력합니다.

```bash
cp .env.template .env
```

`.env` 파일에 발급받은 카카오페이 API 키를 입력합니다 (실제 키를 커밋하지 마세요):
```bash
# KakaoPay API Credentials
KAKAOPAY_CLIENT_ID=your_kakaopay_client_id_here
KAKAOPAY_CLIENT_SECRET=your_kakaopay_client_secret_here
KAKAOPAY_SECRET_KEY_DEV=your_kakaopay_dev_secret_key_here
KAKAOPAY_SECRET_KEY_PROD=your_kakaopay_prod_secret_key_here
```

> ⚠️ 키 발급은 [카카오페이 개발자센터](https://developers.kakaopay.com/)에서 진행하며, `.env`는 `.gitignore`로 제외됩니다.

### 3. 인프라 실행 (PostgreSQL, Redis)

```bash
docker-compose -f docker/docker-compose.yml up -d
```

DB 접속 확인:
```bash
psql -h localhost -p 5432 -U postgres -d acp
# Password: postgres
```

### 4. 빌드 및 테스트

```bash
./gradlew clean build
```

### 5. 서버 실행

**터미널 1 - Merchant 서버**:
```bash
./gradlew :acp-merchant:bootRun
```

**터미널 2 - PSP 서버**:
```bash
./gradlew :acp-psp:bootRun
```

### 6. API 테스트

**상품 피드 조회**:
```bash
curl http://localhost:8080/feed
```

**체크아웃 세션 생성**:
```bash
curl -X POST http://localhost:8080/checkout_sessions \
  -H "Content-Type: application/json" \
  -d '{
    "items": [{"id": "prod_001", "quantity": 1}],
    "buyer": {"email": "user@example.com", "name": "홍길동"}
  }'
```

---

## 📊 프로젝트 구조

```
acp/
├── acp-merchant/          # Merchant 서버 (Port 8080)
│   ├── src/main/kotlin/
│   │   ├── adapter/       # REST Controllers, Repositories
│   │   ├── application/   # Use Cases
│   │   ├── domain/        # 순수 도메인 로직
│   │   └── config/        # Spring 설정
│   └── src/main/resources/
│       └── db/migration/  # Flyway SQL
│
├── acp-psp/               # PSP 서버 (Port 8081)
│   ├── src/main/kotlin/
│   │   ├── adapter/
│   │   ├── application/
│   │   ├── domain/
│   │   └── config/
│   └── src/main/resources/
│       └── db/migration/
│
├── acp-shared/            # 공유 스키마 (Kotlin Multiplatform)
│   └── src/commonMain/kotlin/com/acp/schema/
│       ├── feed/          # Product Feed Models
│       ├── checkout/      # Checkout Models
│       └── payment/       # Payment Models
│
├── acp-client/            # 에이전트 시뮬레이터 (Compose Desktop)
│
├── docker/
│   └── docker-compose.yml # PostgreSQL, Redis, Prometheus, Grafana
│
└── docs/                  # 프로젝트 문서
```

---

## 🧪 테스트

### 단위 테스트 실행

```bash
./gradlew test
```

### 통합 테스트 실행

```bash
./gradlew integrationTest
```

### 테스트 커버리지 확인

```bash
./gradlew jacocoTestReport
open build/reports/jacoco/test/html/index.html
```

---

## 📈 모니터링

### Prometheus

```bash
# Merchant 메트릭
curl http://localhost:8080/actuator/prometheus

# PSP 메트릭
curl http://localhost:8081/actuator/prometheus
```

### Grafana 대시보드

```bash
# Grafana 접속 (Docker Compose 실행 시)
open http://localhost:3000
# ID: admin, PW: admin
```

---

## 🔒 보안

### 민감 정보 보호

- **암호화**: AES-256-GCM (pg_token, tid, API 시크릿)
- **로그 마스킹**: 이메일, 전화번호, 카드 정보 자동 마스킹
- **HTTPS 전용**: TLS 1.3
- **API Key 인증**: HMAC-SHA256 서명 검증

### 보안 스캔

```bash
# OWASP Dependency Check
./gradlew dependencyCheckAnalyze

# 정적 분석
./gradlew detekt
```

---

## 📜 라이선스

Apache License 2.0 - 자세한 내용은 [LICENSE](LICENSE) 파일 참조

---

## 🤝 기여

기여를 환영합니다! 다음 절차를 따라주세요:

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m '[기능] 놀라운 기능 추가'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## 📞 지원 및 문의

- **이슈 트래커**: [GitHub Issues](https://github.com/your-org/acp/issues)
- **OpenAI 커뮤니티**: [Developer Forum](https://community.openai.com/)
- **카카오페이 지원**: [개발자센터](https://developers.kakaopay.com/)

---

## 🎓 참고 자료

### OpenAI ACP
- [Get Started Guide](https://developers.openai.com/commerce/guides/get-started)
- [Checkout Spec](https://developers.openai.com/commerce/specs/checkout)
- [Product Feed Spec](https://developers.openai.com/commerce/specs/feed)

### KakaoPay
- [단건 결제 가이드](https://developers.kakaopay.com/docs/payment/online/single-payment)
- [API 공통 가이드](https://developers.kakaopay.com/docs/getting-started/api-common-guide/restapi)

### Architecture
- [Hexagonal Architecture](https://alistair.cockburn.us/hexagonal-architecture/)
- [Domain-Driven Design](https://www.domainlanguage.com/ddd/)

---

## 🚀 로드맵

현재 진행 상황 및 향후 계획은 [TODO.md](docs/TODO.md)를 참조하세요.

### Milestone 1: MVP (Week 1-4) ✅
- [x] 기본 체크아웃 플로우
- [x] 카카오페이 결제 통합
- [ ] 에이전트 시뮬레이터

### Milestone 2: 프로덕션 준비 (Week 5-8) 🚧
- [ ] ACP 스펙 100% 구현
- [ ] 보안 강화
- [ ] 관측성 구축

### Milestone 3: ChatGPT 연동 (Week 9-12) 📅
- [ ] Custom GPT 연동
- [ ] 성능 최적화
- [ ] 문서화 완성

---

**Made with ❤️ for the AI Agent Era**
