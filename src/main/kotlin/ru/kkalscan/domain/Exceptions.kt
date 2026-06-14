package ru.kkalscan.domain

open class DomainException(
    val errorCode: String,
    override val message: String,
    val scansLeft: Int? = null,
) : Exception(message)

class LimitHitException(scansLeft: Int = 0) :
    DomainException("limit_hit", "На сегодня бесплатные сканы закончились", scansLeft)

class BonusAlreadyUsedException :
    DomainException("bonus_already_used", "Бонус за рекламу уже использован сегодня")

class ForbiddenException(message: String = "Доступ запрещён") :
    DomainException("forbidden", message)

class NotFoundException(message: String = "Не найдено") :
    DomainException("not_found", message)

class BadRequestException(message: String) :
    DomainException("bad_request", message)

class UnauthorizedException(message: String = "Не авторизован") :
    DomainException("unauthorized", message)

class VisionBudgetExceededException :
    DomainException("vision_budget_exceeded", "Сервис временно недоступен, попробуйте позже")

class VisionUnavailableException(cause: Throwable? = null) :
    DomainException("vision_unavailable", "Не удалось распознать фото, попробуйте ещё раз") {
    init {
        cause?.let { initCause(it) }
    }
}

class InvalidPhotoException(message: String) :
    DomainException("bad_request", message)
