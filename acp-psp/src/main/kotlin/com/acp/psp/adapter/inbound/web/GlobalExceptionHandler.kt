package com.acp.psp.adapter.inbound.web

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

private val logger = KotlinLogging.logger {}

/**
 * 전역 예외 처리기 (PSP).
 *
 * 표준 예외를 RFC 7807 ProblemDetail 응답으로 변환한다.
 * - IllegalArgumentException -> 400 (잘못된 결제 요청/파라미터)
 * - IllegalStateException    -> 409 (결제 상태 충돌)
 *
 * 프레임워크 예외(요청 본문 파싱 실패 등)는 Spring 기본 매핑(4xx)을 유지하기 위해
 * 의도적으로 catch-all 핸들러를 두지 않는다.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(e: IllegalArgumentException): ProblemDetail {
        logger.warn { "Bad request: ${e.message}" }
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.message ?: "Invalid request")
                .apply { title = "Bad Request" }
    }

    @ExceptionHandler(IllegalStateException::class)
    fun handleConflict(e: IllegalStateException): ProblemDetail {
        logger.warn { "Conflict: ${e.message}" }
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.message ?: "Invalid state")
                .apply { title = "Conflict" }
    }
}
