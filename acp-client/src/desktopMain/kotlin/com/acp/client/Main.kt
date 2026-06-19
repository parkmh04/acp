package com.acp.client

import com.acp.schema.checkout.CheckoutItem
import com.acp.schema.checkout.CheckoutSessionResponse
import com.acp.schema.checkout.CreateCheckoutSessionRequest
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import kotlinx.coroutines.runBlocking

/**
 * ACP 에이전트 시뮬레이터 (CLI).
 *
 * Merchant 서버(:8080)를 호출하여 상품 검색 → 체크아웃 세션 생성 흐름을 시연한다.
 * (Compose Desktop UI는 향후 추가; 현재는 헤드리스 검증이 가능한 CLI로 제공)
 *
 * 사용 예:
 *   ./gradlew :acp-client:run --args="demo"
 *   ./gradlew :acp-client:run --args="feed"
 *   ./gradlew :acp-client:run --args="get <sessionId>"
 */
class AcpCli : CliktCommand(name = "acp-agent", help = "ACP 에이전트 시뮬레이터") {
    val baseUrl: String by option("--base-url", help = "Merchant 서버 URL")
            .default("http://localhost:8080")

    override fun run() {
        currentContext.obj = baseUrl
    }
}

private fun parentBaseUrl(cmd: CliktCommand): String =
        cmd.currentContext.findObject<String>() ?: "http://localhost:8080"

class FeedCommand : CliktCommand(name = "feed", help = "상품 피드 조회") {
    override fun run() = runBlocking {
        val client = AcpApiClient(parentBaseUrl(this@FeedCommand))
        try {
            val items = client.getFeed()
            echo("상품 ${items.size}건:")
            items.forEach { echo("  - ${it.id} | ${it.title} | ${it.price}${it.currency} | ${it.availability}") }
        } finally {
            client.close()
        }
    }
}

class DemoCommand :
        CliktCommand(name = "demo", help = "피드 조회 → 첫 상품으로 체크아웃 세션 생성") {
    private val qtyOpt: String by option("--qty", help = "수량").default("1")

    override fun run() = runBlocking {
        val client = AcpApiClient(parentBaseUrl(this@DemoCommand))
        try {
            val feed = client.getFeed()
            if (feed.isEmpty()) {
                echo("피드가 비어 있습니다. Merchant 서버와 시드 데이터를 확인하세요.")
                return@runBlocking
            }
            val first = feed.first()
            echo("선택 상품: ${first.id} (${first.title})")

            val session =
                    client.createCheckoutSession(
                            CreateCheckoutSessionRequest(
                                    items = listOf(CheckoutItem(id = first.id, quantity = qtyOpt.toInt()))
                            )
                    )
            printSession(session)
        } finally {
            client.close()
        }
    }

    private fun printSession(s: CheckoutSessionResponse) {
        echo("세션 생성됨: ${s.id} [${s.status}]")
        s.totals.forEach { echo("  ${it.displayText}: ${it.amount}${s.currency}") }
        s.nextActionUrl?.let { echo("  next_action_url: $it") }
    }
}

class GetCommand : CliktCommand(name = "get", help = "세션 조회") {
    private val sessionId: String by option("--id", help = "세션 ID").default("")

    override fun run() = runBlocking {
        if (sessionId.isBlank()) {
            echo("--id 가 필요합니다.")
            return@runBlocking
        }
        val client = AcpApiClient(parentBaseUrl(this@GetCommand))
        try {
            val s = client.getCheckoutSession(sessionId)
            echo("세션 ${s.id} [${s.status}]")
            s.totals.forEach { echo("  ${it.displayText}: ${it.amount}${s.currency}") }
        } finally {
            client.close()
        }
    }
}

fun main(args: Array<String>) =
        AcpCli().subcommands(FeedCommand(), DemoCommand(), GetCommand()).main(args)
