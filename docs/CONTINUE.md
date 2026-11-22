# TestHelper — продолжение

Работа: C:/Projects/quizgenerator, feature/android-mvp. Правила/карта/команды: AGENTS.md. 02_MyWork/android не изменять; без эмулятора/ADB, платных услуг и новых публичных Releases.

## Последний завершённый этап: TXT + DOCX и сохранение фрагментов
22 ноября 2026. Исходная точка этапа1c6438f (TXT); этап0 f68975a. OpenCode a2f14ce и be3aa02 сохранены в истории и отправлены без переписывания.
- TXT: UTF-8/BOM/UTF-16LE/BE/Windows-1251, строгие ошибки вместо потери символов; ручной выбор и предпросмотр кодировки с подтверждением перечитывания. Windows-1251 не содержит казахских букв: уже потерянные при записи символы восстановить нельзя; для казахского использовать Unicode.
- Ключи до/после вопросов, частичные/конфликтующие ключи не становятся подтверждёнными; JSON multi-answer не приводит строки/дроби к int. Множественный ключ хранится, но существующая single-select сессия пока исключает такие вопросы из оценки — полноценный multi-select ещё предстоит.
- DOCX: word/document.xml + numbering.xml, цифровая/буквенная нумерация, вложенные списки/перезапуск, startOverride; таблицы, merged cells и split runs. Не все специальные Word numbering styles поддержаны. ZIP/CRC/XML/объём/отмена сохранены; новые зависимости не устанавливались.
- Пропущенные/повторные/неоднозначные фрагменты сохраняются в Quiz.importProblems, переживают JSON save/load и видны в редакторе. Декодирование/парсинг/просмотр не сохраняют тест в библиотеку автоматически.
- Фактически: 87 Android unit-тестов, 0 fail; Lint 0 errors/3 warnings; assembleDebug успешен. UniversityImportTest проверил TXT и DOCX на800/1500 вопросов с12вариантами, JSON roundtrip; новые tests WordNumberingTest/ImportReportTest/ImportSafetyRegressionTest. На телефоне не запускалось.

## Следующее
1. Продолжить DOCX: full lvlOverride, явные Word line breaks. Скрипт подготовки, ещё НЕ выполнен: C:/Users/Admin/.codex/visualizations/2026/11/17/01a1ea96-ad51-7301-b601-fecc6464ec24/testhelper-pdf.cjs — просмотреть перед запуском. Предлагает системный local text PDF только Android15+/API35 без тяжёлой зависимости, pipeline tests, ещё100/500-вопросные fixtures. Android8–14 local PDF потребует отдельного поддерживаемого решения; облачный OCR PDF/JPG/PNG уже есть с явным согласием, его не дублировать и не выдавать за офлайн. Текстовое PDF/scan/OCR на устройстве ещё не проверены.
2. Доработать неоднозначные unlabeled blocks без догадок: сохранять raw и требовать review. Нужны регрессии смешанных форматов/переносов, производительности и отмены.
3. Только затем Google+Email welcome, обязательный первый вход, офлайн-сессия/изоляция данных, профиль и архитектура локального ИИ. Эти этапы НЕ выполнены. Не блокировать/не переносить старые данные без проверенной миграции. В текущем Worker provider=password; Google потребует согласованного изменения validator и отдельного разрешения на production rollout.
4. Архитектуру локального ИИ и сравнение моделей делать после импорта/входа; никаких гигабайтных моделей или тяжёлых native runtime без разрешения. Не выдавать fake adapters за inference.
5. После крупного следующего этапа целевые/полные проверки, APK и commit/push. Публичный релиз не публиковать без отдельного разрешения.

Команда ближайшей проверки из корня:
& .\android\.build-tools\run-checks.ps1 -Tasks ':app:testDebugUnitTest','--tests','*WordNumberingTest','--tests','*ImportReportTest','--tests','*UniversityImportTest'
Полный этап: runner -Tasks ':app:testDebugUnitTest',':app:lintDebug',':app:assembleDebug'; логи в ignored .build-tools, читать итог/хвост. Не повторять без изменения/ошибки.

## Облако и исходный релиз
Firebase testhelper-fe15b Spark и Worker testhelper-ai Free работают; не создавать заново. Production69d616ce-dd2d-4fd5-bc3b-26dc658d2804. 22ноября прошёл реальный existing-account→verified JWT→Worker→Gemini тест:HTTP200,2вопроса за3005мс без admin-кода. 33 backend tests ранее прошли. Secrets/SQLite/квоты/маршруты сохранять; документы не отправлять без согласия. Подробности docs/testhelper-0.4.0-verification.md.
Rollback с сохранением SQLite: android/.build-tools/wrangler-rollback.toml, version f426c75a-8802-41de-bd29-38b2d272bc75. Исходный backup worker-56dc4423-backup.mjs в ignored .build-tools. Не удалять namespace/class/migration и не читать/публиковать credentials.
Публичный android-v0.4.0-beta опубликован ранее; не заменять. SHA256 его APK731242cfd46abcfde0212ddf3c5b642604f0a637ddcc1ee93ed79e54ebe62430. Новая локальная сборка отличается и не является завершённой следующей beta.
Локальный промежуточный APK: android/app/build/outputs/apk/debug/app-debug.apk; 11089225 bytes; SHA256 573d00500ce085a0a1823f1baa19a19d17f7dc2fb3a8fa9c90191688957d17c8. Version0.4.0/code6 пока не повышены, перед следующей beta повысить versionCode. Подпись сохраняется прежним debug keystore; реальное обновление/сохранность данных на телефоне ещё не проверены.
