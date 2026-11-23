# TestHelper0.8.1 UI Beta — опубликовано

Отдельный публичный GitHub Pre-release: [https://github.com/ytkablack-hue/quizgenerator/releases/tag/android-v0.8.1-beta](https://github.com/ytkablack-hue/quizgenerator/releases/tag/android-v0.8.1-beta). Тег android-v0.8.1-beta, название TestHelper0.8.1 UI Beta. Коммит APK:fc5f4c922145874f9fa01ba1dd74c976e58b2f92, feature/android-mvp. UI остаётся2872325 с отчётом4d6cf03; единственное изменение приложения после этого — versionName0.8.1-beta/versionCode11. Package kz.quizgenerator, minSdk26,targetSdk36; миграции/дизайн/функции не менялись.

APK: [TestHelper-0.8.1-debug.apk](https://github.com/ytkablack-hue/quizgenerator/releases/download/android-v0.8.1-beta/TestHelper-0.8.1-debug.apk). Локально:C:/Projects/quizgenerator/android/app/build/outputs/apk/debug/app-debug.apk.

- Размер:50652109байт (48.31МиБ).
- SHA-256:5926b2fd969dbc59e6e32cf9fb4b310b9c85f9449988970464256a3dbaf2b9c3.
- Прежний сертификат SHA-256:710f7ec2403e51ae9423bc84de097ace7abb474d5f6d89c943d25075b17f333e.
- apksigner verify PASS; zipalign -c -P 16 4 PASS; aapt2 подтвердил package/versionName/versionCode.
- :app:testDebugUnitTest :app:lintDebug :app:assembleDebug — BUILD SUCCESSFUL,2m11s. Lint0errors/7warnings. Unit task UP-TO-DATE:206прошедших тестов UI-этапа, повторно в этой задаче не запускались.
- GitHub asset digest совпал с локальным SHA до публикации. После публикации выполнено полное анонимное скачивание:HTTP200,размер и SHA-256 совпали.
- Проверены tag→commit,pre-release=true,неизменность main и метаданных/asset ID/размеров/digest всех9предыдущих релизов. Предыдущие релизы не заменялись и не удалялись. APK не добавлен в Git.

В release notes явно указано:это тестовая версия Stitch, визуально на настоящем Android ещё НЕ проверена. Для проверки устанавливать поверх0.8.0 без удаления/очистки данных:package/сертификат сохранены,versionCode11>10. Фактическое обновление, интерфейс,360dp/1.3x/темы/скролл и действия требуют телефонной приёмки; компьютерная сборка их не подтверждает.

Production/Firebase/Cloudflare/SQLite/секреты/квоты не изменялись. /lectures и /answers не развёрнуты, ожидают отдельного разрешения. Новая разработка не выполнялась. Следующий шаг — скачать APK на телефон и проверить дизайн; новые релизы и production-изменения требуют отдельного согласия.
