# acp-psp (PSP 서버, :8081)

**카카오페이 단건 결제**를 래핑하는 PSP(결제대행) 서버. 헥사고날 아키텍처 + 불변 장부(Immutable Ledger).

- **스택**: Kotlin, Spring Boot 3.5.3(WebFlux/WebClient), Coroutines, jOOQ(PostgreSQL `psp` 스키마)
- **포트**: 8081
- **외부**: 카카오페이 `https://open-api.kakaopay.com` (`/online/v1/payment/*`)

## 주요 엔드포인트
| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/api/v1/payments/prepare` | 결제 준비 (ready) |
| POST | `/api/v1/payments/approve` | 결제 승인 (pg_token) |
| POST | `/api/v1/payments/cancel` | 결제 취소 |
| GET | `/actuator/health`, `/actuator/prometheus` | 헬스/메트릭 |

## 핵심 설계
- **불변 장부**: 상태를 UPDATE하지 않고 `type`(PREPARE/APPROVE/CANCEL) 행을 INSERT. `merchant_order_id`로 그룹핑.
- **멱등성**: `merchant_order_id`당 PREPARE 1건 — 애플리케이션 체크 + DB 부분 unique 인덱스(`uq_payments_prepare_per_order`).
- **암호화**: `pg_tid`를 AES-256-GCM으로 저장 (`AesEncryptionAdapter`).
- **망취소**: 승인 중 네트워크 오류 시 PG 상태 조회 후 필요하면 자동 취소(`handleNetCancel`).

## 구조 (헥사고날)
```
adapter/inbound/web       # PaymentController, GlobalExceptionHandler
adapter/outbound/kakaopay # KakaoPayProvider (PaymentProvider 구현)
adapter/outbound/persistence # PaymentPersistenceAdapter (jOOQ)
application/port + service # PaymentUseCase, PaymentService
infrastructure/config + security # KakaoPayConfig, AesEncryptionAdapter
```

## 실행
```bash
docker compose -f ../docker/docker-compose.yml up -d
./gradlew :acp-psp:bootRun
# 실결제는 KAKAOPAY_SECRET_KEY_DEV 환경변수 필요 (미설정 시 더미로 기동)
```

## 테스트
```bash
./gradlew :acp-psp:test   # PaymentServiceTest, KakaoPayProviderTest, PaymentIntegrationTest
```
