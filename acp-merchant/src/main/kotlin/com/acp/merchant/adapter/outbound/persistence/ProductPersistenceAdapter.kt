package com.acp.merchant.adapter.outbound.persistence

import com.acp.merchant.application.port.output.ProductPersistencePort
import com.acp.merchant.generated.jooq.tables.pojos.Products
import com.acp.merchant.generated.jooq.tables.references.PRODUCTS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

/** 상품 영속성 어댑터 (Outbound Adapter) */
@Repository
class ProductPersistenceAdapter(private val dsl: DSLContext) : ProductPersistencePort {

    override suspend fun findAll(): List<Products> =
            withContext(Dispatchers.IO) { dsl.selectFrom(PRODUCTS).fetchInto(Products::class.java) }

    override suspend fun findById(id: String): Products? =
            withContext(Dispatchers.IO) {
                dsl.selectFrom(PRODUCTS)
                   .where(PRODUCTS.ID.eq(id))
                   .fetchOneInto(Products::class.java)
            }

    override suspend fun saveAll(products: List<Products>): Unit =
            withContext(Dispatchers.IO) {
                val records = products.map { product -> dsl.newRecord(PRODUCTS, product) }
                dsl.batchInsert(records).execute()
                Unit
            }

    override suspend fun deleteAll(): Unit =
            withContext(Dispatchers.IO) {
                dsl.deleteFrom(PRODUCTS).execute()
                Unit
            }

    override suspend fun decreaseStock(productId: String, quantity: Int): Boolean =
            withContext(Dispatchers.IO) {
                // 가용 재고가 충분할 때만 차감 (동시 주문 오버셀 방지)
                val updated =
                        dsl.update(PRODUCTS)
                                .set(PRODUCTS.INVENTORY_QTY, PRODUCTS.INVENTORY_QTY.minus(quantity))
                                .where(PRODUCTS.ID.eq(productId))
                                .and(PRODUCTS.INVENTORY_QTY.ge(quantity))
                                .execute()
                updated > 0
            }
}
