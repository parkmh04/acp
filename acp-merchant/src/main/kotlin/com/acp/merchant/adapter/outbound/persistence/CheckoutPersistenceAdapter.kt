package com.acp.merchant.adapter.outbound.persistence

import com.acp.merchant.application.port.output.CheckoutRepositoryPort
import com.acp.merchant.domain.model.*
import com.acp.merchant.generated.jooq.tables.references.CHECKOUT_ITEMS
import com.acp.merchant.generated.jooq.tables.references.CHECKOUT_SESSIONS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.math.BigDecimal

@Repository
class CheckoutPersistenceAdapter(
    private val dsl: DSLContext
) : CheckoutRepositoryPort {

    override suspend fun save(checkoutSession: CheckoutSession): CheckoutSession = withContext(Dispatchers.IO) {
            // 1. Save Session
            dsl.insertInto(CHECKOUT_SESSIONS)
                .set(CHECKOUT_SESSIONS.ID, checkoutSession.id)
                .set(CHECKOUT_SESSIONS.STATUS, checkoutSession.status.name)
                .set(CHECKOUT_SESSIONS.CURRENCY, checkoutSession.currency)
                .set(CHECKOUT_SESSIONS.TOTAL_AMOUNT, checkoutSession.totals.total)
                .set(CHECKOUT_SESSIONS.BUYER_EMAIL, checkoutSession.buyer?.email)
                .set(CHECKOUT_SESSIONS.BUYER_NAME, checkoutSession.buyer?.name)
                .set(CHECKOUT_SESSIONS.SHIPPING_ADDRESS_COUNTRY, checkoutSession.shippingAddress?.countryCode)
                .set(CHECKOUT_SESSIONS.SHIPPING_ADDRESS_POSTAL_CODE, checkoutSession.shippingAddress?.postalCode)
                .set(CHECKOUT_SESSIONS.SELECTED_FULFILLMENT_OPTION, checkoutSession.selectedFulfillmentOption)
                .set(CHECKOUT_SESSIONS.SHIPPING_COST, checkoutSession.totals.shipping)
                .set(CHECKOUT_SESSIONS.NEXT_ACTION_URL, checkoutSession.nextActionUrl)
                .set(CHECKOUT_SESSIONS.CREATED_AT, checkoutSession.createdAt.toOffsetDateTime())
                .set(CHECKOUT_SESSIONS.UPDATED_AT, java.time.OffsetDateTime.now())
                .set(CHECKOUT_SESSIONS.EXPIRES_AT, checkoutSession.expiresAt?.toOffsetDateTime())
                .onDuplicateKeyUpdate()
                .set(CHECKOUT_SESSIONS.STATUS, checkoutSession.status.name)
                .set(CHECKOUT_SESSIONS.TOTAL_AMOUNT, checkoutSession.totals.total)
                .set(CHECKOUT_SESSIONS.BUYER_EMAIL, checkoutSession.buyer?.email)
                .set(CHECKOUT_SESSIONS.BUYER_NAME, checkoutSession.buyer?.name)
                .set(CHECKOUT_SESSIONS.SHIPPING_ADDRESS_COUNTRY, checkoutSession.shippingAddress?.countryCode)
                .set(CHECKOUT_SESSIONS.SHIPPING_ADDRESS_POSTAL_CODE, checkoutSession.shippingAddress?.postalCode)
                .set(CHECKOUT_SESSIONS.SELECTED_FULFILLMENT_OPTION, checkoutSession.selectedFulfillmentOption)
                .set(CHECKOUT_SESSIONS.SHIPPING_COST, checkoutSession.totals.shipping)
                .set(CHECKOUT_SESSIONS.NEXT_ACTION_URL, checkoutSession.nextActionUrl)
                .set(CHECKOUT_SESSIONS.UPDATED_AT, java.time.OffsetDateTime.now())
                .execute()

            // 2. Delete existing items
            dsl.deleteFrom(CHECKOUT_ITEMS)
                .where(CHECKOUT_ITEMS.CHECKOUT_SESSION_ID.eq(checkoutSession.id))
                .execute()

            // 3. Insert new items
            if (checkoutSession.items.isNotEmpty()) {
                val insert = dsl.insertInto(CHECKOUT_ITEMS,
                    CHECKOUT_ITEMS.CHECKOUT_SESSION_ID,
                    CHECKOUT_ITEMS.PRODUCT_ID,
                    CHECKOUT_ITEMS.QUANTITY,
                    CHECKOUT_ITEMS.UNIT_PRICE,
                    CHECKOUT_ITEMS.TOTAL_PRICE
                )
                
                checkoutSession.items.forEach { item ->
                    insert.values(
                        checkoutSession.id,
                        item.productId,
                        item.quantity,
                        item.unitPrice,
                        item.totalPrice
                    )
                }
                insert.execute()
            }
            
            return@withContext checkoutSession
    }

    override suspend fun findById(id: String): CheckoutSession? = withContext(Dispatchers.IO) {
        val record = dsl.selectFrom(CHECKOUT_SESSIONS)
            .where(CHECKOUT_SESSIONS.ID.eq(id))
            .fetchOne() ?: return@withContext null

        val items = dsl.selectFrom(CHECKOUT_ITEMS)
            .where(CHECKOUT_ITEMS.CHECKOUT_SESSION_ID.eq(id))
            .fetch()
            .map { itemRecord ->
                CheckoutItem(
                    productId = itemRecord.productId!!,
                    quantity = itemRecord.quantity!!,
                    unitPrice = itemRecord.unitPrice!!,
                    totalPrice = itemRecord.totalPrice!!
                )
            }
            
        val itemsBaseAmount = items.fold(BigDecimal.ZERO) { acc, i -> acc.add(i.totalPrice) }
        val shippingCost = record.shippingCost ?: BigDecimal.ZERO
        val total = record.totalAmount!!
        // 세션 테이블은 tax를 별도 컬럼으로 저장하지 않으므로, 저장된 합계에서 역산한다.
        // total = subtotal + shipping + tax  →  tax = total - subtotal - shipping
        val tax = (total.subtract(itemsBaseAmount).subtract(shippingCost)).max(BigDecimal.ZERO)

        return@withContext CheckoutSession(
            id = record.id!!,
            status = CheckoutStatus.valueOf(record.status!!),
            currency = record.currency!!,
            items = items,
            buyer = if (record.buyerEmail != null || record.buyerName != null) Buyer(record.buyerEmail, record.buyerName) else null,
            shippingAddress = if (record.shippingAddressCountry != null) Address(record.shippingAddressCountry!!, record.shippingAddressPostalCode) else null,
            selectedFulfillmentOption = record.selectedFulfillmentOption,
            totals = Totals(
                 itemsBaseAmount = itemsBaseAmount,
                 itemsDiscount = BigDecimal.ZERO,
                 subtotal = itemsBaseAmount,
                 tax = tax,
                 shipping = shippingCost,
                 total = total
            ),
            nextActionUrl = record.nextActionUrl,
            createdAt = record.createdAt!!.toZonedDateTime(),
            expiresAt = record.expiresAt?.toZonedDateTime()
        )
    }
}
