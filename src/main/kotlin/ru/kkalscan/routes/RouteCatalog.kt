package ru.kkalscan.routes

/**
 * HTTP route catalog — see docs/methods.md §2, §16.
 *
 * | Method | Path | Handler |
 * |--------|------|---------|
 * | GET | /health | HealthRoutes |
 * | POST | /api/v1/scan | ScanRoutes.postScan |
 * | POST | /api/v1/scan/bonus | ScanRoutes.postBonus |
 * | GET | /api/v1/diary | DiaryRoutes.getDiary |
 * | POST | /api/v1/diary/entries | DiaryRoutes.postEntry |
 * | DELETE | /api/v1/diary/entries/{id} | DiaryRoutes.deleteEntry |
 * | GET | /api/v1/subscription/status | SubscriptionRoutes.getStatus |
 * | POST | /api/v1/auth/vk | AuthRoutes.postVk |
 * | POST | /api/v1/auth/yandex | AuthRoutes.postYandex |
 * | GET | /api/v1/auth/me | AuthRoutes.getMe |
 * | POST | /api/v1/payments/tochka/create | PaymentRoutes.create |
 * | POST | /api/v1/payments/tochka/webhook | PaymentRoutes.webhook |
 * | GET | /pay | PaymentRoutes.payPage |
 * | GET | /privacy | StaticRoutes.privacy |
 */
object RouteCatalog
