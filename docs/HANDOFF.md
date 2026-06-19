# 🤝 세션 인계 문서 (Handoff)

> 새 세션/기여자가 바로 이어서 작업할 수 있도록 **현재 상태 · 실행법 · 다음 작업**을 정리한 문서.
> 최종 갱신: 2026-06-19

---

## 1. 한눈에 보는 현재 상태

- **목표**: OpenAI ACP(상품피드+체크아웃) + 카카오페이 결제 레퍼런스. 포크 후 즉시 데모 가능.
- **로드맵 진척**: 가중 종합 ≈ **45~50%** (체크박스 155/430 ≈ 36%). MVP 경로(Phase 0~3) ~75% 견고.
- **데모 경로 동작 확인됨**: `docker compose up` → `gradlew build` → 두 서버 기동 → 상품피드/체크아웃/재고차감/결제준비.
- **CI**: GitHub Actions가 push/PR마다 빌드+테스트 자동 검증.

### Phase별 완성도
| Phase | % | 비고 |
|---|---|---|
| 0 기반 인프라 | 90 | 멀티모듈·헥사고날·docker·CI |
| 1 Product Feed | 60 | Cafe24·DB폴백·시드 / variants·포맷·캐싱 미 |
| 2 Checkout | 80 | 엔드포인트·세금·배송·재고차감·검증·만료·RFC7807 / 할인·멱등키·인증 미 |
| 3 PSP/카카오페이 | 70 | ready/approve/cancel·멱등성·암호화·타임아웃 / 스케줄러·IP화이트리스트 미 |
| 4 에이전트/ChatGPT | 15 | CLI 시뮬레이터만 / GUI·ChatGPT Actions·webhook 미 |
| 5 관측성 | 30 | Actuator·Prometheus·헬스 / 비즈니스메트릭·로깅·트레이싱·Grafana 미 |
| 6 보안 | 20 | tid암호화·SQLi방지 / 인증·HMAC·rate limit·TLS 미 |
| 7 테스트 | 40 | 통합테스트·Testcontainers / JaCoCo·E2E·성능 미 |
| 8 CI/CD | 25 | Actions(빌드+테스트) / 커버리지·정적분석·이미지·k8s 미 |
| 9 문서화 | 45 | README·모듈README·아키텍처·DB / Swagger·Runbook 미 |

---

## 2. 빠른 실행 (검증된 절차)

```bash
docker compose -f docker/docker-compose.yml up -d     # 1. 인프라(스키마 자동 생성)
./gradlew clean build                                 # 2. 빌드(jOOQ codegen은 DB 필요)
./gradlew :acp-merchant:bootRun                       # 3. Merchant :8080
./gradlew :acp-psp:bootRun                            # 4. PSP :8081 (다른 터미널)
./gradlew :acp-client:run --args="demo"               # 5. CLI 에이전트 데모
```
- 데모는 `.env` 불필요(기본값). 카카오페이 실결제만 `KAKAOPAY_SECRET_KEY_DEV` 필요.
- 헬스: `/actuator/health`, 메트릭: `/actuator/prometheus`
- 스키마 초기화: `docker compose -f docker/docker-compose.yml down -v`

---

## 3. 이번 세션에서 완료한 작업 (PR #1~#12, 전부 main 머지)

**문서/정합성**: 시크릿 제거+LICENSE, 문서-코드 정합, DB_SCHEMA 실스키마화, 깨진링크/불용코드 정리, TODO·STATUS·마일스톤 현행화, 모듈별 README 추가.

**부트스트랩(포크 실행가능화)**: docker initdb 스키마 자동생성, secret 기본값, README 실행절차 검증.

**기능/수정**:
- KakaoPay base-url 버그(open-api.kakaopay.com) + jackson 중복키
- 상품 피드 DB 폴백(Cafe24 미설정 시 시드)
- 전역 예외처리(RFC7807)
- PSP 결제준비 멱등성(부분 unique index)
- 영속성 어댑터 IO 격리(withContext)
- Actuator 헬스 + Prometheus
- KakaoPay 콜백 URL 외부화
- acp-client CLI 시뮬레이터(빈 모듈 → 구현)
- 체크아웃 세션 GET 재조회 tax=0 버그 수정
- WebClient 타임아웃(connect 3s/response 10s)
- 결제 확정 시 재고 차감(원자적)
- 입력 검증(수량 1~99·이메일) + 세션 만료(30분)
- GitHub Actions CI

**보안 조치**: 유출된 카카오페이 키 4종 콘솔 재발급 완료(과거 커밋 노출 무효화).

---

## 4. 다음 작업 (우선순위)

### P0 — 실질/빠른 가치
- [ ] **할인(discount) 계산** — 현재 `itemsDiscount=0`. 할인코드 테이블 + 적용 로직 설계 필요(중간 규모).
- [ ] **비즈니스 메트릭** — `checkout_sessions_created_total`, `orders_completed_total`, `payment_success_rate` 등 Micrometer 카운터.
- [ ] **Swagger/OpenAPI** — springdoc 추가, `/swagger-ui.html`. ChatGPT Actions의 openapi.yaml 기반도 됨.

### P1 — 프로덕션 하드닝
- [ ] **인증** — API Key(Authorization: Bearer) + 요청 서명(HMAC-SHA256)
- [ ] **Rate Limiting** — Redis 기반(이미 Redisson 있음)
- [ ] **Idempotency-Key 헤더** — 체크아웃 생성/수정 멱등성
- [ ] **구조화 로깅 + PII 마스킹**, OpenTelemetry 트레이싱
- [ ] **정적분석** — detekt는 Kotlin 2.1 비호환(1.23.x=Kotlin2.0)으로 보류. detekt 2.0 정식 또는 ktlint(대량 포맷 diff 감수) 검토.

### P2 — 기능 확장
- [ ] 재고 복구(주문 취소 시), 상태 전이 검증(불가능 전이 차단)
- [ ] 만료 세션 자동 정리 스케줄러, PSP 결제상태 조회 API(`GET /api/v1/payments/{id}`)
- [ ] 피드 포맷(jsonl/csv/gzip), variants, 캐싱(ETag)
- [ ] acp-client Compose GUI, ChatGPT Custom GPT 연동, Webhook

---

## 5. 작업 규칙/관례 (이 repo)

- **브랜치 → PR → 머지** 워크플로 (직접 main 푸시 지양). 커밋 메시지 한글 `[타입] 제목`.
- **검증 필수**: 변경 후 `docker compose up` 띄운 상태에서 `./gradlew :acp-merchant:test`(또는 해당 모듈) 통과 확인. jOOQ codegen은 빌드타임 DB 연결 필요.
- **헥사고날**: 도메인/포트/어댑터 계층 유지. DTO는 `acp-shared`(kotlinx, snake_case 와이어).
- **마이그레이션**: Flyway 미사용. `db/migration/*.sql` 추가 시 `docker/initdb/00-init.sh`의 적용 순서에도 등록.
- **문서 정직성**: 미구현은 `[ ]` + 사유. 코드로 확인된 것만 `[x]`.

### 알려진 함정
- jOOQ `generateJooq`는 **DB+스키마가 먼저** 있어야 함 → 항상 `docker compose up` 후 빌드.
- 마이그레이션 SQL 변경은 기존 볼륨에 재적용 안 됨 → `down -v` 또는 컨테이너에 수동 `psql` 적용.
- 카카오페이 단건결제는 `open-api.kakaopay.com` + `SECRET_KEY` 헤더 (레거시 `kapi.kakao.com` 아님).
- 테스트 로그의 netty macOS DNS 경고/`Payment approval failed`는 정상(실패경로 테스트).
