# TestHelper 0.4.0 — готовность магазинов

Состояние на 21 ноября 2026: локальная beta, не production-релиз. Код новой авторизации подготовлен; Firebase и Durable Object ещё не развёрнуты. Не публиковать в Google Play/App Store в текущем состоянии.

## Android

Стабильный applicationId: kz.quizgenerator. versionCode 6, versionName 0.4.0, minSdk 26. Compile/target SDK 36, AGP 8.10.1, Gradle 8.11.1; инструменты установлены локально в исключённую из Git .build-tools. Unit-тесты и сборки APK/AAB пройдены. Runtime-проверки на Android 16 ещё обязательны. Требование Google Play к API 36:
https://developer.android.com/google/play/requirements/target-sdk

Release включает R8 и shrinkResources, не включает debug network config, не содержит общего AI-пароля и Gemini API key. Подпись release настраивается только переменными TESTHELPER_UPLOAD_STORE_FILE, TESTHELPER_UPLOAD_STORE_PASSWORD, TESTHELPER_UPLOAD_KEY_ALIAS, TESTHELPER_UPLOAD_KEY_PASSWORD. Ключ и пароли в Git запрещены. Без них AAB является проверочным неподписанным артефактом, не готовым к загрузке в Play. Debug APK подписывается прежним debug-сертификатом для обновления 0.3.1 без удаления данных.

До загрузки: runtime-проверка target36 на Android 16; проверка native-библиотек на страницы 16 KiB на устройстве; владелец предоставляет/утверждает upload key и Play App Signing; UI-проверки реального телефона (поворот, фон/восстановление, светлая/тёмная/системная тема, offline, свежая установка и обновление с данными). Скриншоты магазина должны быть сняты из проверенного приложения, не нарисованы вместо тестирования. Добавлена векторная иконка; окончательные adaptive/store-ресурсы и описание владелец утверждает отдельно.

## Data safety — черновая карта

Локальные тесты/карточки/история не синхронизируются. Firebase: email, UID, опционально имя и аутентификация. ИИ: отправленные по нажатию учебный текст/файлы/изображения, тема, ошибки. Cloudflare/Firebase/Gemini: технические метаданные по условиям провайдеров. Нельзя автоматически отмечать «данные не собираются», «не передаются третьим лицам», «полностью анонимны», «не используются для обучения».

Уточнить и заполнить с владельцем назначение каждой категории, ephemeral processing, service-provider exemptions, обязательность/добровольность и удаление с учётом фактического развёртывания. Источники:
https://support.google.com/googleplay/android-developer/answer/10144311
https://support.google.com/googleplay/android-developer/answer/14151465

## iOS: отдельный SwiftUI-клиент с общим backend

Выбран вариант с меньшим риском для работающего Kotlin/Compose приложения: не переносить Android UI на KMP сейчас. Общие модели/схемы API, правила проверки ответов, тестовые примеры и Firebase-проект используются обоими клиентами. Контракт сервера в backend/cloudflare/openapi.yaml. На iOS нужен собственный клиент, Keychain для сессий, локальное хранилище и миграции, OCR/file picker, темы, доступность и отдельные UI/unit-тесты. Android Activity/ViewModel/Keystore не являются переносимым кодом. Общую чистую Kotlin-логику можно извлечь позже после стабилизации тестов, без миграции базы пользователей в рамках этого этапа.

На Windows iOS не собран и не тестировался. Нужны Mac, Xcode 26+ с iOS26 SDK (актуально по странице требований Apple), Apple Developer account, signing/provisioning, App Store Connect и TestFlight. Проверить minimum deployment target по актуальным требованиям. Email/Password не является social login; если позже добавятся Google/Facebook login, отдельно оценить требование Sign in with Apple. Нужны account deletion внутри приложения, App Privacy, понятное согласие на передачу данных стороннему ИИ, возрастные рейтинги и политика конфиденциальности.
https://developer.apple.com/news/upcoming-requirements/
https://developer.apple.com/app-store/review/guidelines/

Ни магазин, ни iOS-готовность не заявлены до выполнения этих пунктов.
