package com.acp.merchant.adapter.outbound.persistence

import com.acp.merchant.application.port.output.OrderRepositoryPort
import com.acp.merchant.domain.model.Order
import com.acp.merchant.domain.model.OrderLineItem
import com.acp.merchant.domain.model.OrderStatus
import com.acp.merchant.generated.jooq.tables.references.ORDERS
import com.acp.merchant.generated.jooq.tables.references.ORDER_LINES
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class OrderPersistenceAdapter(
    private val dsl: DSLContext
) : OrderRepositoryPort {

    override suspend fun save(order: Order): Order = withContext(Dispatchers.IO) {
            // 1. Save Order
            dsl.insertInto(ORDERS)
                .set(ORDERS.ID, order.id)
                .set(ORDERS.USER_ID, order.userId)
                .set(ORDERS.STATUS, order.status.name)
                .set(ORDERS.TOTAL_AMOUNT, order.totalAmount)
                .set(ORDERS.CURRENCY, order.currency)
                .set(ORDERS.PAYMENT_REQUEST_ID, order.paymentRequestIds)
                .set(ORDERS.CREATED_AT, order.createdAt.toOffsetDateTime())
                .set(ORDERS.UPDATED_AT, java.time.OffsetDateTime.now())
                .onDuplicateKeyUpdate()
                .set(ORDERS.STATUS, order.status.name)
                .set(ORDERS.UPDATED_AT, java.time.OffsetDateTime.now())
                .execute()

            // 2. Delete existing lines (simple update strategy)
            dsl.deleteFrom(ORDER_LINES)
                .where(ORDER_LINES.ORDER_ID.eq(order.id))
                .execute()

            // 3. Insert new items
            if (order.items.isNotEmpty()) {
                val insert = dsl.insertInto(ORDER_LINES,
                    ORDER_LINES.ID,
                    ORDER_LINES.ORDER_ID,
                    ORDER_LINES.PRODUCT_ID,
                    ORDER_LINES.QUANTITY,
                    ORDER_LINES.UNIT_PRICE,
                    ORDER_LINES.TOTAL_PRICE,
                    ORDER_LINES.CURRENCY
                )
                
                order.items.forEach { item ->
                    insert.values(
                        UUID.randomUUID().toString(),
                        order.id,
                        item.productId,
                        item.quantity,
                        item.unitPrice,
                        item.totalPrice,
                        order.currency
                    )
                }
                insert.execute()
            }
            
            return@withContext order
    }

    override suspend fun findById(id: String): Order? = withContext(Dispatchers.IO) {
        val record = dsl.selectFrom(ORDERS)
            .where(ORDERS.ID.eq(id))
            .fetchOne() ?: return@withContext null

        val items = dsl.selectFrom(ORDER_LINES)
            .where(ORDER_LINES.ORDER_ID.eq(id))
            .fetch()
            .map { 
                OrderLineItem(
                    productId = it.productId!!,
                    productName = "Unknown", // TODO: Join with products table if needed
                    quantity = it.quantity!!,
                    unitPrice = it.unitPrice!!,
                    totalPrice = it.totalPrice!!
                )
            }

        return@withContext Order(
            id = record.id!!,
            userId = record.userId!!,
            status = OrderStatus.valueOf(record.status!!),
            totalAmount = record.totalAmount!!,
            currency = record.currency!!,
            paymentRequestIds = record.paymentRequestId,
            items = items,
            createdAt = record.createdAt!!.toZonedDateTime()
        )
    }
}
