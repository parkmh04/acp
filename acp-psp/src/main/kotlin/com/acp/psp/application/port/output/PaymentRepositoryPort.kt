package com.acp.psp.application.port.output

import com.acp.psp.generated.jooq.tables.pojos.Payments

/** 결제 영속성 포트 (Output Port) */
interface PaymentRepositoryPort {
    suspend fun findLastByMerchantOrderIdAndType(merchantOrderId: String, type: String): Payments?
    suspend fun save(payment: Payments)
}
