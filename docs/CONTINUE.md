# TestHelper — контрольная точка 22.11.2026

Проект: C:/Projects/quizgenerator. Ветка: feature/android-mvp. Исходники APK: 98fb0e39decbc098b6c3522de32bf945219b939f. Последующий коммит содержит только итоговые документы. Старую 02_MyWork/android не изменять.

## Завершено

- b132816: полные оригиналы и извлечённый текст отдельно от ручного поля, атомарное account-scoped хранение с проверкой целостности.
- 08ba8eb: карточки вложений, восстановление выбора/черновика, просмотр, полные локальные документы и явные ограниченные запросы Gemini.
- 75d71e8: общая светло-голубая тема и собственный знак, компактный Google/Email-вход; dark/system и прежняя авторизация сохранены.
- 5c6d2f3: настоящая библиотека/поиск/статистика, наборы Материал–Тест–Карточки–Подготовка, архив/восстановление, ZIP-экспорт, оригинальный PDF/фото и текст.
- 98fb0e3: разбор всех вопросов/повтор ошибок, завершение карточного занятия, история успешных частей Gemini, предпросмотр облачного текста, этапы загрузки и сообщения о повреждённых банках. Фоновая запись больших тестов/попыток и защита от восстановления отменённого снимка.

## Финальные проверки

153 Android unit-теста: 0 failures/errors/skipped. Lint: 0 ошибок, 7 предупреждений. assembleDebug: BUILD SUCCESSFUL. Версия 0.6.0-beta, code 8, package kz.quizgenerator, minSdk 26, targetSdk 36. APK проверен apksigner (v2, прежний debug-сертификат) и zipalign -P 16.

APK: C:/Projects/quizgenerator/android/app/build/outputs/apk/debug/app-debug.apk
Размер: 50 290 781 байт. SHA-256: 11c2a2055627eca4324e80831a1bd01a2520daa647b25bb9a7757ac87e28e421.

Полный отчёт: docs/testhelper-0.6.0-verification.md. Ограничения и телефонный checklist: docs/REMAINING.md. Дизайн/16 референсов: docs/ux-design-notes.md.

## Продолжение

Следующий шаг — проверка APK на настоящем телефоне по REMAINING, установка поверх существующей версии без удаления данных. Реальные Google Credential Manager, native OCR/PDF, обновление и Compose lifecycle/визуальное поведение на устройстве НЕ проверены. Новых live-запросов к production в этой задаче не делали; сервер не менялся.

Начать с git status, этого файла и точечного diff. Не повторять полный аудит или сборку неизменённых исходников. После изменений команда полного этапа из корня:

```powershell
& .\android\.build-tools\run-checks.ps1 -Tasks '-Pkotlin.compiler.execution.strategy=in-process',':app:testDebugUnitTest',':app:lintDebug',':app:assembleDebug'
```

Последний лог: android/.build-tools/final-060-checks.log (игнорируется). Не запускать эмулятор/ADB, не скачивать LLM, не менять production/Firebase/Worker/SQLite/secrets/тарифы/main. Новый Release не разрешён и не создан; публичная версия остаётся 0.5.0. Не коммитить APK/SDK/JDK/кэши/.build-tools/локальные настройки. После изменений — целевые проверки, staged diff, commit и обычный push; при расхождении веток остановиться перед merge/rebase.
