# Контрольная точка: многоключевые ответы

База d5c4659. Мультивыбор в редакторе/прохождении, сохранение selections с чтением прежних answers, точное сравнение наборов, история всех выбранных/верных вариантов. LOCAL_AI/CLOUD_AI отделены от старого INFERRED и исключены из строгой оценки. Явно обработан NoCredentialException.
Фактически30целевых Android tests PASS,0fail. Полный набор/Lint/APK после этой правки пока НЕ запускались. APK на диске — предыдущая успешно проверенная сборка d5c4659,0.5.0-beta/code7,12863452байта,SHA256f7f517e22917bd80ab55862592d22edd88237e98ae28d0ad1afa57638894a699. Ниже исторический отчёт этапа входа, не путать с текущей проверкой.
Отдельный список остатка: docs/REMAINING.md. Следующая команда: git status --short --branch; затем runner с полным набором после следующих исправлений. Коммит контрольной точки: git log -1 --format=%H.
# Актуальная контрольная точка: вход / офлайн / профиль

22ноября, исходная точка b7c61f5. Реализован вход Google/Email, обязательный первый вход, офлайн восстановление сессии, изоляция по UID, явный владелец старой библиотеки без переноса файлов, профиль/удаление/ZIP экспорт без credentials. Version0.5.0-beta/code7. Последний SHA этого этапа: git log -1 --format=%H (документ входит в этот коммит).
Фактически прошли108Android unit tests,0fail; Lint0errors/5warnings (4обновления зависимостей + NoCredentialException — обрабатывается общим catch, добавить явное сообщение); assembleDebug PASS. 34backend tests PASS. Googleid1.1.1 выбран из-за несовместимости1.2.1 с текущим Kotlin, тяжёлого обновления toolchain не делать.
Пользователь явно разрешил Google Firebase и rollout. Firebase testhelper-fe15b Spark: Google включён, support email и debug SHA1/SHA256 добавлены. Worker45b22ae6-f37a-480f-b9e2-4c1be2734f20: password/google.com, JWT/email/quota/SQLite сохранены; имена secrets до/после одинаковы. Live existing verified Email→JWT→Worker→Gemini:HTTP200/2вопроса/1637мс. Реальный Google UI на телефоне НЕ проверен. Откат на69d616ce-dd2d-4fd5-bc3b-26dc658d2804 с тем же binding/migration; локальный sourcebackup google-rollout-rollback-69d616ce в ignored .build-tools.
Следующее: несколько выбранных ответов (пока UI single-select и мультиключевые вопросы не оцениваются), явное происхождение LOCAL_AI/CLOUD_AI, архитектура без скачивания моделей. Доклад docs/testhelper-auth-verification.md. PDF8–14/локальный OCR ещё отсутствуют; Android15+ engine и Google/Keystore/темы/обновление/режим полёта/SAF требуют телефона. Не выдавать моки за эти проверки.
Команда: & .\android\.build-tools\run-checks.ps1 -Tasks '-Pkotlin.compiler.execution.strategy=in-process',':app:testDebugUnitTest',':app:lintDebug',':app:assembleDebug'
Публичный Release не создавать. Сначала git status и точечный diff. Ниже исторический контекст импорта; актуальная production и версия — в этом блоке.
# TestHelper — продолжение

Работа: C:/Projects/quizgenerator, feature/android-mvp. Правила/карта/команды: AGENTS.md. 02_MyWork/android не изменять; без эмулятора/ADB, платных услуг и новых публичных Releases.

## Последний завершённый этап: импорт / масштаб (после c01bafd)
22 ноября 2026. Исходная точка этапа1c6438f (TXT); этап0 f68975a. OpenCode a2f14ce и be3aa02 сохранены в истории и отправлены без переписывания.
- TXT: UTF-8/BOM/UTF-16LE/BE/Windows-1251, строгие ошибки вместо потери символов; ручной выбор и предпросмотр кодировки с подтверждением перечитывания. Windows-1251 не содержит казахских букв: уже потерянные при записи символы восстановить нельзя; для казахского использовать Unicode.
- Ключи до/после вопросов, частичные/конфликтующие ключи не становятся подтверждёнными; JSON multi-answer не приводит строки/дроби к int. Множественный ключ хранится, но существующая single-select сессия пока исключает такие вопросы из оценки — полноценный multi-select ещё предстоит.
- DOCX: word/document.xml + numbering.xml, цифровая/буквенная нумерация, вложенные списки/перезапуск, startOverride; таблицы, merged cells и split runs. Полные lvlOverride/startOverride и ручные w:br/w:cr поддержаны. Специальные numbering styles вне decimal/upperLetter/lowerLetter требуют преобразования/ручной проверки. ZIP/CRC/XML/объём/отмена сохранены; новые зависимости не устанавливались.
- Пропущенные/повторные/неоднозначные фрагменты сохраняются в Quiz.importProblems, переживают JSON save/load и видны в редакторе. Декодирование/парсинг/просмотр не сохраняют тест в библиотеку автоматически.
- Фактически: 94 Android unit-тестов, 0 fail; Lint 0 errors/3 warnings; assembleDebug успешен. UniversityImportTest проверил TXT и DOCX на100/500/800/1500 вопросов с12вариантами, JSON roundtrip; новые tests WordNumberingTest/ImportReportTest/ImportSafetyRegressionTest. На телефоне не запускалось.

## Следующее
1. Выполнены локальный PDF API35+ (Android15) без новых зависимостей; bound64MB/5000pages/8mchars, отмена, запрет тихого частичного результата при пустой/сканированной странице. PdfImportPipelineTest проверяет текстовый конвейер, НЕ реальный системный PDF engine. На Android8–14 использовать существующий облачный PDF/JPG/PNG OCR с явным согласием; полный локальный PDF этих версий и локальный OCR ещё не реализованы. Нельзя объявлять их готовыми. Тяжёлый parser/OCR runtime без разрешения не добавлять.
2. Безметочные блоки распознаются лишь при вопросной форме и отдельной границе; кандидаты/обычные переносы с дефисом сохраняются как problem source для ручной проверки. Мягкий перенос склеивается; дефис сохраняется. Фрагменты и многоключевые данные переживают JSON. Реальные файлы пользователя/телефон/все варианты вёрстки PDF ещё нужны. Масштаб TXT/DOCX100/500/800/1500 с12вариантами проверен автоматически; device memory/time не измерены.
3. Google+Email welcome, первый обязательный вход, офлайн-сессия, UID-изоляция/явное закрепление старых файлов, профиль и ручной ZIP экспорт реализованы в следующем этапе ниже. Архитектура локального ИИ и многоключевое прохождение — следующие задачи.
4. Архитектуру локального ИИ и сравнение моделей делать после импорта/входа; никаких гигабайтных моделей или тяжёлых native runtime без разрешения. Не выдавать fake adapters за inference.
5. После крупного следующего этапа целевые/полные проверки, APK и commit/push. Публичный релиз не публиковать без отдельного разрешения.

Команда ближайшей проверки из корня:
& .\android\.build-tools\run-checks.ps1 -Tasks ':app:testDebugUnitTest','--tests','*WordNumberingTest','--tests','*ImportReportTest','--tests','*UniversityImportTest'
Полный этап: runner -Tasks '-Pkotlin.compiler.execution.strategy=in-process',':app:testDebugUnitTest',':app:lintDebug',':app:assembleDebug'; логи в ignored .build-tools, читать итог/хвост. Не повторять без изменения/ошибки.

## Облако и исходный релиз
Firebase testhelper-fe15b Spark и Worker testhelper-ai Free работают; не создавать заново. Production69d616ce-dd2d-4fd5-bc3b-26dc658d2804. 22ноября прошёл реальный existing-account→verified JWT→Worker→Gemini тест:HTTP200,2вопроса за3005мс без admin-кода. 33 backend tests ранее прошли. Secrets/SQLite/квоты/маршруты сохранять; документы не отправлять без согласия. Подробности docs/testhelper-0.4.0-verification.md.
Rollback с сохранением SQLite: android/.build-tools/wrangler-rollback.toml, version f426c75a-8802-41de-bd29-38b2d272bc75. Исходный backup worker-56dc4423-backup.mjs в ignored .build-tools. Не удалять namespace/class/migration и не читать/публиковать credentials.
Публичный android-v0.4.0-beta опубликован ранее; не заменять. SHA256 его APK731242cfd46abcfde0212ddf3c5b642604f0a637ddcc1ee93ed79e54ebe62430. Новая локальная сборка отличается и не является завершённой следующей beta.
Локальный промежуточный APK: android/app/build/outputs/apk/debug/app-debug.apk; 11089225 bytes; SHA256 3d4ea2af2552b93463b0ae37324bec6d062bbef0e937278ca8a7c3c02098c558. Version0.4.0/code6 пока не повышены, перед следующей beta повысить versionCode. Подпись сохраняется прежним debug keystore; реальное обновление/сохранность данных на телефоне ещё не проверены.
