package ru.kkalscan.routes

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import ru.kkalscan.AppConfig
import ru.kkalscan.AppModule
import ru.kkalscan.api.dto.AuthTokenJson
import ru.kkalscan.api.dto.HealthResponse
import ru.kkalscan.api.dto.VisionHealthInfo
import ru.kkalscan.api.dto.BonusResponse
import ru.kkalscan.api.dto.BugReportResponse
import ru.kkalscan.api.dto.DiaryEntryRequest
import ru.kkalscan.api.dto.PaymentCreateJson
import ru.kkalscan.api.dto.PaymentCreateRequest
import ru.kkalscan.api.dto.ScanBonusRequest
import ru.kkalscan.api.dto.ScanResponse
import ru.kkalscan.api.dto.SubscriptionStatusResponse
import ru.kkalscan.api.dto.VkAuthRequest
import ru.kkalscan.api.dto.WebhookAck
import ru.kkalscan.api.dto.toJson
import ru.kkalscan.api.dto.toResponse
import ru.kkalscan.domain.BadRequestException
import ru.kkalscan.domain.port.DiaryService
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

fun Application.configureRouting(module: AppModule) {
    routing {
        get("/health") {
            call.respond(
                HealthResponse(
                    status = "ok",
                    version = "0.1.0-SNAPSHOT",
                    vision = VisionHealthInfo(
                        provider = AppConfig.visionProvider,
                        api_key_configured = AppConfig.openRouterApiKey.isNotBlank(),
                        model = AppConfig.openRouterModel.takeIf { AppConfig.visionProvider == "openrouter" },
                    ),
                ),
            )
        }

        get("/privacy") {
            call.respondText("KkalScan — политика конфиденциальности (placeholder)", ContentType.Text.Plain)
        }

        get("/pay") {
            val deviceId = call.parseDeviceId() ?: throw BadRequestException("device_id обязателен")
            call.respondText(module.paymentService.renderPayPage(deviceId), ContentType.Text.Html)
        }

        route("/api/v1") {
            post("/scan") {
                val deviceId = call.parseDeviceId() ?: throw BadRequestException("device_id обязателен")
                val multipart = call.receiveMultipart(formFieldLimit = 1024 * 1024)
                var photoBytes: ByteArray? = null
                var tzOffset = call.request.queryParameters["timezone_offset_minutes"]?.toIntOrNull() ?: 180

                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FileItem -> if (part.name == "photo") {
                            photoBytes = part.streamProvider().readBytes()
                        }
                        is PartData.FormItem -> when (part.name) {
                            "timezone_offset_minutes" -> tzOffset = part.value.toIntOrNull() ?: tzOffset
                        }
                        else -> Unit
                    }
                    part.dispose()
                }

                val photo = photoBytes ?: throw BadRequestException("photo обязателен")
                val actor = module.identityResolver.resolve(deviceId, call.request.headers["Authorization"])
                val localDate = LocalDate.now()
                val result = module.scanService.analyzePhoto(actor, photo, localDate, tzOffset)
                call.respond(HttpStatusCode.OK, result.toResponse())
            }

            post("/scan/bonus") {
                val body = call.receive<ScanBonusRequest>()
                val deviceId = parseUuid(body.device_id, "device_id")
                val actor = module.identityResolver.resolve(deviceId, call.request.headers["Authorization"])
                val result = module.quotaService.grantAdBonus(actor, LocalDate.now())
                call.respond(BonusResponse(result.scansLeft, result.bonusGranted))
            }

            get("/diary") {
                val deviceId = call.parseDeviceId() ?: throw BadRequestException("device_id обязателен")
                val date = call.request.queryParameters["date"]?.let { LocalDate.parse(it) }
                    ?: throw BadRequestException("date обязателен")
                val tz = call.request.queryParameters["timezone_offset_minutes"]?.toIntOrNull() ?: 180
                val actor = module.identityResolver.resolve(deviceId, call.request.headers["Authorization"])
                call.respond(module.diaryService.getDay(actor, date, tz).toJson())
            }

            post("/diary/entries") {
                val body = call.receive<DiaryEntryRequest>()
                val deviceId = parseUuid(body.device_id, "device_id")
                val actor = module.identityResolver.resolve(deviceId, call.request.headers["Authorization"])
                val request = DiaryService.CreateDiaryEntryRequest(
                    mealType = body.meal_type,
                    scanId = body.scan_id?.let { parseUuid(it, "scan_id") },
                    dishes = body.dishes,
                )
                val response = module.diaryService.addEntry(actor, request, LocalDate.now())
                call.respond(HttpStatusCode.Created, response.toJson())
            }

            delete("/diary/entries/{id}") {
                val deviceId = call.parseDeviceId() ?: throw BadRequestException("device_id обязателен")
                val entryId = parseUuid(call.parameters["id"] ?: throw BadRequestException("id обязателен"), "id")
                val actor = module.identityResolver.resolve(deviceId, call.request.headers["Authorization"])
                module.diaryService.deleteEntry(actor, entryId)
                call.respond(HttpStatusCode.NoContent)
            }

            get("/subscription/status") {
                val deviceId = call.parseDeviceId() ?: throw BadRequestException("device_id обязателен")
                val actor = module.identityResolver.resolve(deviceId, call.request.headers["Authorization"])
                call.respond(module.subscriptionService.getStatus(actor).toJson())
            }

            post("/auth/vk") {
                val body = call.receive<VkAuthRequest>()
                val response = module.authService.linkVk(parseUuid(body.device_id, "device_id"), body.access_token)
                call.respond(
                    AuthTokenJson(
                        access_token = response.accessToken,
                        token_type = response.tokenType,
                        expires_in = response.expiresIn,
                        user_id = response.userId.toString(),
                        is_pro = response.isPro,
                        account_linked = response.accountLinked,
                        linked_providers = response.linkedProviders.map { it.name },
                    ),
                )
            }

            post("/payments/tochka/create") {
                val body = call.receive<PaymentCreateRequest>()
                val response = module.paymentService.createTochkaPayment(
                    parseUuid(body.device_id, "device_id"),
                    body.tariff,
                )
                call.respond(
                    PaymentCreateJson(
                        payment_url = response.paymentUrl,
                        payment_id = response.paymentId.toString(),
                    ),
                )
            }

            post("/payments/tochka/webhook") {
                val raw = call.receiveText()
                val signature = call.request.headers["X-Signature"]
                module.paymentService.handleTochkaWebhook(raw, signature)
                call.respond(WebhookAck())
            }

            post("/feedback/bug") {
                val deviceId = call.parseDeviceId() ?: throw BadRequestException("device_id обязателен")
                val multipart = call.receiveMultipart(formFieldLimit = 1024 * 1024)
                var email: String? = null
                var description: String? = null
                val screenshots = mutableListOf<ByteArray>()

                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FormItem -> when (part.name) {
                            "email" -> email = part.value
                            "description" -> description = part.value
                        }
                        is PartData.FileItem -> if (part.name == "screenshot") {
                            screenshots.add(part.streamProvider().readBytes())
                        }
                        else -> Unit
                    }
                    part.dispose()
                }

                val actor = module.identityResolver.resolve(deviceId, call.request.headers["Authorization"])
                val result = module.bugReportService.submitBugReport(
                    actor = actor,
                    email = email ?: throw BadRequestException("email обязателен"),
                    description = description ?: throw BadRequestException("description обязателен"),
                    screenshots = screenshots,
                )
                call.respond(
                    BugReportResponse(
                        report_id = result.reportId.toString(),
                        is_pro = result.isPro,
                        pro_until = result.proUntil?.let { instantFormatter.format(it) },
                        message = result.message,
                    ),
                )
            }
        }
    }
}

private val instantFormatter = DateTimeFormatter.ISO_INSTANT

private fun ApplicationCall.parseDeviceId(): UUID? {
    request.queryParameters["device_id"]?.let { return runCatching { UUID.fromString(it) }.getOrNull() }
    request.headers["X-Device-Id"]?.let { return runCatching { UUID.fromString(it) }.getOrNull() }
    return null
}

private fun parseUuid(value: String, field: String): UUID =
    runCatching { UUID.fromString(value) }.getOrElse {
        throw BadRequestException("Некорректный $field")
    }
