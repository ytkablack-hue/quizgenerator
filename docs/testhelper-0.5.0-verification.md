# TestHelper 0.5.0 Beta — проверка 22.11.2026

Ветка feature/android-mvp. Исходная точка финального этапа cfbfb4e; исходники APK соответствуют коммиту, содержащему этот отчёт (после сборки менялась только документация). Старые коммиты OpenCode a2f14ce/be3aa02 сохранены. Старая папка02_MyWork/android не изменялась.

## Реализовано

- TXT: строгие UTF-8/BOM/UTF-16LE/BE/Windows1251, ручной выбор кодировки с предпросмотром; русский/казахский текст, вопросы без ключей, разные обозначения/нумерация/многострочность/отдельные ключи. Неоднозначные/повторные/проблемные исходные фрагменты сохраняются в importProblems и JSON; импорт сначала просматривается.
- DOCX: document.xml/numbering.xml, таблицы/объединённые ячейки/split runs/переносы, decimal/upperLetter/lowerLetter, restart/startOverride/lvlOverride; bounded ZIP/XML/CRC/отмена сохранены.
- PDF: Android15+ системное извлечение текста; без текста и на Android8–14 — локальный OCR страницы. JPG/PNG — локальный OCR. Существующий облачный OCR/Gemini остаётся отдельным явным действием, автоматического облачного fallback нет.
- OCR: разрешённый Tesseract4Android4.9.0, rus/kaz/eng tessdata_fast commit87416418657359cb625c412a48b6e1d6d41c29bd. Языки12,709,470байт, manifest SHA256/лицензия в assets/ocr. Модели OCR включены в APK и копируются в собственный noBackup каталог с проверкой SHA256; LLM не скачивалась. Изображение64МиБ, исходные размеры≤100Мп, декодирование/рендер по одной странице≤2500px/6.25Мп, PDF≤5000страниц/8млнсимволов. Нативная отмена через HOCR/stop/progress, ориентация EXIF. Пустая/нераспознаваемая страница прерывает импорт с ошибкой, частичный тест автоматически не сохраняется. OCR-ключи помечены INFERRED и требуют ручного подтверждения.
- Несколько правильных ответов: редактор/мультивыбор, exact-set оценка, сохранение selections с чтением старых answers, история всех выбранных/верных ответов, карточки со всеми ключами без дубликатов. Неизвестные/предположительные ответы исключены из строгой оценки.
- Google/Email onboarding, обязательный первый вход, сохранённая зашифрованная Firebase сессия, локальная работа без сети после входа, UID-изоляция/явный владелец старой библиотеки, профиль, выход/удаление, ZIP экспорт без токенов. Детали docs/testhelper-auth-verification.md.
- AnswerInferenceProvider/Service/Result и адаптер выборки30: только архитектура/тесты, без production LLM. SOURCE/MANUAL/INFERRED/LOCAL_AI/CLOUD_AI/UNKNOWN различаются. Непроверенное правило первого ответа убрано из UI. Размеры/память/план docs/local-ai-plan.md.

## Фактически выполненные проверки

123 Android unit tests,0fail,0errors. Полный :app:testDebugUnitTest; :app:lintDebug; :app:assembleDebug — BUILD SUCCESSFUL,3m20s. Lint0errors/6warnings: 4совета обновления зависимостей, framework ExifInterface и createBitmap KTX. Новые тяжёлые обновления toolchain ради предупреждений не выполнялись. Googleid1.1.1 оставлен совместимым с Kotlin2.0;1.2.1 требует более нового Kotlin metadata.
TXT/DOCX fixtures100/500/800/1500вопросов с12вариантами/JSON roundtrip; кодировки/повреждения/отмена/ключи/мультивыбор/сохранение старых попыток; mock Firebase/Google/session/errors/изоляция/backup; OCR bounds/недостоверность ключей/реальные SHA256 bundled language files. OCR engine и Android PDF renderer на JVM не запускались: тесты конвейера не доказывают качество распознавания.
34 backend tests PASS; production после разрешения пользователя45b22ae6-f37a-480f-b9e2-4c1be2734f20. Live existing verified Email→Firebase JWT→Cloudflare→Gemini:HTTP200/2вопроса/1637мс. Реальный Google Credential Manager на телефоне пока не проверен. SQLite/секреты/квоты сохранены, тарифы не менялись.
Официальный OCR AAR SHA256 bce5d6413a1a5ae3d7240033fbbc851ba3217d0a08d9769400e17a077f42cb2a совпал с upstream Gradle metadata. ELF PT_LOAD alignment arm64/x86_64 всех8библиотек≥16384. APK zipalign -c -P16 PASS. Gradle сообщил, что не смог выполнить stripping этих .so, и упаковал их как есть; NDK ради уменьшения debug APK не устанавливался.

## APK

Путь C:/Projects/quizgenerator/android/app/build/outputs/apk/debug/app-debug.apk
TestHelper0.5.0-beta/code7; package kz.quizgenerator; targetSdk36/minSdk26; ABI arm64-v8a/armeabi-v7a/x86/x86_64.
Размер47891836байт (~45.67МиБ). SHA256 cc3456fb8725a8216aa96f867673d042f3b7a52d41894d55321001e5f2dba45f.
apksigner verify PASS, v2, один signer Android Debug; прежний сертификат SHA256710f7ec2403e51ae9423bc84de097ace7abb474d5f6d89c943d25075b17f333e, SHA1068679212275fda91e62750d09a7d705621ed45c совпал с добавленными Firebase fingerprints. Private keystore в Git не добавлен.
APK в Git не добавляется, новый публичный Release не создавался.

## Не объявлять проверенным

См. docs/REMAINING.md: установка обновления/реальная сохранность данных, Google UI/Email flows на телефоне, Keystore/режим полёта/темы/SAF/таймер/мультивыбор; реальное качество/скорость/RAM OCR/рендер PDF на телефоне и800вопросов; реальные сложные университетские файлы. Локального LLM inference и готовой AI проверки30вопросов пока нет; это следующий отдельно согласуемый runtime/model этап.
