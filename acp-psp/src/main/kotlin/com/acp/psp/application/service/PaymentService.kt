package com.acp.psp.application.service

import com.acp.psp.application.port.input.PaymentUseCase
import com.acp.psp.application.port.output.EncryptionPort
import com.acp.psp.application.port.output.PaymentProvider
import com.acp.psp.application.port.output.PaymentRepositoryPort
import com.acp.psp.generated.jooq.tables.pojos.Payments
import com.acp.schema.payment.*
import io.github.oshai.kotlinlogging.KotlinLogging
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.reactive.function.client.WebClientRequestException

private val logger = KotlinLogging.logger {}

/** 결제 처리 서비스 (Application Service) */
@Service
class PaymentService(
        private val paymentRepositoryPort: PaymentRepositoryPort,
        private val paymentProvider: PaymentProvider,
        private val encryptionPort: EncryptionPort
) : PaymentUseCase {

        override suspend fun preparePayment(
                request: PaymentPrepareRequest
        ): PaymentPrepareResponse {
                // 1. 멱등성 체크 (기존 주문번호로 생성된 PREPARE 결제가 있는지 확인)
                val existingRecord =
                        paymentRepositoryPort.findLastByMerchantOrderIdAndType(request.merchantOrderId, "PREPARE")

                if (existingRecord != null) {
                        val decryptedTid = existingRecord.pgTid?.let { encryptionPort.decrypt(it) }
                        return PaymentPrepareResponse(
                                paymentId = existingRecord.id!!,
                                merchantOrderId = existingRecord.merchantOrderId!!,
                                redirectUrl =
                                        "https://mock-kakaopay.com/pay/$decryptedTid", // TODO: 실제 저장된 URL 사용 고려
                                status = existingRecord.status!!
                        )
                }

                // 2. 외부 PG사(카카오페이 등) 결제 준비 요청
                val prepareResult = paymentProvider.prepare(request)

                // 3. 결제 정보 생성 및 DB 저장 (불변: INSERT)
                val paymentId = UUID.randomUUID().toString()
                val payment = Payments(
                    id = paymentId,
                    merchantOrderId = request.merchantOrderId,
                    orgPaymentId = null,
                    type = "PREPARE",
                    status = "READY",
                    amount = request.amount,
                    currency = request.currency,
                    pgProvider = paymentProvider.providerName,
                    pgTid = encryptionPort.encrypt(prepareResult.pgTid), // 암호화 저장
                    createdAt = OffsetDateTime.now()
                )

                try {
                        paymentRepositoryPort.save(payment)
                } catch (e: Exception) {
                        // 동시 요청 레이스: merchant_order_id 당 PREPARE 1건만 허용하는
                        // 부분 unique 제약(uq_payments_prepare_per_order) 위반 가능.
                        // 이미 생성된 PREPARE 가 있으면 그 결과를 멱등 반환한다.
                        val winner =
                                paymentRepositoryPort.findLastByMerchantOrderIdAndType(
                                        request.merchantOrderId, "PREPARE")
                                        ?: throw e
                        logger.warn {
                                "Concurrent PREPARE detected for ${request.merchantOrderId}, returning existing ${winner.id}"
                        }
                        val winnerTid = winner.pgTid?.let { encryptionPort.decrypt(it) }
                        return PaymentPrepareResponse(
                                paymentId = winner.id!!,
                                merchantOrderId = winner.merchantOrderId!!,
                                redirectUrl = "https://mock-kakaopay.com/pay/$winnerTid",
                                status = winner.status!!
                        )
                }

                // 4. 최종 응답 반환
                return PaymentPrepareResponse(
                        paymentId = paymentId,
                        merchantOrderId = request.merchantOrderId,
                        redirectUrl = prepareResult.redirectUrl,
                        status = "READY"
                )
        }

        override suspend fun approvePayment(request: PaymentApproveRequest): PaymentApproveResponse {
                // 1. 원본 결제(PREPARE) 조회
                val prepareRecord = paymentRepositoryPort.findLastByMerchantOrderIdAndType(request.merchantOrderId, "PREPARE")
                    ?: throw IllegalArgumentException("Payment PREPARE record not found for order: ${request.merchantOrderId}")

                // 2. 이미 승인된 건인지 확인 (APPROVE 레코드 존재 여부)
                val existingApprove = paymentRepositoryPort.findLastByMerchantOrderIdAndType(request.merchantOrderId, "APPROVE")
                if (existingApprove != null && existingApprove.status == "SUCCESS") {
                    logger.info { "Payment already approved: ${existingApprove.id}" }
                    return PaymentApproveResponse(
                        paymentId = existingApprove.id!!,
                        status = "COMPLETED",
                        totalAmount = existingApprove.amount!!.toLong()
                    )
                }

                try {
                    val decryptedTid = prepareRecord.pgTid?.let { encryptionPort.decrypt(it) }
                        ?: throw IllegalStateException("PG TID missing in prepare record")
                    
                    val approval = paymentProvider.approve(decryptedTid, request.merchantOrderId, request.pgToken)
                    
                    // 3. 승인 정보 저장 (불변: INSERT)
                    val approveId = UUID.randomUUID().toString()
                    val approvePayment = Payments(
                        id = approveId,
                        merchantOrderId = request.merchantOrderId,
                        orgPaymentId = prepareRecord.id, // 원본(PREPARE) 참조
                        type = "APPROVE",
                        status = "SUCCESS",
                        amount = approval.amount,
                        currency = prepareRecord.currency,
                        pgProvider = paymentProvider.providerName,
                        pgTid = encryptionPort.encrypt(approval.pgTid), // 암호화 저장
                        paymentMethodType = approval.paymentMethod,
                        cardIssuer = approval.cardIssuer,
                        createdAt = OffsetDateTime.now()
                    )
                    paymentRepositoryPort.save(approvePayment)
                    
                    return PaymentApproveResponse(
                        paymentId = approveId,
                        status = "COMPLETED",
                        approvedAt = approval.approvedAt,
                        totalAmount = approval.amount,
                        method = approval.paymentMethod,
                        cardInfo = approval.cardIssuer?.let { CardInfo(it) }
                    )

                } catch (e: Exception) {
                    logger.error(e) { "Payment approval failed" }
                    
                    // 망취소 로직 (Network Cancellation)
                    if (isNetworkError(e)) {
                        handleNetCancel(prepareRecord)
                    }
                    // 실패 기록 저장
                    val failId = UUID.randomUUID().toString()
                    val failPayment = Payments(
                        id = failId,
                        merchantOrderId = request.merchantOrderId,
                        orgPaymentId = prepareRecord.id,
                        type = "APPROVE",
                        status = "FAIL",
                        amount = prepareRecord.amount,
                        currency = prepareRecord.currency,
                        pgProvider = paymentProvider.providerName,
                        pgTid = prepareRecord.pgTid, // 이미 암호화된 상태일 것임
                        createdAt = OffsetDateTime.now()
                    )
                    paymentRepositoryPort.save(failPayment)
                    
                    throw e
                }
        }

        override suspend fun cancelPayment(request: PaymentCancelRequest): PaymentCancelResponse {
            // 1. 성공한 결제(APPROVE, SUCCESS) 조회
            val approvedRecord = paymentRepositoryPort.findLastByMerchantOrderIdAndType(request.merchantOrderId, "APPROVE")
                ?: throw IllegalArgumentException("No successful payment found for order: ${request.merchantOrderId}")
            
            if (approvedRecord.status != "SUCCESS") {
                 throw IllegalStateException("Payment is not in SUCCESS state: ${approvedRecord.status}")
            }
            
            // 2. 이미 취소된 건인지 확인
            val existingCancel = paymentRepositoryPort.findLastByMerchantOrderIdAndType(request.merchantOrderId, "CANCEL")
            if (existingCancel != null && existingCancel.status == "SUCCESS") {
                logger.info { "Payment already canceled: ${existingCancel.id}" }
                return PaymentCancelResponse(
                    paymentId = existingCancel.id!!,
                    status = "CANCELED",
                    canceledAt = existingCancel.createdAt!!.toString(),
                    canceledAmount = existingCancel.amount!!.toLong()
                )
            }

            val decryptedTid = approvedRecord.pgTid?.let { encryptionPort.decrypt(it) }
                 ?: throw IllegalStateException("PG TID missing in approved record")

            // 3. 외부 PG사 취소 요청
            val cancelResult = paymentProvider.cancel(
                decryptedTid,
                request.amount,
                request.reason
            )

            // 4. 취소 정보 저장
            val cancelId = UUID.randomUUID().toString()
            val cancelPayment = Payments(
                id = cancelId,
                merchantOrderId = request.merchantOrderId,
                orgPaymentId = approvedRecord.id,
                type = "CANCEL",
                status = cancelResult.status,
                amount = cancelResult.amount,
                currency = approvedRecord.currency,
                pgProvider = paymentProvider.providerName,
                pgTid = approvedRecord.pgTid, // 암호화된 상태 유지
                createdAt = OffsetDateTime.now()
            )
            paymentRepositoryPort.save(cancelPayment)

            return PaymentCancelResponse(
                paymentId = cancelId,
                status = cancelResult.status,
                canceledAt = cancelResult.canceledAt,
                canceledAmount = cancelResult.amount
            )
        }

        private suspend fun handleNetCancel(prepareRecord: Payments) {
            val decryptedTid = prepareRecord.pgTid?.let { encryptionPort.decrypt(it) }
                ?: return

            logger.warn { "Executing Net Cancel for payment: $decryptedTid" }
            try {
                val statusInfo = paymentProvider.checkStatus(decryptedTid)
                if (statusInfo.status == "PAID") {
                    logger.info { "Payment was PAID at PG, canceling now..." }
                    paymentProvider.cancel(
                        decryptedTid, 
                        prepareRecord.amount!!, 
                        "Net Cancel due to system timeout"
                    )
                    
                    // 취소 이력 저장
                    val cancelId = UUID.randomUUID().toString()
                    val cancelPayment = Payments(
                        id = cancelId,
                        merchantOrderId = prepareRecord.merchantOrderId,
                        orgPaymentId = prepareRecord.id,
                        type = "CANCEL",
                        status = "SUCCESS",
                        amount = prepareRecord.amount,
                        currency = prepareRecord.currency,
                        pgProvider = paymentProvider.providerName,
                        pgTid = prepareRecord.pgTid, // 암호화된 상태 유지
                        createdAt = OffsetDateTime.now()
                    )
                    paymentRepositoryPort.save(cancelPayment)
                    
                } else {
                    logger.info { "Payment status at PG is ${statusInfo.status}, no cancel needed" }
                }
            } catch (e: Exception) {
                logger.error(e) { "Net Cancel failed!" }
            }
        }

        private fun isNetworkError(e: Exception): Boolean {
            return e is WebClientRequestException || 
                   e.cause is java.net.SocketTimeoutException ||
                   e.message?.contains("timeout", ignoreCase = true) == true
        }
}
