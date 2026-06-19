# acp-merchant (Merchant 서버, :8080)

ACP 스펙의 **상품 피드 + 체크아웃**을 담당하는 서버. 헥사고날 아키텍처.

- **스택**: Kotlin, Spring Boot 3.5.3(WebFlux/WebClient), Coroutines, jOOQ(PostgreSQL `merchant` 스키마), Redis(Redisson/Caffeine)
- **포트**: 8080

## 주요 엔드포인트
| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/feed` | 상품 피드 (Cafe24 우선, 미설정 시 로컬 시드 폴백) |
| POST | `/checkout_sessions` | 세션 생성 |
| GET | `/checkout_sessions/{id}` | 세션 조회 |
| POST | `/checkout_sessions/{id}` | 세션 수정 |
| POST | `/checkout_sessions/{id}/complete` | 결제 준비(PSP 호출) |
| POST | `/checkout_sessions/{id}/confirm` | 결제 승인·주문 생성 |
| POST | `/checkout_sessions/{id}/cancel` | 세션 취소 |
| GET | `/api/v1/payments/{success,completed,cancel,fail}` | 카카오페이 리다이렉트 콜백 |
| GET | `/actuator/health`, `/actuator/prometheus` | 헬스/메트릭 |

## 구조 (헥사고날)
```
adapter/inbound/web      # CheckoutController, FeedController, PaymentCallbackController, GlobalExceptionHandler
adapter/outbound/web     # PaymentClientAdapter (→ PSP)
adapter/outbound/cafe24  # Cafe24ProductAdapter
adapter/outbound/persistence # jOOQ Repository 어댑터
application/port + service# UseCase 포트와 구현 (CheckoutService, ProductFeedService ...)
domain/model + service   # CheckoutSession, Order, PricingEngine(VAT 10%), ShippingCalculator, AddressValidator
config                   # Redis, Cafe24, DataInitializer
```

## 실행
루트 [README](../README.md) "빠른 시작" 참고. 요약:
```bash
docker compose -f ../docker/docker-compose.yml up -d   # 스키마 자동 생성
./gradlew :acp-merchant:bootRun
```

## 테스트
```bash
./gradlew :acp-merchant:test   # Testcontainers 기반 통합 테스트 포함
```
