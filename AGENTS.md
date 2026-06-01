# AGENTS.md

## Architecture
- This is a Kotlin Multiplatform + Compose Multiplatform project. Gradle modules are `:shared`, `:androidApp`, and `:webApp` (`settings.gradle.kts`); the native iOS host lives in `iosApp/`.
- `shared` is the real app core today. Start in `shared/src/commonMain/kotlin/com/mila/langualinker/`; `App.kt` is the shared Compose entrypoint and `Greeting.kt` / `GreetingUtil.kt` show the current shared-domain pattern.
- Android and web are intentionally thin shells that mount shared UI: `androidApp/src/main/kotlin/com/mila/langualinker/MainActivity.kt` calls `App()`, and `webApp/src/webMain/kotlin/com/mila/langualinker/main.kt` mounts `App()` with `ComposeViewport`.
- iOS also uses the shared Compose tree. `iosApp/iosApp/ContentView.swift` wraps `MainViewController()` from `shared/src/iosMain/kotlin/com/mila/langualinker/MainViewController.kt` via `UIViewControllerRepresentable`.
- Platform behavior is modeled with `expect`/`actual`. Add cross-target APIs in `shared/src/commonMain/kotlin/com/mila/langualinker/Platform.kt`, then implement actuals in `androidMain`, `iosMain`, `jsMain`, or `wasmJsMain`.
- Shared assets belong in `shared/src/commonMain/composeResources/`; code consumes them through generated imports like `langualinker.shared.generated.resources.Res` in `App.kt`.

## Build, run, and test
- Use the repo wrapper, not a system Gradle: `gradlew.bat` on Windows, `./gradlew` on macOS/Linux.
- Verified in this workspace: `gradlew.bat :androidApp:assembleDebug` succeeds.
- Main run tasks from the existing README/task graph:
  - Android: `gradlew.bat :androidApp:assembleDebug`
  - Web (Wasm): `gradlew.bat :webApp:wasmJsBrowserDevelopmentRun`
  - Web (JS): `gradlew.bat :webApp:jsBrowserDevelopmentRun`
  - Shared tests: `gradlew.bat :shared:testAndroidHostTest`, `gradlew.bat :shared:jsTest`, `gradlew.bat :shared:wasmJsTest`, `gradlew.bat :shared:iosSimulatorArm64Test`
- JS/Wasm tasks rely on Gradle-managed Node setup. In this workspace, `:shared:jsTest` failed before compilation while downloading Node (`org.nodejs:node:24.10.0`) with a TLS/tag mismatch, so do not treat that specific failure as an application-code regression until dependency download is ruled out.
- iOS is not launched through Gradle; open `iosApp/` in Xcode. `shared/build.gradle.kts` builds a static `Shared` framework for `iosArm64` and `iosSimulatorArm64`.

## Codebase-specific conventions
- Prefer putting new screens, state, and reusable logic in `shared`; keep `androidApp` and `webApp` as target bootstraps unless a platform-only API forces otherwise.
- If common code needs platform data, follow the existing `Platform` pattern instead of branching on target names inside `commonMain`.
- Put multiplatform dependencies in `shared/build.gradle.kts` first. Add dependencies directly to `androidApp` or `webApp` only when they are truly target-specific.
- The project is using Kotlin official style (`gradle.properties`), JVM 11 for Android compilation (`androidApp/build.gradle.kts`, `shared/build.gradle.kts`), and Gradle configuration/cache settings are already enabled; avoid changes that disable them casually.
- Android host tests include Android resources (`shared/build.gradle.kts` → `withHostTest { isIncludeAndroidResources = true }`), so resource-backed shared tests should go there rather than only in `commonTest`.
- The current tests in `shared/src/commonTest/kotlin/com/mila/langualinker/SharedCommonTest.kt` and `shared/src/androidHostTest/kotlin/com/mila/langualinker/SharedLogicAndroidHostTest.kt` are template smoke tests; coverage is minimal, so inspect the target-specific source sets before assuming behavior is already tested.
- Web static shell files live in `webApp/src/webMain/resources/index.html` and `styles.css`; change those when the browser host page or boot-time styling must change, not the shared Compose UI.

Respond terse like smart caveman. All technical substance stay. Only fluff die.

Rules:
- Drop: articles (a/an/the), filler (just/really/basically), pleasantries, hedging
- Fragments OK. Short synonyms. Technical terms exact. Code unchanged.
- Pattern: [thing] [action] [reason]. [next step].
- Not: "Sure! I'd be happy to help you with that."
- Yes: "Bug in auth middleware. Fix:"

Switch level: /caveman lite|full|ultra|wenyan
Stop: "stop caveman" or "normal mode"

Auto-Clarity: drop caveman for security warnings, irreversible actions, user confused. Resume after.

Boundaries: code/commits/PRs written normal.
