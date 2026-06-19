package com.acp.psp.adapter.outbound.persistence

import com.acp.psp.application.port.output.PaymentRepositoryPort
import com.acp.psp.generated.jooq.tables.pojos.Payments
import com.acp.psp.generated.jooq.tables.references.PAYMENTS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

/** 결제 영속성 어댑터 (Outbound Adapter) */
@Repository
class PaymentPersistenceAdapter(private val dsl: DSLContext) : PaymentRepositoryPort {

    override suspend fun findLastByMerchantOrderIdAndType(merchantOrderId: String, type: String): Payments? =
            withContext(Dispatchers.IO) {
                dsl.selectFrom(PAYMENTS)
                        .where(PAYMENTS.MERCHANT_ORDER_ID.eq(merchantOrderId))
                        .and(PAYMENTS.TYPE.eq(type))
                        .orderBy(PAYMENTS.CREATED_AT.desc())
                        .limit(1)
                        .fetchOneInto(Payments::class.java)
            }

    override suspend fun save(payment: Payments): Unit =
            withContext(Dispatchers.IO) {
                dsl.transaction { configuration ->
                    val txDsl = configuration.dsl()
                    val record = txDsl.newRecord(PAYMENTS, payment)
                    txDsl.insertInto(PAYMENTS).set(record).execute()
                }
                Unit
            }
}
