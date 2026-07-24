package ru.kkalscan.routes

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import ru.kkalscan.AppConfig
import ru.kkalscan.AppModule
import ru.kkalscan.api.dto.HealthResponse
import ru.kkalscan.api.dto.AuthTokenJson
import ru.kkalscan.api.dto.FeatureSearchIntentRequest
import ru.kkalscan.api.dto.FeatureSearchIntentResponse
import ru.kkalscan.api.dto.FeatureSearchItemJson
import ru.kkalscan.api.dto.FeatureSearchResponse
import ru.kkalscan.api.dto.FoodSearchResponse
import ru.kkalscan.api.dto.SearchQueryStatJson
import ru.kkalscan.api.dto.SearchTopResponse
import ru.kkalscan.api.dto.VisionHealthInfo
import ru.kkalscan.api.dto.BonusResponse
import ru.kkalscan.api.dto.BugReportResponse
import ru.kkalscan.api.dto.DiaryEntryRequest
import ru.kkalscan.api.dto.TestPaymentActivateRequest
import ru.kkalscan.api.dto.TestPaymentActivateResponse
import ru.kkalscan.api.dto.ProSubscriptionStartRequest
import ru.kkalscan.api.dto.ProSubscriptionStartResponse
import ru.kkalscan.api.dto.PromoApplyRequest
import ru.kkalscan.api.dto.PromoApplyResponse
import ru.kkalscan.api.dto.PaymentCreateJson
import ru.kkalscan.api.dto.PaymentCreateRequest
import ru.kkalscan.api.dto.ScanBonusRequest
import ru.kkalscan.api.dto.ScanTextRequest
import ru.kkalscan.api.dto.ScanResponse
import ru.kkalscan.api.dto.SubscriptionOfferJson
import ru.kkalscan.api.dto.SubscriptionOffersResponse
import ru.kkalscan.api.dto.SubscriptionStatusResponse
import ru.kkalscan.api.dto.VkAuthRequest
import ru.kkalscan.api.dto.WebhookAck
import ru.kkalscan.api.dto.ActivitySyncRequest
import ru.kkalscan.api.dto.WorkoutRequest
import ru.kkalscan.api.dto.WorkoutResponse
import ru.kkalscan.api.dto.WorkoutTextRequest
import ru.kkalscan.api.dto.WorkoutParseResponse
import ru.kkalscan.api.dto.toJson
import ru.kkalscan.api.dto.parseActivitySource
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
            call.respondText(PrivacyPolicyPage.html(), ContentType.Text.Html)
        }

        get("/pay") {
            val deviceId = call.parseDeviceId() ?: throw BadRequestException("device_id обязателен")
            call.respondText(module.paymentService.renderPayPage(deviceId), ContentType.Text.Html)
        }

        get("/pay/success") {
            val deviceId = call.parseDeviceId() ?: throw BadRequestException("device_id обязателен")
            call.respondText(module.paymentService.renderPaySuccessPage(deviceId), ContentType.Text.Html)
        }

        get("/pay/fail") {
            call.respondText(module.paymentService.renderPayFailPage(), ContentType.Text.Html)
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

            post("/scan/text") {
                val body = call.receive<ScanTextRequest>()
                val deviceId = parseUuid(body.device_id, "device_id")
                val actor = module.identityResolver.resolve(deviceId, call.request.headers["Authorization"])
                val localDate = LocalDate.now()
                val result = module.scanService.analyzeDescription(
                    actor,
                    body.description,
                    localDate,
                    body.timezone_offset_minutes,
                )
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

            get("/activity/emulator") {
                val deviceId = call.parseDeviceId() ?: throw BadRequestException("device_id обязателен")
                val tz = call.request.queryParameters["timezone_offset_minutes"]?.toIntOrNull() ?: 180
                val today = LocalDate.now()
                call.respond(
                    module.activityEmulatorService.getEmulator(deviceId, today, tz).toJson(),
                )
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

            post("/workout/text") {
                val body = call.receive<WorkoutTextRequest>()
                val deviceId = parseUuid(body.device_id, "device_id")
                val actor = module.identityResolver.resolve(deviceId, call.request.headers["Authorization"])
                val result = module.diaryService.parseWorkoutDescription(actor, body.description)
                call.respond(
                    HttpStatusCode.OK,
                    WorkoutParseResponse(
                        title = result.title,
                        burned_kcal = result.burnedKcal,
                        duration_minutes = result.durationMinutes,
                    ),
                )
            }

            post("/diary/workouts") {
                val body = call.receive<WorkoutRequest>()
                val deviceId = parseUuid(body.device_id, "device_id")
                val actor = module.identityResolver.resolve(deviceId, call.request.headers["Authorization"])
                val response = module.diaryService.addWorkout(
                    actor,
                    DiaryService.CreateWorkoutRequest(name = body.name, kcal = body.kcal),
                    LocalDate.now(),
                )
                call.respond(HttpStatusCode.Created, WorkoutResponse(response.workout.toJson()))
            }

            put("/diary/activity") {
                val body = call.receive<ActivitySyncRequest>()
                val deviceId = parseUuid(body.device_id, "device_id")
                val actor = module.identityResolver.resolve(deviceId, call.request.headers["Authorization"])
                val day = module.diaryService.syncActivity(
                    actor,
                    DiaryService.SyncActivityRequest(
                        steps = body.steps,
                        kcal = body.kcal,
                        source = parseActivitySource(body.source),
                    ),
                    LocalDate.now(),
                    body.timezone_offset_minutes,
                )
                call.respond(HttpStatusCode.OK, day.toJson())
            }

            delete("/diary/workouts/{id}") {
                val deviceId = call.parseDeviceId() ?: throw BadRequestException("device_id обязателен")
                val workoutId = parseUuid(call.parameters["id"] ?: throw BadRequestException("id обязателен"), "id")
                val actor = module.identityResolver.resolve(deviceId, call.request.headers["Authorization"])
                module.diaryService.deleteWorkout(actor, workoutId)
                call.respond(HttpStatusCode.NoContent)
            }

            get("/subscription/status") {
                val deviceId = call.parseDeviceId() ?: throw BadRequestException("device_id обязателен")
                module.paymentService.syncPendingPayments(deviceId)
                val actor = module.identityResolver.resolve(deviceId, call.request.headers["Authorization"])
                call.respond(module.subscriptionService.getStatus(actor).toJson())
            }

            get("/subscription/offers") {
                val deviceId = call.parseDeviceId() ?: throw BadRequestException("device_id обязателен")
                module.deviceRepository.getOrCreate(deviceId)
                val offers = module.promoService.listOffers(deviceId).map { offer ->
                    SubscriptionOfferJson(
                        tariff = offer.tariff,
                        title = offer.title,
                        price_rub = offer.priceRub,
                        amount_rub = offer.amountRub,
                        amount_kopecks = offer.amountKopecks,
                        discount_percent = offer.discountPercent,
                        promo_code = offer.promoCode,
                    )
                }
                call.respond(SubscriptionOffersResponse(offers = offers))
            }

            post("/promo/apply") {
                val body = call.receive<PromoApplyRequest>()
                val deviceId = parseUuid(body.device_id, "device_id")
                module.deviceRepository.getOrCreate(deviceId)
                val result = module.promoService.applyPromo(deviceId, body.promo_code)
                call.respond(
                    PromoApplyResponse(
                        promo_code = result.promoCode,
                        discount_percent = result.discountPercent,
                    ),
                )
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

            post("/payments/pro/start") {
                val body = call.receive<ProSubscriptionStartRequest>()
                val deviceId = parseUuid(body.device_id, "device_id")
                val result = module.paymentService.startProSubscription(deviceId, body.tariff)
                call.respond(
                    ProSubscriptionStartResponse(
                        is_pro = result.isPro,
                        pro_until = result.proUntil?.let { instantFormatter.format(it) },
                        tariff = result.tariff,
                        payment_required = result.paymentRequired,
                        payment_url = result.paymentUrl,
                        payment_id = result.paymentId?.toString(),
                        message = result.message,
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
                // Tochka requires HTTP 200 on accessibility test and retries; never fail the delivery ACK.
                val raw = call.receiveText()
                val signature = call.request.headers["X-Signature"]
                runCatching {
                    module.paymentService.handleTochkaWebhook(raw, signature)
                }.onFailure { error ->
                    call.application.environment.log.warn(
                        "Tochka webhook not applied: {} bodyPrefix={}",
                        error.message,
                        raw.take(80),
                    )
                }
                call.respond(WebhookAck())
            }

            post("/payments/test/activate") {
                val body = call.receive<TestPaymentActivateRequest>()
                val deviceId = parseUuid(body.device_id, "device_id")
                val result = module.paymentService.activateTestPayment(deviceId, body.secret)
                call.respond(
                    TestPaymentActivateResponse(
                        is_pro = result.isPro,
                        pro_until = instantFormatter.format(result.proUntil),
                        tariff = result.tariff,
                        email_sent = result.emailSent,
                        message = "Pro активирован на 30 дней (тестовая оплата)",
                    ),
                )
            }

            get("/features/search") {
                val deviceId = call.parseDeviceId() ?: throw BadRequestException("device_id обязателен")
                val query = call.request.queryParameters["q"] ?: ""
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
                val locale = call.request.queryParameters["locale"] ?: "ru"
                val result = module.featureSearchService.search(deviceId, query, limit, locale)
                call.respond(
                    FeatureSearchResponse(
                        query = result.query,
                        items = result.items.map { item ->
                            FeatureSearchItemJson(
                                id = item.id,
                                title = item.title,
                                subtitle = item.subtitle,
                                deeplink = item.deeplink,
                                icon = item.icon,
                            )
                        },
                        total = result.total,
                        popularFallback = result.popularFallback,
                    ),
                )
            }

            post("/feature-search/intent") {
                val deviceId = call.parseDeviceId() ?: throw BadRequestException("device_id обязателен")
                val body = call.receive<FeatureSearchIntentRequest>()
                val result = module.featureSearchService.classifyIntent(deviceId, body.query)
                call.respond(
                    FeatureSearchIntentResponse(
                        query = result.query,
                        isFoodIntent = result.isFoodIntent,
                    ),
                )
            }

            get("/food/search") {
                val deviceId = call.parseDeviceId() ?: throw BadRequestException("device_id обязателен")
                val query = call.request.queryParameters["q"] ?: throw BadRequestException("q обязателен")
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
                val source = call.request.queryParameters["source"] ?: "diary"
                val result = module.foodSearchService.search(deviceId, query, limit, source)
                call.respond(
                    FoodSearchResponse(
                        query = result.query,
                        items = result.items,
                        total = result.total,
                    ),
                )
            }

            get("/analytics/search-top") {
                val days = call.request.queryParameters["days"]?.toIntOrNull() ?: 30
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                val stats = module.foodSearchService.topQueries(days, limit)
                call.respond(
                    SearchTopResponse(
                        days = days.coerceIn(1, 365),
                        queries = stats.map { SearchQueryStatJson(it.query, it.count) },
                    ),
                )
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
