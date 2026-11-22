# TestHelper 0.7.0 Beta — контрольная точка

Проект C:/Projects/quizgenerator, ветка feature/android-mvp. База 0.6.0 b599fb1. Этапы: ec8582b (три режима AI), 3d8b0d1 (Stitch shell), 52bbeae (режимы/закладки/время/карточки), текущий коммит — финальная маршрутизация, cloud provenance и versionCode9.

Внедрены нативные экраны Stitch7–13. AiGeneration/AiInputMode отделены от UI, TOPIC не включает документ. Черновик хранит режим/свой текст независимо от sourceId; явный выбор документа учитывается до окончания асинхронного bind. Экзамен не раскрывает ключи/подсказки, сохраняет режим/закладки/таймер/время; старые попытки мигрируют без выдуманной длительности. Есть переход/повтор закладок, трудные карточки и карточки из подтверждённых ошибок. Генерация всегда CLOUD_AI независимо от присланной evidence.

Финально выполнены 173 Android unit-теста (0 ошибок/пропусков), 34 локальных Worker security tests, Lint (0 ошибок,7 предупреждений), assembleDebug. Версия0.7.0-beta/code9; package kz.quizgenerator, min26/target36. apksigner v2/прежний сертификат и zipalign -P16 прошли.
APK: C:/Projects/quizgenerator/android/app/build/outputs/apk/debug/app-debug.apk
Размер51214575байт; SHA25640f6ed4b871b3bfc5be99f33ea9c4f1f709e4e36af3b89301676abd64920ad40.

Далее только итоговый отчёт/точный SHA исходников и обычный push, затем телефонный checklist docs/REMAINING.md. Отчёт проверки: docs/testhelper-0.7.0-verification.md (будет добавлен следующим коммитом). План AI: docs/ai-next-stage.md; карта Stitch: docs/stitch-0.7.0.md. Не продолжать разработку с нуля/не запускать сборку неизменённых исходников.

После новых изменений:
```powershell
& .\android\.build-tools\run-checks.ps1 -Tasks '-Pkotlin.compiler.execution.strategy=in-process',':app:testDebugUnitTest',':app:lintDebug',':app:assembleDebug'
```
Логи ignored android/.build-tools/070-final-checks.log и 070-worker-tests.log. Реальные Google/OCR/Compose360dp/большой шрифт/обновление и Gemini из нового UI требуют телефона. Layoutlib не установлен; эмулятор/ADB не запускать. Production/Firebase/SQLite/secrets/квоты/main/старую02_MyWork не менять, LLM/зависимости не скачивать. Новый публичный Release0.7 не разрешён и не создан. APK/SDK/JDK/кэши/локальные настройки не коммитить. Сохранять чужие изменения, никаких force/reset/clean.
