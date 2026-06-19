package com.acp.merchant.application.service

import com.acp.merchant.application.port.input.GetProductFeedUseCase
import com.acp.merchant.application.port.output.Cafe24ProductClient
import com.acp.merchant.application.port.output.ProductPersistencePort
import com.acp.merchant.domain.service.Cafe24ToAcpConverter
import com.acp.merchant.generated.jooq.tables.pojos.Products
import com.acp.schema.feed.Availability
import com.acp.schema.feed.Condition
import com.acp.schema.feed.ProductFeedItem
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

/**
 * 상품 피드 조회 Use Case 구현체
 *
 * L1 (Caffeine) -> L2 (Redis) -> Source 계층형 캐싱 적용.
 *
 * Source 전략: Cafe24 API를 우선 조회하되, Cafe24 미설정/빈 응답 시 로컬 상품 저장소(DB)로 폴백한다.
 * 따라서 Cafe24 자격증명이 없는 로컬/포크 환경에서도 시드 데이터로 피드가 동작한다.
 */
@Service
class ProductFeedService(
        private val cafe24ProductClient: Cafe24ProductClient,
        private val cafe24ToAcpConverter: Cafe24ToAcpConverter,
        private val productPersistencePort: ProductPersistencePort,
        private val redisTemplate: StringRedisTemplate,
        private val objectMapper: ObjectMapper
) : GetProductFeedUseCase {

    // L1 Cache: 1분 TTL, 최대 1000개 항목
    private val localCache: Cache<String, List<ProductFeedItem>> = Caffeine.newBuilder()
        .expireAfterWrite(1, TimeUnit.MINUTES)
        .maximumSize(1000)
        .build()

    private val REDIS_TTL = Duration.ofMinutes(30)

    override suspend fun execute(limit: Int, offset: Int): List<ProductFeedItem> {
        val cacheKey = "feed:all:$limit:$offset"
        return getCachedOrFetch(cacheKey) {
            val fromCafe24 = fetchFromCafe24(limit, offset)
            fromCafe24.ifEmpty { fetchFromDb(limit, offset) }
        }
    }

    override suspend fun search(keyword: String, limit: Int, offset: Int): List<ProductFeedItem> {
        val cacheKey = "feed:search:$keyword:$limit:$offset"
        return getCachedOrFetch(cacheKey) {
            val fromCafe24 = searchFromCafe24(keyword, limit, offset)
            fromCafe24.ifEmpty { searchFromDb(keyword, limit, offset) }
        }
    }

    private suspend fun getCachedOrFetch(
        key: String, 
        fetcher: suspend () -> List<ProductFeedItem>
    ): List<ProductFeedItem> {
        // 1. L1 Cache (Caffeine)
        localCache.getIfPresent(key)?.let { 
            logger.debug { "L1 Cache hit: $key" }
            return it 
        }

        // 2. L2 Cache (Redis)
        try {
            val cachedJson = redisTemplate.opsForValue().get(key)
            if (cachedJson != null) {
                logger.debug { "L2 Cache hit: $key" }
                val items = objectMapper.readValue(cachedJson, object : TypeReference<List<ProductFeedItem>>() {})
                localCache.put(key, items) // Update L1
                return items
            }
        } catch (e: Exception) {
            logger.warn(e) { "L2 Cache failed: $key" }
        }

        // 3. Source (Cafe24)
        logger.info { "Cache miss: $key. Fetching from Source..." }
        val items = fetcher()

        if (items.isNotEmpty()) {
            // Update Caches
            localCache.put(key, items)
            try {
                val json = objectMapper.writeValueAsString(items)
                redisTemplate.opsForValue().set(key, json, REDIS_TTL)
            } catch (e: Exception) {
                logger.warn(e) { "Failed to update L2 Cache: $key" }
            }
        }

        return items
    }

    private suspend fun fetchFromCafe24(limit: Int, offset: Int): List<ProductFeedItem> {
        return try {
            val cafe24Response = cafe24ProductClient.getProducts(limit, offset, "T", "T")
            cafe24ToAcpConverter.convertAll(cafe24Response.products)
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch products" }
            emptyList()
        }
    }

    private suspend fun searchFromCafe24(keyword: String, limit: Int, offset: Int): List<ProductFeedItem> {
        return try {
            val cafe24Response = cafe24ProductClient.searchProducts(keyword, limit, offset)
            cafe24ToAcpConverter.convertAll(cafe24Response.products)
        } catch (e: Exception) {
            logger.error(e) { "Failed to search products" }
            emptyList()
        }
    }

    /** 로컬 DB(시드 데이터) 폴백 - Cafe24 미설정 환경에서 피드 제공 */
    private suspend fun fetchFromDb(limit: Int, offset: Int): List<ProductFeedItem> {
        return try {
            productPersistencePort.findAll()
                    .asSequence()
                    .drop(offset.coerceAtLeast(0))
                    .take(limit.coerceAtLeast(0))
                    .map { it.toFeedItem() }
                    .toList()
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch products from DB" }
            emptyList()
        }
    }

    private suspend fun searchFromDb(keyword: String, limit: Int, offset: Int): List<ProductFeedItem> {
        return fetchFromDb(Int.MAX_VALUE, 0)
                .asSequence()
                .filter {
                    it.title.contains(keyword, ignoreCase = true) ||
                            it.description.contains(keyword, ignoreCase = true)
                }
                .drop(offset.coerceAtLeast(0))
                .take(limit.coerceAtLeast(0))
                .toList()
    }

    private fun Products.toFeedItem(): ProductFeedItem =
            ProductFeedItem(
                    id = id ?: "",
                    title = title ?: "",
                    description = description ?: "",
                    link = link ?: "",
                    imageLink = imageLink ?: "",
                    price = priceAmount?.stripTrailingZeros()?.toPlainString() ?: "0",
                    currency = currency ?: "KRW",
                    salePrice = salePriceAmount?.stripTrailingZeros()?.toPlainString(),
                    salePriceEffectiveDate = salePriceEffectiveDate,
                    availability =
                            runCatching { Availability.valueOf((availability ?: "IN_STOCK").uppercase()) }
                                    .getOrDefault(Availability.IN_STOCK),
                    productCategory = category,
                    brand = brand,
                    gtin = gtin,
                    mpn = mpn,
                    condition =
                            runCatching { Condition.valueOf((condition ?: "NEW").uppercase()) }
                                    .getOrDefault(Condition.NEW),
                    merchantName = merchantName,
                    merchantUrl = merchantUrl,
                    shippingWeight = shippingWeight,
                    returnPolicyDays = returnPolicyDays,
                    reviewsAverageRating = reviewsAverageRating?.toDouble(),
                    reviewsCount = reviewsCount
            )
}
