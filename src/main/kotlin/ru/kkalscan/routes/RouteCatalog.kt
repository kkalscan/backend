package ru.kkalscan.routes

/**
 * HTTP route catalog — see docs/methods.md §2, §16.
 *
 * | Method | Path | Handler |
 * |--------|------|---------|
 * | GET | /health | HealthRoutes |
 * | POST | /api/v1/scan | ScanRoutes.postScan |
 * | POST | /api/v1/scan/text | ScanRoutes.postScanText |
 * | POST | /api/v1/scan/bonus | ScanRoutes.postBonus |
 * | GET | /api/v1/diary | DiaryRoutes.getDiary |
 * | GET | /api/v1/activity/emulator | ApiRoutes activity emulator |
 * | POST | /api/v1/diary/entries | DiaryRoutes.postEntry |
 * | PUT | /api/v1/diary/activity | DiaryRoutes.syncActivity |
 * | POST | /api/v1/diary/workouts | DiaryRoutes.postWorkout |
 * | DELETE | /api/v1/diary/workouts/{id} | DiaryRoutes.deleteWorkout |
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
