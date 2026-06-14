package ru.kkalscan.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import ru.kkalscan.api.dto.ApiErrorResponse
import ru.kkalscan.domain.DomainException

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<DomainException> { call, cause ->
            val status = when (cause.errorCode) {
                "limit_hit" -> HttpStatusCode.TooManyRequests
                "bonus_already_used" -> HttpStatusCode.Conflict
                "forbidden" -> HttpStatusCode.Forbidden
                "not_found" -> HttpStatusCode.NotFound
                "unauthorized" -> HttpStatusCode.Unauthorized
                "bad_request" -> HttpStatusCode.BadRequest
                "vision_budget_exceeded", "vision_unavailable" -> HttpStatusCode.ServiceUnavailable
                else -> HttpStatusCode.InternalServerError
            }
            call.respond(
                status,
                ApiErrorResponse(cause.errorCode, cause.message, cause.scansLeft),
            )
        }
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Unhandled", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiErrorResponse("internal_error", "Внутренняя ошибка сервера"),
            )
        }
    }
}
