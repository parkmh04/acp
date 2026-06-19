package com.acp.merchant.config

import com.acp.merchant.infrastructure.cafe24.Cafe24TokenManager
import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.channel.ChannelOption
import java.time.Duration
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import reactor.netty.http.client.HttpClient

private val logger = KotlinLogging.logger {}

/** 외부 호출 무한 대기 방지용 타임아웃 커넥터 (connect 3s / response 10s) */
internal fun timeoutConnector(connectMs: Int = 3000, responseSec: Long = 10): ReactorClientHttpConnector {
    val httpClient =
            HttpClient.create()
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectMs)
                    .responseTimeout(Duration.ofSeconds(responseSec))
    return ReactorClientHttpConnector(httpClient)
}

/**
 * Cafe24 API 설정
 *
 * Cafe24 API 호출을 위한 WebClient 및 관련 설정을 제공합니다.
 */
@Configuration(proxyBeanMethods = false)
class Cafe24Config(
    private val tokenManager: Cafe24TokenManager
) {

    @field:Value("\${cafe24.api.base-url}")
    lateinit var baseUrl: String

    @field:Value("\${cafe24.client-id}")
    lateinit var clientId: String

    @field:Value("\${cafe24.client-secret}")
    lateinit var clientSecret: String

    /**
     * Cafe24 API 호출용 WebClient
     *
     * - Base URL: Cafe24 API 엔드포인트
     * - Authorization: Bearer 토큰 인증 (동적 주입)
     * - Content-Type: application/json
     */
    @Bean
    fun cafe24WebClient(): WebClient {
        logger.info { "Initializing Cafe24 WebClient with baseUrl: $baseUrl" }

        return WebClient.builder()
            .baseUrl(baseUrl)
            .clientConnector(timeoutConnector())
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .filter { request, next ->
                // 매 요청마다 최신 토큰 조회
                val token = tokenManager.getAccessToken()
                val modifiedRequest = if (token.isNotBlank()) {
                    org.springframework.web.reactive.function.client.ClientRequest.from(request)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                        .build()
                } else {
                    request
                }

                logger.debug { "Cafe24 API Request: ${request.method()} ${request.url()}" }
                
                next.exchange(modifiedRequest)
                    .doOnError { error ->
                        when (error) {
                            is WebClientResponseException -> {
                                logger.error {
                                    "Cafe24 API Error: ${error.statusCode} - ${error.responseBodyAsString}"
                                }
                            }
                            else -> {
                                logger.error(error) { "Cafe24 API Request failed" }
                            }
                        }
                    }
                    .onErrorResume { error ->
                        logger.error(error) { "Cafe24 API call failed" }
                        Mono.error(error)
                    }
            }
            .build()
    }

    /** Cafe24 OAuth 설정 정보 */
    @Bean
    open fun cafe24OAuthConfig() =
        Cafe24OAuthConfig(clientId = clientId, clientSecret = clientSecret, baseUrl = baseUrl)
}

/** Cafe24 OAuth 설정 정보를 담는 데이터 클래스 */
data class Cafe24OAuthConfig(val clientId: String, val clientSecret: String, val baseUrl: String)
