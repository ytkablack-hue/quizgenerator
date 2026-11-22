# TestHelper 0.4.0 Beta — контрольная точка

22 ноября 2026. C:/Projects/quizgenerator, feature/android-mvp. Не начинать разработку заново. Старую 02_MyWork/android не менять, эмулятор/ADB не запускать. Только Spark/Workers Free, без биллинга.

## Уже выполнено
- Android 0.4.0 / versionCode 6, targetSdk 36, AGP 8.10.1, Gradle 8.11.1/JDK17. Ранее прошли 51 unit-тест, Lint (0 ошибок, 3 предупреждения), assembleDebug/bundleRelease. AAB без production-подписи. В текущем этапе Android не менялся, пересборка не требовалась.
- Firebase testhelper-fe15b создан на Spark; Email/Password включён, русские письма подтверждения/восстановления, пароль минимум 8 Require enforcement; kz.quizgenerator зарегистрирован. Не создавать проект заново. Enumeration protection отдельно не проверена.
- Wrangler 4.166.0 официально установлен локально в android/.build-tools/wrangler-cli. Запуск android/.build-tools/wrangler.ps1; OAuth подтверждён пользователем. Аккаунт zhantik690@gmail.com / 08a62d9bfa36d8f2360007dd14236f23. Scopes user:read/account:read/workers_scripts:write/offline_access. Каталог OAuth android/.build-tools/wrangler-config с ACL только текущего пользователя. Ничего из .build-tools не коммитить.
- Существующий testhelper-ai развёрнут с Firebase config и SQLite Durable Object AI_QUOTA -> AiQuota, migration auth-quota-v1/new_sqlite_classes. Текущая версия 69d616ce-dd2d-4fd5-bc3b-26dc658d2804, https://testhelper-ai.zhantik690.workers.dev. Preview URLs отключены; keep_vars=true. Оба секретa GEMINI_API_KEY и TESTHELPER_ACCESS_TOKEN сохранились; значения не менять/не выводить.
- Обнаружено и исправлено реальное runtime-ограничение Workers: redirect:error вызывает TypeError. Все внешние запросы используют manual, 3xx отклоняются. 33 backend-теста прошли, включая запрет JWKS redirect и проверки режима в интеграционных mocks. Временное диагностическое логирование убрано.
- Live: публичный /api/v1/auth/config вернул правильный проект; регистрация и вход Firebase успешны, письмо VERIFY_EMAIL отправлено. Без JWT HTTP401, настоящий JWT с неподтверждённой почтой HTTP403 после исправления (до него был503).

## Откат подготовлен
- Исходная версия 56dc4423-568e-43fc-a228-eb01b70256e8, текст сохранён в android/.build-tools/worker-56dc4423-backup.mjs, LF 8300 символов; SHA256 DE04DF3E32585E7961B2691AF64344AF90FA7C7AD77456CA0AC22129CDC0215F. Копия сверена с редактором, node --check успешен.
- Сначала развёрнут старый обработчик + экспорт AiQuota и SQLite: f426c75a-8802-41de-bd29-38b2d272bc75. Старый /ai/status с сохранённым admin-кодом вернул configured:true. Эта версия — совместимая точка отката после миграции.
- android/.build-tools/wrangler-rollback.toml и worker-rollback-compatible.mjs возвращают старую логику, сохраняя класс/namespace/миграцию. Команда только при необходимости: & .\android\.build-tools\wrangler.ps1 deploy --config .\android\.build-tools\wrangler-rollback.toml
- Нельзя удалять класс/namespace/migration или вслепую откатывать на домиграционную56dc4423. Секреты отдельно не восстанавливать и не вращать.

## Осталось: подтверждение почты и проверка полного личного Firebase-пути
- Тестовый аккаунт создан: zhantik690+testhelper-beta-20261122@gmail.com. Письмо поступает в zhantik690@gmail.com; владелец должен открыть ссылку Firebase (возможно Спам). Запрошено подтверждение пользователя; пока результата нет. Не обходить email_verified и не менять его вручную в Console ради теста.
- Пароль вне Git в защищённом C:/Users/Admin/AppData/Local/TestHelper-private/firebase-beta-test-account.json. Сессия firebase-beta-test-session.json там же. НЕ печатать пароль/JWT/refresh token. Не перезаписывать файлы и не создавать ещё один аккаунт.
- Gmail connector подключён к другому адресу; не читать его письма. После подтверждения выполнить Firebase signInWithPassword с сохранёнными email/password для свежего JWT; получить public apiKey через /api/v1/auth/config. Проверить emailVerified через accounts:lookup. Отправить POST /api/v1/ai/quizzes с Bearer новым JWT, Content-Type application/json, X-Request-ID новым UUID, JSON topic/count=2/difficulty=easy. Проверить HTTP200, число вопросов, варианты/correctIndex и русский текст. Gemini новой production-версии уже проверен отдельным запросом через сохранённый legacy admin token: HTTP200, два корректных русских вопроса. SQLite резервирование сработало. Это не доказывает полный verified-user путь: он ожидает письма. Ответ без секретов в ignored android/.build-tools/live-gemini-quiz.json.
- Команды CLI после входа: & .\android\.build-tools\wrangler.ps1 deploy --config .\android\.build-tools\wrangler-rollout.toml ; node --test backend/cloudflare/tests/security.test.mjs. В ignored rollout-конфиге main указывает на tracked backend/cloudflare/worker.mjs, Firebase client vars взяты из C:/Users/Admin/Downloads/google-services.json. Не путать публичный Firebase client key с секретом Gemini.
- После live-проверки обновить этот файл и docs/testhelper-0.4.0-verification.md, git diff/staged list, commit/push только feature/android-mvp. Не force/main. После облачных изменений повторная APK-сборка не нужна.

## Телефон
APK: C:/Projects/quizgenerator/android/app/build/outputs/apk/debug/app-debug.apk
10600504 bytes; SHA256 731242cfd46abcfde0212ddf3c5b642604f0a637ddcc1ee93ed79e54ebe62430 (повторно проверен).
Debug cert SHA256 710f7ec2403e51ae9423bc84de097ace7abb474d5f6d89c943d25075b17f333e (ранее проверен).
Установить как обновление; не удалять старое приложение с данными. Личный кабинет -> Создать аккаунт / Войти. После ссылки в письме нажать Я подтвердил почту. Адрес сервера уже фиксирован; общий код доступа не нужен. Создать небольшой тест ИИ, пройти его и сохранить результат; затем проверить локальные тесты без сети. На устройстве ещё не проверены UI, жизненный цикл/таймер, обновление с сохранением данных. Реальные recovery/refresh/deletion/квоты пределов, extraction PDF/photo новой версии тоже пока не проверены.
