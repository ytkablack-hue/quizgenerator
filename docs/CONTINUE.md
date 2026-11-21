# TestHelper 0.4.0 Beta — продолжение

Состояние на 22 ноября 2026. Работать только в C:/Projects/quizgenerator, feature/android-mvp. Исходную 02_MyWork/android не изменять. Не запускать эмулятор/ADB, не подключать биллинг, Blaze или Workers Paid. Не начинать аудит/разработку заново.

## Выполнено
- Android и backend подготовлены, коммит 3da0a1ca7591fb488f66a7b233c27a5c6b81ae06 уже в origin. targetSdk 36, AGP 8.10.1, Gradle 8.11.1, JDK 17, versionName 0.4.0 / versionCode 6.
- Ранее фактически прошли 51 Android unit-тест, Lint (0 ошибок, 3 предупреждения), assembleDebug и bundleRelease. AAB без production-подписи.
- 22 ноября повторно запущены backend/cloudflare/tests/security.test.mjs: 32 pass, 0 fail (Google/DO mocked, не live Firebase).
- Создан Firebase TestHelper: project ID testhelper-fe15b, project number 21095038877, аккаунт zhantik690@gmail.com. Условия приняты с отдельного разрешения пользователя. Spark, No-cost $0/month подтверждён UI. Analytics и Gemini-помощник консоли отключены.
- Email/Password включён; email link выключен. Шаблоны писем переведены на русский. Password policy: Require enforcement, minimum 8 (Save завершился, кнопка стала disabled). Enumeration protection отдельно не проверена.
- Android app kz.quizgenerator зарегистрирован; Firebase app ID 1:21095038877:android:e06ed55985ab9841d6f974. Конфигурация скачана в C:/Users/Admin/Downloads/google-services.json. Это публичная клиентская конфигурация; не путать с Gemini secret. Не коммитить локальный файл.
- Android получает публичный Firebase config через GET /api/v1/auth/config Worker; добавлять Firebase SDK/ключи в APK не требуется.

## Production ещё НЕ завершён
- Существующий Worker testhelper-ai, https://testhelper-ai.zhantik690.workers.dev, аккаунт Cloudflare 08a62d9bfa36d8f2360007dd14236f23. Последняя UI-проверка: Active Latest 56dc4423. Новый backend ещё НЕ развёрнут; bindings AI_QUOTA пока нет. Старый Gemini ранее отвечал, это не проверка новой авторизации.
- GEMINI_API_KEY и TESTHELPER_ACCESS_TOKEN уже есть в production; НЕ заменять/раскрывать. Значение общего кода в защищённом C:/Users/Admin/AppData/Local/TestHelper-private/server-access.txt, НЕ выводить и НЕ копировать в Git/APK.
- История Cloudflare сохраняет прежние версии. Полной локальной копии старого Worker нет; перед rollout сохранить исходник через редактор, проверить обратное чтение. Откат после DO migration должен сохранять экспорт класса AiQuota; не рассчитывать на слепой rollback к версии без класса.
- Wrangler не установлен. Не устанавливать без разрешения. Браузерный Quick Edit доступен, но требует точного фокуса Monaco; обычный Ctrl+A иногда выбирает внешнюю страницу. Не извлекать cookies/auth токены, не делать fetch через browser evaluate.

## Следующий шаг
Первая команда PowerShell (только чтение):

```powershell
Set-Location -LiteralPath C:\Projects\quizgenerator
git status --short --branch
Get-Content -LiteralPath backend\cloudflare\wrangler.toml
```

1. Через авторизованный Cloudflare UI подготовить rollout существующего Worker, сохранить старый код и секреты. Исходники уже в backend/cloudflare/{worker,auth,quota,ai-core,delete-page}.mjs. wrangler.toml содержит AI_QUOTA -> AiQuota и migration auth-quota-v1/new_sqlite_classes. Не публиковать обработчик, требующий квот, без работоспособной binding.
2. Добавить public env FIREBASE_PROJECT_ID=testhelper-fe15b и FIREBASE_WEB_API_KEY из скачанного JSON client[0].api_key[0].current_key. Не использовать Admin/service-account key. Если UI не позволяет создать SQLite migration, запросить установку официального Wrangler вместо циклов и временных проектов.
3. Развернуть код и SQLite Durable Object в Free. Подтвердить /api/v1/auth/config, отказ без токена, реальную регистрацию/вход, доставку/подтверждение письма и один небольшой quiz через Firebase JWT + X-Request-ID UUID. Настоящий аккаунт и Gemini в новой интеграции ещё не проверены. Не повторять старые health/legacy тесты вместо этого.
4. Пароли/токены тестового аккаунта хранить только защищённо вне Git, не печатать. Ввод/изменение пароля через UI требует передачи управления пользователю по computer-use policy. Для проверки почты читать только относящееся к тесту письмо в правильном аккаунте.
5. При изменении исходников запустить нужные тесты и сборку; только облачная конфигурация не требует пересборки APK. Для Android: & .\android\.build-tools\run-checks.ps1 -Tasks ':app:testDebugUnitTest',':app:lintDebug',':app:assembleDebug'. Использовать готовые локальные инструменты/кэши, не перенастраивать Wrapper. Перед коммитом diff и staged files; push только feature/android-mvp, без force/main.

## Готовый APK
C:/Projects/quizgenerator/android/app/build/outputs/apk/debug/app-debug.apk
10600504 bytes; SHA-256 731242cfd46abcfde0212ddf3c5b642604f0a637ddcc1ee93ed79e54ebe62430 (перепроверен 22 ноября).
Debug certificate SHA-256 710f7ec2403e51ae9423bc84de097ace7abb474d5f6d89c943d25075b17f333e (ранее проверен).
APK устанавливаемый, но новая регистрация/ИИ не готовы до rollout Worker. Не заявлять готовность live-интеграции. На Android вручную остаются вход, письма, Gemini, UI, таймер в фоне/после убийства процесса, обновление с сохранением данных; устройство не использовалось.
