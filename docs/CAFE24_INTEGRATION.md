# 📦 Cafe24 API 연동 가이드

> Cafe24 Open API를 활용하여 실제 쇼핑몰 상품 데이터를 ACP 스펙으로 변환하는 가이드

---

## 🔑 인증 정보

### 앱 정보

**중요**: 실제 인증 정보는 `.env` 파일에서 관리합니다. 절대 Git에 커밋하지 마세요!

환경 변수 설정:
```bash
# .env 파일에 다음 정보 입력
CAFE24_CLIENT_ID=your_client_id_here
CAFE24_CLIENT_SECRET=your_client_secret_here
CAFE24_SERVICE_KEY=your_service_key_here
CAFE24_MALL_ID=your_mall_id_here
```

인증 정보는 [Cafe24 개발자센터](https://developers.cafe24.com/)에서 앱 생성 후 확인할 수 있습니다.

### Webhook 수신 IP (방화벽 허용 필요)
| Name | IP | Port |
|------|-----|------|
| WebHook 발송 서버 | 119.205.223.208 | 443 (HTTPS) |
| | 119.205.223.209 | 443 (HTTPS) |
| | 203.245.45.182 | 443 (HTTPS) |
| | 203.245.45.183 | 443 (HTTPS) |

---

## 🚀 빠른 시작

### 1. 환경 변수 설정

`.env.template`을 복사하여 `.env` 파일 생성:

```bash
cp .env.template .env
```

`.env` 파일에서 `CAFE24_MALL_ID` 수정:
```bash
CAFE24_MALL_ID=your_actual_mall_id  # 실제 쇼핑몰 ID로 변경
CAFE24_API_BASE_URL=https://your_actual_mall_id.cafe24api.com
```

### 2. OAuth 2.0 인증 플로우

Cafe24 API는 OAuth 2.0 인증을 사용합니다.

#### Step 1: Authorization Code 요청

브라우저에서 다음 URL 접속:

```
https://your_mall_id.cafe24api.com/api/v2/oauth/authorize
  ?response_type=code
  &client_id=YOUR_CAFE24_CLIENT_ID
  &state=random_state_string
  &redirect_uri=http://localhost:8080/oauth/callback
  &scope=mall.read_product,mall.read_category
```

#### Step 2: Access Token 발급

Authorization Code를 받은 후:

```bash
curl -X POST https://your_mall_id.cafe24api.com/api/v2/oauth/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=authorization_code" \
  -d "code=YOUR_AUTHORIZATION_CODE" \
  -d "redirect_uri=http://localhost:8080/oauth/callback" \
  -d "client_id=YOUR_CAFE24_CLIENT_ID" \
  -d "client_secret=YOUR_CAFE24_CLIENT_SECRET"
```

응답:
```json
{
  "access_token": "xxxxxxxx",
  "expires_at": "2025-12-30T09:00:00.000",
  "refresh_token": "yyyyyyyy",
  "refresh_token_expires_at": "2026-12-29T09:00:00.000",
  "client_id": "YOUR_CAFE24_CLIENT_ID",
  "mall_id": "your_mall_id",
  "user_id": "admin",
  "scopes": ["mall.read_product", "mall.read_category"],
  "issued_at": "2025-12-29T09:00:00.000"
}
```

#### Step 3: Access Token 저장

발급받은 `access_token`을 `.env` 파일에 추가:

```bash
CAFE24_ACCESS_TOKEN=xxxxxxxx
CAFE24_REFRESH_TOKEN=yyyyyyyy
```

---

## 📡 주요 API 엔드포인트

### 1. 상품 목록 조회

**GET** `/api/v2/products`

```bash
curl -X GET "https://your_mall_id.cafe24api.com/api/v2/products?limit=10" \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -H "Content-Type: application/json"
```

**응답 예시**:
```json
{
  "products": [
    {
      "shop_no": 1,
      "product_no": 123,
      "product_code": "P0000ABC",
      "product_name": "나이키 에어맥스 270",
      "price": "89000",
      "retail_price": "120000",
      "supply_price": "70000",
      "display": "T",
      "selling": "T",
      "product_condition": "N",
      "stock_quantity": 50,
      "category": [
        {
          "category_no": 45,
          "category_depth": 2,
          "category_name": "운동화"
        }
      ],
      "detail_image": "https://cdn.example.com/product/123.jpg"
    }
  ]
}
```

### 2. 상품 상세 조회

**GET** `/api/v2/products/{product_no}`

```bash
curl -X GET "https://your_mall_id.cafe24api.com/api/v2/products/123" \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -H "Content-Type: application/json"
```

### 3. 카테고리 조회

**GET** `/api/v2/categories`

```bash
curl -X GET "https://your_mall_id.cafe24api.com/api/v2/categories" \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -H "Content-Type: application/json"
```

### 4. 재고 조회

**GET** `/api/v2/products/{product_no}/variants/{variant_code}/inventories`

---

## 🔄 Cafe24 → ACP 데이터 변환

### 변환 매핑 테이블

| Cafe24 필드 | ACP 필드 | 변환 로직 |
|-------------|----------|-----------|
| `product_no` | `id` | `"cafe24_" + product_no` |
| `product_name` | `title` | 그대로 사용 |
| `product_description` | `description` | HTML 태그 제거 |
| `detail_image` | `image_link` | 그대로 사용 |
| `price` | `price` | 숫자로 변환 |
| - | `currency` | 고정값 "KRW" |
| `selling == "T" && stock_quantity > 0` | `availability` | `in_stock` |
| `selling == "F"` | `availability` | `out_of_stock` |
| `product_condition` | `condition` | N→new, U→used, R→refurbished |
| `category[0].category_name` | `product_category` | 카테고리 경로 생성 |

### 변환 예시 (Kotlin)

```kotlin
fun Cafe24Product.toAcpProductFeedItem(): ProductFeedItem {
    return ProductFeedItem(
        id = "cafe24_$productNo",
        title = productName,
        description = productDescription?.stripHtml() ?: "",
        link = "https://${mallId}.cafe24.com/product/detail.html?product_no=$productNo",
        imageLink = detailImage ?: "",
        price = price.toString(),
        currency = "KRW",
        availability = when {
            selling == "T" && stockQuantity > 0 -> Availability.IN_STOCK
            selling == "F" -> Availability.OUT_OF_STOCK
            else -> Availability.PREORDER
        },
        productCategory = category.firstOrNull()?.categoryName,
        brand = brand,
        condition = when (productCondition) {
            "N" -> Condition.NEW
            "U" -> Condition.USED
            "R" -> Condition.REFURBISHED
            else -> Condition.NEW
        },
        sellerName = mallId
    )
}
```

---

## 🔧 구현 가이드

### 1. Cafe24Config 설정

```kotlin
@Configuration
class Cafe24Config(
    @field:Value("\${cafe24.api.base-url}") private val baseUrl: String,
    @field:Value("\${cafe24.access-token}") private val accessToken: String
) {
    @Bean
    fun cafe24WebClient(): WebClient {
        return WebClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("Authorization", "Bearer $accessToken")
            .defaultHeader("Content-Type", "application/json")
            .build()
    }
}
```

### 2. Cafe24ProductClient 인터페이스

```kotlin
interface Cafe24ProductClient {
    suspend fun getProducts(limit: Int = 100, offset: Int = 0): Cafe24ProductsResponse
    suspend fun getProduct(productNo: Long): Cafe24Product
    suspend fun getCategories(): Cafe24CategoriesResponse
}
```

### 3. Cafe24ProductAdapter 구현

```kotlin
@Component
class Cafe24ProductAdapter(
    private val webClient: WebClient
) : Cafe24ProductClient {
    
    override suspend fun getProducts(limit: Int, offset: Int): Cafe24ProductsResponse {
        return webClient.get()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/api/v2/products")
                    .queryParam("limit", limit)
                    .queryParam("offset", offset)
                    .build()
            }
            .retrieve()
            .awaitBody()
    }
    
    override suspend fun getProduct(productNo: Long): Cafe24Product {
        return webClient.get()
            .uri("/api/v2/products/$productNo")
            .retrieve()
            .awaitBody()
    }
}
```

### 4. ProductFeedUseCase 구현

```kotlin
@Service
class GetProductFeedUseCase(
    private val cafe24Client: Cafe24ProductClient,
    private val redisTemplate: ReactiveRedisTemplate<String, String>
) {
    suspend fun execute(query: String? = null): List<ProductFeedItem> {
        // 1. Cafe24에서 상품 조회
        val cafe24Products = cafe24Client.getProducts(limit = 100)
        
        // 2. ACP 포맷으로 변환
        val acpProducts = cafe24Products.products.map { it.toAcpProductFeedItem() }
        
        // 3. 검색 쿼리 필터링 (선택)
        return if (query != null) {
            acpProducts.filter { it.title.contains(query, ignoreCase = true) }
        } else {
            acpProducts
        }
    }
}
```

---

## 🔐 보안 고려사항

### 1. Access Token 관리

- **저장**: 데이터베이스에 암호화하여 저장
- **갱신**: Refresh Token을 사용하여 자동 갱신
- **만료 처리**: Access Token 만료 시 자동으로 재발급

```kotlin
@Service
class Cafe24TokenManager(
    private val encryptionService: EncryptionService
) {
    suspend fun refreshAccessToken(refreshToken: String): String {
        // Refresh Token으로 새 Access Token 발급
        val response = webClient.post()
            .uri("/api/v2/oauth/token")
            .bodyValue(mapOf(
                "grant_type" to "refresh_token",
                "refresh_token" to refreshToken
            ))
            .retrieve()
            .awaitBody<TokenResponse>()
        
        // 새 토큰 암호화 저장
        return encryptionService.encrypt(response.accessToken)
    }
}
```

### 2. Rate Limiting

Cafe24 API는 다음과 같은 제한이 있습니다:
- **분당 요청 수**: 2,500회
- **일일 요청 수**: 1,000,000회

```kotlin
@Component
class Cafe24RateLimiter {
    private val rateLimiter = RateLimiter.create(40.0) // 초당 40회
    
    suspend fun <T> execute(block: suspend () -> T): T {
        rateLimiter.acquire()
        return block()
    }
}
```

---

## 🧪 테스트

### 1. 통합 테스트

```kotlin
@SpringBootTest
class Cafe24ProductAdapterTest {
    
    @Autowired
    lateinit var cafe24Client: Cafe24ProductClient
    
    @Test
    fun `상품 목록 조회 테스트`() = runBlocking {
        val products = cafe24Client.getProducts(limit = 10)
        
        assertThat(products.products).isNotEmpty()
        assertThat(products.products[0].productNo).isGreaterThan(0)
    }
}
```

### 2. Mock 테스트

```kotlin
@Test
fun `Cafe24 → ACP 변환 테스트`() {
    val cafe24Product = Cafe24Product(
        productNo = 123,
        productName = "테스트 상품",
        price = "10000",
        stockQuantity = 50,
        selling = "T"
    )
    
    val acpProduct = cafe24Product.toAcpProductFeedItem()
    
    assertThat(acpProduct.id).isEqualTo("cafe24_123")
    assertThat(acpProduct.availability).isEqualTo(Availability.IN_STOCK)
}
```

---

## 📚 참고 자료

- [Cafe24 API 문서](https://developers.cafe24.com/docs/ko/api/front/)
- [OAuth 2.0 가이드](https://developers.cafe24.com/docs/ko/api/front/#oauth-20)
- [상품 API 레퍼런스](https://developers.cafe24.com/docs/ko/api/front/#products)

---

## 🐛 트러블슈팅

### 문제: "Invalid access token" 에러

**해결**: Access Token이 만료되었을 수 있습니다. Refresh Token으로 재발급하세요.

```bash
curl -X POST https://your_mall_id.cafe24api.com/api/v2/oauth/token \
  -d "grant_type=refresh_token" \
  -d "refresh_token=YOUR_REFRESH_TOKEN"
```

### 문제: Rate Limit 초과

**해결**: 요청 속도를 줄이거나 배치 처리를 사용하세요.

---

**Last Updated**: 2025-12-29
