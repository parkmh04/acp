package com.acp.merchant

import com.acp.merchant.support.IntegrationTestBase
import com.acp.merchant.application.port.output.ProductPersistencePort
import com.acp.merchant.generated.jooq.tables.pojos.Products
import com.acp.merchant.generated.jooq.tables.references.*
import com.acp.schema.checkout.CreateCheckoutSessionRequest
import com.acp.schema.checkout.CheckoutItem
import com.acp.schema.checkout.Buyer
import com.acp.schema.checkout.Address
import com.acp.schema.checkout.UpdateCheckoutSessionRequest
import com.acp.merchant.application.port.output.Cafe24ProductClient
import com.acp.merchant.application.port.output.PaymentClient
import com.acp.schema.payment.PaymentPrepareResponse
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import java.math.BigDecimal

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CheckoutIntegrationTest : IntegrationTestBase() {

    @Autowired
    lateinit var webTestClient: WebTestClient

    @Autowired
    lateinit var productRepository: ProductPersistencePort
    
    @Autowired
    lateinit var dsl: DSLContext
    
    @Autowired
    lateinit var paymentClient: PaymentClient

    @TestConfiguration
    class TestConfig {
        @Bean
        @Primary
        fun mockCafe24ProductClient(): Cafe24ProductClient {
            return mockk(relaxed = true)
        }
        
        @Bean
        @Primary
        fun mockPaymentClient(): PaymentClient {
            return mockk(relaxed = true)
        }
    }

    @BeforeEach
    fun setup() {
        runBlocking {
            withContext(Dispatchers.IO) {
                dsl.deleteFrom(CHECKOUT_ITEMS).execute()
                dsl.deleteFrom(CHECKOUT_SESSIONS).execute()
                dsl.deleteFrom(ORDER_LINES).execute()
                dsl.deleteFrom(ORDERS).execute()
                dsl.deleteFrom(PRODUCT_IMAGES).execute()
            }
            productRepository.deleteAll()
            productRepository.saveAll(listOf(
                Products(
                    id = "prod_1",
                    title = "Test Product 1",
                    description = "Desc 1",
                    link = "http://example.com/1",
                    imageLink = "http://example.com/img1.jpg",
                    priceAmount = BigDecimal("10000.0000"),
                    currency = "KRW",
                    availability = "in_stock",
                    inventoryQty = 100,
                    condition = "new",
                    createdAt = java.time.OffsetDateTime.now(),
                    updatedAt = java.time.OffsetDateTime.now()
                ),
                Products(
                    id = "prod_2",
                    title = "Test Product 2",
                    description = "Desc 2",
                    link = "http://example.com/2",
                    imageLink = "http://example.com/img2.jpg",
                    priceAmount = BigDecimal("20000.0000"),
                    currency = "KRW",
                    availability = "in_stock",
                    inventoryQty = 50,
                    condition = "new",
                    createdAt = java.time.OffsetDateTime.now(),
                    updatedAt = java.time.OffsetDateTime.now()
                )
            ))
        }
    }

    @Test
    fun `create checkout session successfully`() {
        val request = CreateCheckoutSessionRequest(
            items = listOf(
                CheckoutItem(id = "prod_1", quantity = 2), // 20000
                CheckoutItem(id = "prod_2", quantity = 1)  // 20000
            ),
            buyer = Buyer("test@example.com", "Tester"),
            fulfillmentAddress = Address("KR", "12345")
        )

        webTestClient.post()
            .uri("/checkout_sessions")
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.id").isNotEmpty
            .jsonPath("$.status").isEqualTo("NOT_READY")
            .jsonPath("$.currency").isEqualTo("KRW")
            .jsonPath("$.totals[?(@.type == 'TOTAL')].amount").isEqualTo(44000)
            .jsonPath("$.totals[?(@.type == 'TAX')].amount").isEqualTo(4000)
    }

    @Test
    fun `complete checkout session calls psp`() {
        // ... (이전 코드 동일)
    }

    @Test
    fun `shipping option selection calculates total`() {
        // 1. Create Session (Total = 11000)
        val request = CreateCheckoutSessionRequest(
            items = listOf(CheckoutItem(id = "prod_1", quantity = 1)),
            buyer = Buyer("test@example.com", "Tester"),
            fulfillmentAddress = Address("KR", "12345")
        )

        val createResult = webTestClient.post()
            .uri("/checkout_sessions")
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk
            .returnResult(com.acp.schema.checkout.CheckoutSessionResponse::class.java)
            
        val sessionId = createResult.responseBody.blockFirst()!!.id

        // 2. Select Express Shipping (5000 KRW)
        val updateRequest = UpdateCheckoutSessionRequest(
            fulfillmentOptionId = "express"
        )

        webTestClient.post()
            .uri("/checkout_sessions/${sessionId}")
            .bodyValue(updateRequest)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("READY")
            .jsonPath("$.totals[?(@.type == 'FULFILLMENT')].amount").isEqualTo(5000)
            // Item(10000) + Tax(1000) + Shipping(5000) = 16000
            .jsonPath("$.totals[?(@.type == 'TOTAL')].amount").isEqualTo(16000)
    }

    @Test
    fun `available fulfillment options depend on address`() {
        // 1. Seoul address (06xxx) should have Same Day shipping
        val seoulRequest = CreateCheckoutSessionRequest(
            items = listOf(CheckoutItem(id = "prod_1", quantity = 1)),
            fulfillmentAddress = Address("KR", "06000")
        )

        webTestClient.post()
            .uri("/checkout_sessions")
            .bodyValue(seoulRequest)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.fulfillment_options[?(@.id == 'same_day')]").exists()

        // 2. Other address (99xxx) should NOT have Same Day shipping
        val otherRequest = CreateCheckoutSessionRequest(
            items = listOf(CheckoutItem(id = "prod_1", quantity = 1)),
            fulfillmentAddress = Address("KR", "99000")
        )

        webTestClient.post()
            .uri("/checkout_sessions")
            .bodyValue(otherRequest)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.fulfillment_options[?(@.id == 'same_day')]").doesNotExist()
            .jsonPath("$.fulfillment_options[?(@.id == 'standard')]").exists()
    }

    @Test
    fun `confirm payment creates order and completes session`() {
        // 1. Create Session and update to READY
        val request = CreateCheckoutSessionRequest(
            items = listOf(CheckoutItem(id = "prod_1", quantity = 1)),
            buyer = Buyer("test@example.com", "Tester"),
            fulfillmentAddress = Address("KR", "12345")
        )

        val session = webTestClient.post()
            .uri("/checkout_sessions")
            .bodyValue(request)
            .exchange()
            .returnResult(com.acp.schema.checkout.CheckoutSessionResponse::class.java)
            .responseBody.blockFirst()!!

        webTestClient.post()
            .uri("/checkout_sessions/${session.id}")
            .bodyValue(UpdateCheckoutSessionRequest(fulfillmentOptionId = "standard"))
            .exchange()
            .expectStatus().isOk

        // 2. Mock PSP Approval
        coEvery { paymentClient.approvePayment(any()) } returns com.acp.schema.payment.PaymentApproveResponse(
            paymentId = "PSP-PAY-123",
            status = "COMPLETED",
            totalAmount = 14000L, // Item 10000 + Tax 1000 + Ship 3000
            method = "CARD"
        )

        // 3. Confirm Payment (새 엔드포인트 POST /checkout_sessions/{id}/confirm 예정)
        webTestClient.post()
            .uri("/checkout_sessions/${session.id}/confirm")
            .bodyValue(mapOf("pg_token" to "DUMMY-TOKEN"))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("COMPLETED")

        // 4. Verify order in DB
        val orderRecord = dsl.selectFrom(ORDERS).fetchOne()
        assertNotNull(orderRecord)
        assertEquals("test@example.com", orderRecord?.userId)
        assertEquals(session.id, orderRecord?.id) // Session ID == Order ID
    }

    @Test
    fun `cancel completed session triggers psp cancel and updates order status`() {
        // 1. Create and Complete Session (similar to confirm payment test)
        val request = CreateCheckoutSessionRequest(
            items = listOf(CheckoutItem(id = "prod_1", quantity = 1)),
            buyer = Buyer("test@example.com", "Tester"),
            fulfillmentAddress = Address("KR", "12345")
        )

        val session = webTestClient.post()
            .uri("/checkout_sessions")
            .bodyValue(request)
            .exchange()
            .returnResult(com.acp.schema.checkout.CheckoutSessionResponse::class.java)
            .responseBody.blockFirst()!!

        webTestClient.post()
            .uri("/checkout_sessions/${session.id}")
            .bodyValue(UpdateCheckoutSessionRequest(fulfillmentOptionId = "standard"))
            .exchange()
            .expectStatus().isOk

        // Mock Approval
        coEvery { paymentClient.approvePayment(any()) } returns com.acp.schema.payment.PaymentApproveResponse(
            paymentId = "PSP-PAY-123", status = "COMPLETED", totalAmount = 14000L, method = "CARD"
        )
        
        webTestClient.post()
            .uri("/checkout_sessions/${session.id}/confirm")
            .bodyValue(mapOf("pg_token" to "DUMMY-TOKEN"))
            .exchange()
            .expectStatus().isOk

        // 2. Mock Cancel
        coEvery { paymentClient.cancelPayment(any()) } returns com.acp.schema.payment.PaymentCancelResponse(
            paymentId = "PSP-CANCEL-123",
            status = "CANCELED",
            canceledAt = "2026-01-05T12:00:00",
            canceledAmount = 14000L
        )

        // 3. Request Cancel
        webTestClient.post()
            .uri("/checkout_sessions/${session.id}/cancel")
            .bodyValue(mapOf("reason" to "Changed mind"))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("CANCELED")

        // 4. Verify Order Status Updated
        val orderRecord = dsl.selectFrom(ORDERS)
            .where(ORDERS.ID.eq(session.id))
            .fetchOne()
        
        assertNotNull(orderRecord)
        assertEquals("CANCELED", orderRecord?.status)
    }

    @Test
    fun `create session with invalid address fails`() {
        val request = CreateCheckoutSessionRequest(
            items = listOf(CheckoutItem(id = "prod_1", quantity = 1)),
            fulfillmentAddress = Address("KR", "123") // Invalid postal code
        )

        webTestClient.post()
            .uri("/checkout_sessions")
            .bodyValue(request)
            .exchange()
            // 잘못된 주소/입력은 GlobalExceptionHandler가 400 Bad Request로 매핑한다.
            .expectStatus().isBadRequest
    }
}
