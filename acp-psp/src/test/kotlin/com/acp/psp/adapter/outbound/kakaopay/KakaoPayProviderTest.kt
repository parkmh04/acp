package com.acp.psp.adapter.outbound.kakaopay

import com.acp.psp.adapter.outbound.kakaopay.dto.KakaoPayReadyResponse
import com.acp.schema.payment.PaymentItem
import com.acp.schema.payment.PaymentPrepareRequest
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

/** KakaoPayProvider 단위 테스트 */
class KakaoPayProviderTest {

    private val webClient = mockk<WebClient>()
    private val provider =
            KakaoPayProvider(
                    webClient,
                    cid = "TC0ONETIME",
                    callbackBaseUrl = "http://localhost:8080"
            )

    @Test
    @DisplayName("카카오페이 결제 준비 API 호출이 정상적으로 변환되어야 한다")
    fun `prepare calls KakaoPay Ready API and returns result`() = runTest {
        // Given
        val request =
                PaymentPrepareRequest(
                        merchantOrderId = "ORDER-123",
                        amount = 10000L,
                        items = listOf(PaymentItem("테스트 상품", 2, 5000L))
                )

        val mockApiResponse =
                KakaoPayReadyResponse(
                        tid = "T123456789",
                        nextRedirectPcUrl = "https://kakaopay.com/pay/pc",
                        nextRedirectMobileUrl = "https://kakaopay.com/pay/mobile",
                        nextRedirectAppUrl = "https://kakaopay.com/pay/app",
                        createdAt = "2025-12-29T22:00:00"
                )

        // WebClient 체이닝 모킹
        val requestBodyUriSpec = mockk<WebClient.RequestBodyUriSpec>()
        val requestBodySpec = mockk<WebClient.RequestBodySpec>()
        val responseSpec = mockk<WebClient.ResponseSpec>()

        every { webClient.post() } returns requestBodyUriSpec
        every { requestBodyUriSpec.uri(any<String>()) } returns requestBodySpec
        every { requestBodySpec.bodyValue(any()) } returns requestBodySpec
        every { requestBodySpec.retrieve() } returns responseSpec

        // bodyToMono (ParameterizedTypeReference 또는 클래스 타입) 모킹
        every { responseSpec.bodyToMono(any<Class<KakaoPayReadyResponse>>()) } returns
                Mono.just(mockApiResponse)
        // 만약 reified inline 함수가 호출된다면 internal 동작에 맞춰 any() 처리
        every {
            responseSpec.bodyToMono(
                    any<
                            org.springframework.core.ParameterizedTypeReference<
                                    KakaoPayReadyResponse>>()
            )
        } returns Mono.just(mockApiResponse)

        // When
        val result = provider.prepare(request)

        // Then
        assertEquals("T123456789", result.pgTid)
        assertEquals("https://kakaopay.com/pay/pc", result.redirectUrl)
        assertEquals("READY", result.status)
    }
}
