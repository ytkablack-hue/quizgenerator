# TestHelper UI module

Reusable light Steam-inspired Jetpack Compose interface. The module owns visual tokens, shared controls and presentation screens; `:app` supplies application state, storage and actions through callbacks.

## Sources

- `theme/SteamTheme.kt` — palette and Material 3 theme.
- `components/SteamComponents.kt` — header, fields, cards, buttons, progress and page layout.
- `screens/AccountScreens.kt` — registration and login.
- `screens/CreationScreens.kt` — test library list.
- `screens/StudyScreens.kt` — learning, lecture stages, flashcards and knowledge checks.

The module has no application ID or data store. Add `implementation(project(":ui"))` to an Android Compose app.
