# Вход, офлайн-библиотеки и Google — 22 ноября 2026

База этапа: b7c61f5, feature/android-mvp. Версия исходников 0.5.0-beta/code7. Публичный релиз не создаётся.

- Android: обязательный первый Google/Email вход; Credential Manager, Firebase REST; проверка и повтор пароля, подтверждение/повтор письма/восстановление. Google связывается с текущим UID, иной UID не может удалить текущий аккаунт.
- Firebase-сессия зашифрована Android Keystore, пароли и Google ID Token не сохраняются. Повторный локальный запуск не обращается к сети; временная ошибка refresh не стирает сессию. Для облака нужен действительный Firebase JWT.
- Библиотеки разделены по SHA256 UID. Старые файлы остаются на месте, владелец подтверждается явно. Выход/удаление аккаунта не удаляет файлы. Ручной ZIP экспорт исключает credentials и чужие аккаунты. Новый UID не наследует библиотеку удалённого аккаунта.
- Профиль: настоящее имя/email/статус, количество тестов/попыток, недавняя история, настройки темы/таймера, связывание Google, выход/удаление.

## Production (разрешено пользователем)
Google включён в существующем Firebase testhelper-fe15b, Spark $0. Support email zhantik690@gmail.com; публичное имя TestHelper. В существующее Android-приложение kz.quizgenerator добавлены SHA-1/SHA-256 прежней debug-подписи. Приватный keystore не публикуется.
Worker testhelper-ai: 45b22ae6-f37a-480f-b9e2-4c1be2734f20. Добавлены только allowlist google.com и публичный Google Web client ID в /api/v1/auth/config; остальные JWT проверки/Email/SQLite/маршруты/квоты сохранены. Список имён secrets до/после одинаков. Новых secrets, migrations и namespace не создавалось.
Откат: существующий Wrangler runner rollback 69d616ce-dd2d-4fd5-bc3b-26dc658d2804 --config android/.build-tools/wrangler-rollout.toml. Этот вариант уже содержит AiQuota. Исходники предыдущей password-only версии отдельно сохранены в игнорируемой android/.build-tools/google-rollout-rollback-69d616ce. Не удалять SQLite binding или migration.

## Фактические проверки
34 backend tests — PASS, включая подписанные тестовые Google JWT, неподтверждённую почту и неверную аудиторию. 14 целевых Android unit tests — PASS (AuthClientTest/AccountStorageTest/LibraryBackupTest).
Живой тест после rollout: существующий подтверждённый Email аккаунт → Firebase ID Token → Cloudflare → Gemini: HTTP200, ровно2вопроса,1637мс, 2026-11-22T11:33:59Z. Тестовый пароль/JWT не включён в отчёт. Повторная регистрация/удаление реального аккаунта не выполнялись.
Google sign-in на телефоне, Android Keystore, UI/темы/SAF экспорт, перезапуск/режим полёта, установка обновления и сохранность настоящей библиотеки требуют ручной проверки. Mock Google/Firebase тест не доказывает работу системного окна выбора аккаунта. App Check/Play Integrity не внедрялись; фиктивной антибот-кнопки нет. Серверные квоты и JWT сохранены.

Полная проверка этапа:108Android unit tests/0fail; Lint0errors/5warnings; assembleDebug PASS (4m38s).
