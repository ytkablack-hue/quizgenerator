# TestHelper — главная после замечаний к 0.7.0

Ветка feature/android-mvp, база05266af. Изменены только StudySetHome.kt, оформление шапки/нижней навигации/размер главной в MainActivity.kt, debug-only HomeStitchPreviews.kt и HomePresentationTest.kt. Остальные шесть экранов, общие SoftStudy/SteamComponents/SteamTheme, storage/auth/AI/backend/квоты не менялись.

Главная следует композиции testhelper_8: компактная шапка с реальными профилем/историей, полный приветственный текст/контекст, поиск48dp без floating label, реальный опциональный прогресс, один blue create-block, компактные наборы/меню/счётчики/«Учить». Пустые строки истории убраны. Rename/export/archive/restore/open/search сохранены, поведение хранилища не менялось. У навигации64dp минимум плюс системный inset, четыре маршрута прежние. У текста свободный перенос и системный fontScale. Спецификация и сравнение размеров: docs/home-stitch-review.md.

178 Android unit-тестов,0 ошибок/пропусков. Lint0ошибок/7прежних предупреждений. assembleDebug успешен; подпись прежняя/zipalign16КБ проверены. APK: C:/Projects/quizgenerator/android/app/build/outputs/apk/debug/app-debug.apk,51265249байт, SHA256711422bde517cfd42e68a911d596797800b772a932d31fc13811197ee33d6078. versionName0.7.0-beta/code9 не повышались. Новый Release не создан, опубликованный0.7.0 остаётся прежним.

Реального Android-снимка НЕТ. Четыре Preview360dp (light/dark,1.0/1.3x) скомпилированы, но не отрисованы. Android Studio установлена; Computer Use helper дважды не запустился с `failed to write kernel assets ... os error3`, включая reset/retry. Не повторять это подключение циклически и не выдавать PNG эталона за рендер приложения. Не запускать эмулятор/ADB/установки.

Следующий шаг — утверждение главной владельцем на настоящем телефоне: новый локальный APK поверх старого без удаления данных, ширина360dp/шрифт1.0/1.3/light/dark, длинные имена/заголовки, один набор/одна карточка/пусто/много, поиск/корзина/все действия и системные отступы. Скриншоты телефона в сообщениях/ZIP не предоставлены; запрошен их локальный путь. До утверждения НЕ переделывать остальные шесть экранов. Main/production/Firebase/SQLite/secrets/подпись не менять. APK/кэши/SDK/JDK в Git не добавлять.

Проверки после новых исходных изменений (не пересобирать текущий APK ради документов):
```powershell
& .\android\.build-tools\run-checks.ps1 -Tasks '-Pkotlin.compiler.execution.strategy=in-process',':app:testDebugUnitTest',':app:lintDebug',':app:assembleDebug'
```
Лог: android/.build-tools/home-stitch-final.log. Детали прошлой функциональности0.7: docs/testhelper-0.7.0-verification.md; известные ограничения: docs/REMAINING.md; планAI: docs/ai-next-stage.md. Источник APK — коммит исправления главной; точный SHA `git log -1 --format=%H` после сохранения этого этапа.
