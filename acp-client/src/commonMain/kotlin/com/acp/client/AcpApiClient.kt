package com.acp.client

import com.acp.schema.checkout.CheckoutSessionResponse
import com.acp.schema.checkout.CreateCheckoutSessionRequest
import com.acp.schema.feed.ProductFeedItem
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

/**
 * Merchant 서버(ACP)와 통신하는 클라이언트.
 *
 * Merchant는 Jackson SNAKE_CASE로 직렬화하므로, kotlinx-serialization도
 * snake_case 네이밍 전략으로 맞춘다.
 */
class AcpApiClient(
        private val baseUrl: String = "http://localhost:8080",
        private val httpClient: HttpClient = defaultClient()
) {
    suspend fun getFeed(): List<ProductFeedItem> = httpClient.get("$baseUrl/feed").body()

    suspend fun createCheckoutSession(
            request: CreateCheckoutSessionRequest
    ): CheckoutSessionResponse =
            httpClient
                    .post("$baseUrl/checkout_sessions") {
                        contentType(ContentType.Application.Json)
                        setBody(request)
                    }
                    .body()

    suspend fun getCheckoutSession(id: String): CheckoutSessionResponse =
            httpClient.get("$baseUrl/checkout_sessions/$id").body()

    fun close() = httpClient.close()

    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        fun defaultClient(): HttpClient =
                HttpClient(CIO) {
                    install(ContentNegotiation) {
                        json(
                                Json {
                                    ignoreUnknownKeys = true
                                    namingStrategy = JsonNamingStrategy.SnakeCase
                                }
                        )
                    }
                }
    }
}
