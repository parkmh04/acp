package com.acp.merchant.adapter.outbound.web

import com.acp.merchant.application.port.output.PaymentClient
import com.acp.merchant.config.timeoutConnector
import com.acp.schema.payment.PaymentPrepareRequest
import com.acp.schema.payment.PaymentPrepareResponse
import com.acp.schema.payment.PaymentApproveRequest
import com.acp.schema.payment.PaymentApproveResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody

@Component
class PaymentClientAdapter(
    private val webClientBuilder: WebClient.Builder,
    @Value("\${psp.base-url}") private val pspUrl: String
) : PaymentClient {

    private val webClient: WebClient by lazy {
        webClientBuilder.baseUrl(pspUrl).clientConnector(timeoutConnector()).build()
    }

    override suspend fun preparePayment(request: PaymentPrepareRequest): PaymentPrepareResponse {
        return webClient.post()
            .uri("/api/v1/payments/prepare")
            .bodyValue(request)
            .retrieve()
            .awaitBody()
    }

    override suspend fun approvePayment(request: PaymentApproveRequest): PaymentApproveResponse {
        return webClient.post()
            .uri("/api/v1/payments/approve")
            .bodyValue(request)
            .retrieve()
            .awaitBody()
    }

    override suspend fun cancelPayment(request: com.acp.schema.payment.PaymentCancelRequest): com.acp.schema.payment.PaymentCancelResponse {
        return webClient.post()
            .uri("/api/v1/payments/cancel")
            .bodyValue(request)
            .retrieve()
            .awaitBody()
    }
}
