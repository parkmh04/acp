package com.acp.merchant.application.port.output

import com.acp.merchant.generated.jooq.tables.pojos.Products

/** 상품 영속성 포트 (Output Port) */
interface ProductPersistencePort {
    suspend fun findAll(): List<Products>
    suspend fun findById(id: String): Products?
    suspend fun saveAll(products: List<Products>)
    suspend fun deleteAll()

    /**
     * 재고를 원자적으로 차감한다. 가용 재고가 충분할 때만(>= quantity) 차감되며,
     * 차감 성공 시 true, 재고 부족/상품 없음으로 차감되지 않으면 false.
     */
    suspend fun decreaseStock(productId: String, quantity: Int): Boolean
}
