# acp-client (에이전트 시뮬레이터)

AI 에이전트가 Merchant 서버를 호출하는 흐름을 시연하는 클라이언트.

- **빌드 타입**: Kotlin Multiplatform (Desktop/JVM)
- **스택**: Ktor Client(CIO), kotlinx.serialization, clikt(CLI), Compose Desktop(GUI 예정)
- **공유**: `acp-shared` DTO 재사용

## 현재 상태
- ✅ **CLI 에이전트 시뮬레이터** — 상품 검색 → 체크아웃 세션 생성 흐름 동작
- 🚧 Compose Desktop GUI — 예정 (build에 의존성 구성됨)

## 실행
Merchant 서버(:8080)가 떠 있어야 합니다 (루트 [README](../README.md) 참고).

```bash
# 피드 → 첫 상품으로 체크아웃 세션 생성
./gradlew :acp-client:run --args="demo"

# 상품 피드만 조회
./gradlew :acp-client:run --args="feed"

# 세션 조회
./gradlew :acp-client:run --args="get --id <sessionId>"

# 다른 Merchant URL 지정
./gradlew :acp-client:run --args="--base-url http://localhost:8080 demo"
```

예시 출력(`demo`):
```
선택 상품: PROD-001 (Classic White T-Shirt)
세션 생성됨: 89b3f0ff-... [NOT_READY]
  상품 금액: 25000KRW
  부가세: 2500KRW
  총 결제 금액: 27500KRW
```

## 구조
```
src/commonMain/kotlin/com/acp/client/AcpApiClient.kt  # Ktor 기반 Merchant API 클라이언트
src/desktopMain/kotlin/com/acp/client/Main.kt          # clikt CLI (feed/demo/get)
```

## 향후 (Phase 4)
- 채팅 인터페이스(Compose), 프로토콜 디버거(JSON 타임라인), 결제 플로우 시각화 — [docs/TODO.md](../docs/TODO.md) 참고.
