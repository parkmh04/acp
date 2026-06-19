package com.acp.psp.adapter.outbound.kakaopay

import com.acp.psp.adapter.outbound.kakaopay.dto.*
import com.acp.psp.application.port.output.*
import com.acp.schema.payment.PaymentPrepareRequest
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

private val logger = KotlinLogging.logger {}

/**
 * 카카오페이 결제 서비스 제공자 구현체 (Outbound Adapter)
 *
 * 카카오페이 API를 사용하여 결제 준비 및 승인을 처리합니다.
 */
@Component
class KakaoPayProvider(
        private val kakaoPayWebClient: WebClient,
        @Value("\${kakaopay.cid:TC0ONETIME}") private val cid: String,
        // 결제 리다이렉트가 돌아올 Merchant 콜백 베이스 URL. 호스트/포트 변경 시 환경변수로 주입.
        @Value("\${merchant.callback-base-url:http://localhost:8080}") private val callbackBaseUrl: String
) : PaymentProvider {

    override val providerName: String = "KAKAOPAY"

    /**
     * 카카오페이 결제 준비 (Ready API)
     *
     * @see https://developers.kakaopay.com/docs/payment/online/single-payment#prepare-payment
     */
    override suspend fun prepare(request: PaymentPrepareRequest): PaymentPrepareResult {
        logger.info { "Preparing KakaoPay payment for order: ${request.merchantOrderId}" }

        val itemName =
                if (request.items.size > 1) {
                    "${request.items.first().name} 외 ${request.items.size - 1}건"
                } else {
                    request.items.firstOrNull()?.name ?: "상품"
                }
        val totalQuantity = request.items.sumOf { it.quantity }

        val body =
                KakaoPayReadyRequest(
                        cid = cid,
                        partnerOrderId = request.merchantOrderId,
                        partnerUserId = "ACP_USER", // TODO: 실제 사용자 ID 매핑
                        itemName = itemName,
                        quantity = totalQuantity,
                        totalAmount = request.amount.toInt(),
                        taxFreeAmount = 0,
                        approvalUrl = "$callbackBaseUrl/api/v1/payments/success?session_id=${request.merchantOrderId}",
                        cancelUrl = "$callbackBaseUrl/api/v1/payments/cancel?session_id=${request.merchantOrderId}",
                        failUrl = "$callbackBaseUrl/api/v1/payments/fail?session_id=${request.merchantOrderId}"
                )

        val response =
                kakaoPayWebClient
                        .post()
                        .uri("/online/v1/payment/ready")
                        .bodyValue(body)
                        .retrieve()
                        .bodyToMono<KakaoPayReadyResponse>()
                        .awaitSingle()

        logger.info { "KakaoPay payment ready: ${response.tid}" }

        return PaymentPrepareResult(pgTid = response.tid, redirectUrl = response.nextRedirectPcUrl)
    }

    /**
     * 카카오페이 결제 승인 (Approve API)
     *
     * @see https://developers.kakaopay.com/docs/payment/online/single-payment#approve-payment
     */
    override suspend fun approve(tid: String, merchantOrderId: String, pgToken: String): PaymentApproval {
        logger.info { "Approving KakaoPay payment tid: $tid, merchantOrderId: $merchantOrderId" }

        val body =
                KakaoPayApproveRequest(
                        cid = cid,
                        tid = tid,
                        partnerOrderId = merchantOrderId,
                        partnerUserId = "ACP_USER",
                        pgToken = pgToken
                )

        val response =
                kakaoPayWebClient
                        .post()
                        .uri("/online/v1/payment/approve")
                        .bodyValue(body)
                        .retrieve()
                        .bodyToMono<KakaoPayApproveResponse>()
                        .awaitSingle()

        logger.info { "KakaoPay payment approved: ${response.tid}" }

        return PaymentApproval(
                paymentId = tid, // tid를 내부 ID처럼 활용
                pgTid = response.tid,
                approvedAt = response.approvedAt,
                amount = response.amount.total.toLong(),
                cardIssuer = response.cardInfo?.issuerCorp,
                paymentMethod = response.paymentMethodType
        )
    }

    override suspend fun cancel(
            paymentId: String, // 여기서는 pgTid를 의미
            amount: Long,
            reason: String
    ): PaymentCancelResult {
        val pgTid = paymentId
        logger.info { "Canceling KakaoPay payment: $pgTid" }

        val body = KakaoPayCancelRequest(
            cid = cid,
            tid = pgTid,
            cancelAmount = amount.toInt(),
            cancelTaxFreeAmount = 0
        )

        val response = kakaoPayWebClient.post()
            .uri("/online/v1/payment/cancel")
            .bodyValue(body)
            .retrieve()
            .bodyToMono<KakaoPayCancelResponse>()
            .awaitSingle()

        return PaymentCancelResult(
            paymentId = paymentId,
            canceledAt = response.canceledAt ?: response.createdAt, // 취소 시각이 없으면 생성 시각 대체
            amount = response.approvedCancelAmount?.total?.toLong() ?: 0L,
            status = mapKakaoStatusToDomain(response.status)
        )
    }

    override suspend fun checkStatus(pgTid: String): PaymentStatusInfo {
        logger.info { "Checking KakaoPay status: $pgTid" }
        
        // GET 요청이 아님. 문서를 보면 조회 API는 POST임? 
        // 카카오페이 문서는 POST /v1/payment/order 임.
        
        val body = KakaoPayOrderRequest(cid = cid, tid = pgTid)
        
        val response = kakaoPayWebClient.post()
            .uri("/online/v1/payment/order")
            .bodyValue(body)
            .retrieve()
            .bodyToMono<KakaoPayOrderResponse>()
            .awaitSingle()
            
        return PaymentStatusInfo(
            status = mapKakaoStatusToDomain(response.status),
            amount = response.amount?.total?.toLong()
        )
    }
    
    private fun mapKakaoStatusToDomain(kakaoStatus: String): String {
        return when (kakaoStatus) {
            "READY" -> "READY"
            "SEND_TMS" -> "READY"
            "OPEN_PAYMENT" -> "IN_PROGRESS"
            "SELECT_METHOD" -> "IN_PROGRESS"
            "ARS_WAITING" -> "IN_PROGRESS"
            "AUTH_PASSWORD" -> "IN_PROGRESS"
            "ISSUED_SID" -> "IN_PROGRESS"
            "SUCCESS_PAYMENT" -> "PAID"
            "PART_CANCEL_PAYMENT" -> "PAID" // 부분 취소도 결제 완료 상태로 봄 (잔액 있음)
            "CANCEL_PAYMENT" -> "CANCELED"
            "FAIL_PAYMENT" -> "FAILED"
            "QUIT_PAYMENT" -> "CANCELED"
            else -> "UNKNOWN"
        }
    }
}