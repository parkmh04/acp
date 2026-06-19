# acp-shared

ACP 프로토콜의 **공유 스키마/DTO** 모듈. Merchant·PSP·Client가 동일한 계약(Contract)을 사용하도록 한다.

- **빌드 타입**: Kotlin Multiplatform (JVM 타깃)
- **직렬화**: kotlinx.serialization (`@Serializable`)
- **의존성 없음**: 프레임워크/DB 의존 없는 순수 모델

## 구조

```
src/commonMain/kotlin/com/acp/schema/
├── feed/FeedModels.kt        # ProductFeedItem, Availability, Condition
├── checkout/CheckoutModels.kt# CreateCheckoutSessionRequest/Response, CheckoutItem, Totals, FulfillmentOption ...
└── payment/PaymentModels.kt  # PaymentPrepare/Approve/Cancel Request·Response, CardInfo, PaymentItem
```

## 사용처
- `acp-merchant`, `acp-psp`: API 요청/응답 DTO
- `acp-client`: Merchant API 호출 시 동일 DTO 재사용

> 와이어 포맷은 snake_case (Merchant는 Jackson `SNAKE_CASE`, Client는 kotlinx `JsonNamingStrategy.SnakeCase`).
