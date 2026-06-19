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
│  - 결제 준비 (POST /api/v1/payments/prepare)│
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

### Observability (예정)
- **Metrics**: Micrometer + Prometheus (예정)
- **Logging**: Logback
- **Tracing**: OpenTelemetry (예정)
- **Dashboard**: Grafana (예정)

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
git clone https://github.com/parkmh04/acp.git
cd acp
```

> **포크 후 바로 실행**: 클론(1) 후 아래 순서(2→3→4→5)만 따르면 추가 설정 없이 두 서버가 기동되고
> 상품 피드/체크아웃 데모가 동작합니다. 카카오페이 **실결제**만 실제 키가 필요합니다(3번 참고).

### 2. 인프라 실행 (PostgreSQL, Redis)

```bash
docker compose -f docker/docker-compose.yml up -d
```

PostgreSQL 컨테이너는 **최초 기동 시** `merchant`/`psp` 스키마와 모든 테이블을
`docker/initdb` 스크립트로 **자동 생성**합니다(별도 마이그레이션 실행 불필요).

> 스키마를 처음부터 다시 만들려면 볼륨까지 초기화: `docker compose -f docker/docker-compose.yml down -v`

DB 접속 확인:
```bash
psql -h localhost -p 5432 -U user -d acp   # Password: password
```

### 3. (선택) 환경 변수 — 실제 외부 연동 시에만 필요

기본값이 모두 설정되어 있어 **데모 실행에는 `.env`가 필요 없습니다.**
카카오페이 실결제나 Cafe24 실연동을 하려면 키를 셸 환경변수로 주입하세요.
(Spring은 `.env` 파일을 자동 로드하지 않으므로 `export` 또는 OS 환경변수를 사용합니다.)

```bash
cp .env.template .env          # 참고용 템플릿
export KAKAOPAY_SECRET_KEY_DEV=발급받은_dev_secret_key
export CAFE24_MALL_ID=...      # Cafe24 연동 시
```

> ⚠️ 실제 키는 절대 커밋하지 마세요. `.env`는 `.gitignore`로 제외됩니다.
> 미설정 시 카카오페이 시크릿은 더미값으로 기동되며, `/feed`는 Cafe24 대신 **로컬 시드 상품**을 반환합니다.

### 4. 빌드

```bash
./gradlew clean build
```

> jOOQ 코드 생성이 **빌드 타임에 DB 스키마를 읽으므로 2번(인프라)이 먼저 떠 있어야 합니다.**

### 5. 서버 실행

```bash
# 터미널 1 - Merchant (8080)
./gradlew :acp-merchant:bootRun

# 터미널 2 - PSP (8081)
./gradlew :acp-psp:bootRun
```

### 6. API 테스트

**상품 피드 조회** (로컬 시드 3건 반환):
```bash
curl http://localhost:8080/feed
```

**체크아웃 세션 생성** (위 피드의 실제 상품 ID 사용, 예: `PROD-001`):
```bash
curl -X POST http://localhost:8080/checkout_sessions \
  -H "Content-Type: application/json" \
  -d '{
    "items": [{"id": "PROD-001", "quantity": 1}],
    "buyer": {"email": "user@example.com", "name": "홍길동"}
  }'
```

> **데모 한계**: 카카오페이 결제 준비(`/complete`) 이후 단계는 유효한 카카오페이 키와
> 외부 리다이렉트가 필요합니다. 실결제 데모는 2번에서 키를 주입하세요.
> `acp-client`(에이전트 시뮬레이터)는 현재 스캐폴드 단계로 비어 있습니다.

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
│       └── db/migration/  # 수동 SQL 스크립트 (Flyway 미사용)
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
│   └── docker-compose.yml # PostgreSQL, Redis (Prometheus/Grafana는 미포함)
│
└── docs/                  # 프로젝트 문서
```

---

## 🧪 테스트

### 전체 테스트 실행

단위/통합 테스트는 모두 `test` 태스크로 실행됩니다 (통합 테스트는 Testcontainers 기반).

```bash
./gradlew test
```

> **참고**: 별도 `integrationTest` 태스크와 JaCoCo 커버리지 리포트(`jacocoTestReport`)는 아직 구성되지 않았습니다 (예정).

---

## 📈 모니터링

Spring Boot Actuator + Micrometer가 두 서버에 적용되어 있습니다. (노출: `health`, `info`, `prometheus`)

### 헬스 체크

```bash
curl http://localhost:8080/actuator/health   # Merchant
curl http://localhost:8081/actuator/health   # PSP
# liveness/readiness 프로브: /actuator/health/{liveness,readiness}
```

### Prometheus 메트릭

```bash
curl http://localhost:8080/actuator/prometheus  # Merchant
curl http://localhost:8081/actuator/prometheus  # PSP
```

### Grafana 대시보드 (예정)

> Prometheus/Grafana 컨테이너는 아직 `docker-compose.yml`에 포함되지 않았습니다.
> 추가 후 `http://localhost:3000`(admin/admin)에서 위 `/actuator/prometheus`를 스크랩하여 사용합니다.

---

## 🔒 보안

### 민감 정보 보호

- **암호화**: AES-256-GCM — PSP `pg_tid` 저장 시 암호화 (구현됨, `AesEncryptionAdapter`)
- **로그 마스킹**: 이메일, 전화번호, 카드 정보 자동 마스킹 (예정)
- **HTTPS 전용**: TLS 1.3 / HSTS (예정, 운영 배포 시)
- **API Key 인증**: HMAC-SHA256 서명 검증 (예정)

### 보안 스캔 (예정)

> OWASP Dependency Check, detekt 플러그인은 아직 빌드에 구성되지 않았습니다. 구성 후 아래 명령으로 실행합니다.

```bash
# OWASP Dependency Check (예정)
./gradlew dependencyCheckAnalyze

# 정적 분석 (예정)
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

- **이슈 트래커**: [GitHub Issues](https://github.com/parkmh04/acp/issues)
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

### Milestone 1: MVP (Week 1-4) 🚧
- [x] 기본 체크아웃 플로우
- [x] 카카오페이 결제 통합 (코드 구현, 실결제 미검증)
- [ ] 에이전트 시뮬레이터 (acp-client 스캐폴드)

### Milestone 2: 프로덕션 준비 (Week 5-8) 🚧
- [ ] ACP 스펙 100% 구현 (세금/할인/피드 포맷 미구현)
- [ ] 보안 강화 (tid 암호화·RFC7807만, 인증/rate limit 미구현)
- [ ] 관측성 구축 (Actuator/Prometheus·헬스체크 O, 로깅/트레이싱/Grafana 미구현)

### Milestone 3: ChatGPT 연동 (Week 9-12) 📅
- [ ] Custom GPT 연동
- [ ] 성능 최적화
- [ ] 문서화 완성

---

**Made with ❤️ for the AI Agent Era**
