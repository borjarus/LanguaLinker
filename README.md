# 🚀 LanguaLinker

> Language learning app built with **Kotlin Multiplatform (KMP)** & **Compose Multiplatform**. Shares UI + logic across Android, Web (JS/Wasm), and iOS.

***

## 📋 Table of Contents

- [About](#about)
- [Architecture](#architecture)
- [Requirements](#requirements)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [Build & Run](#build--run)
- [Modules](#modules)
- [Tech Stack](#tech-stack)
- [Testing](#testing)
- [Development](#development)

***

## 📖 About

**LanguaLinker** — memory-based language learning. Shared Compose UI runs natively on Android, web, and iOS (bridged via SwiftUI).

Cross-platform sharing:
- **UI** — Compose Multiplatform (composable components)
- **Business logic** — models, states, platform APIs
- **Networking & persistence** — abstracted via `expect`/`actual`

***

## 🏛️ Architecture

Modular KMP layout:

```
shared/                    Core domain + UI tree (Compose)
├── commonMain/           Shared code (all platforms)
├── androidMain/          Android actuals
├── iosMain/              iOS framework export
├── jsMain/               JS backend
├── wasmJsMain/           Wasm backend
├── androidHostTest/      Android-specific tests
└── commonTest/           Shared unit tests

androidApp/               Android thin shell → App()
webApp/                   Web thin shell → App() + index.html
iosApp/                   iOS thin shell (Swift) → MainViewController()
```

State: `remember` + `mutableStateOf`. Platform divergence via `expect fun getPlatform(): Platform`.

***

## ✅ Requirements

| Tool           | Version     |
|---|---|
| JDK            | 11+         |
| Kotlin         | 2.3.21      |
| Gradle         | wrapper (8.5+) |
| Android SDK    | compileSdk 36 |
| Xcode          | 15.0+       |
| Node.js        | 24.10.0+ (for JS/Wasm) |

***

## 📁 Project Structure

```
LanguaLinker/
├── shared/                              Core KMP module
│   ├── src/
│   │   ├── commonMain/kotlin/
│   │   │   └── com/mila/langualinker/
│   │   │       ├── App.kt               Shared Compose root
│   │   │       ├── Greeting.kt          State example
│   │   │       └── Platform.kt          expect/actual interface
│   │   ├── androidMain/                 Android actuals
│   │   ├── iosMain/                     iOS framework (Shared.framework)
│   │   ├── jsMain/                      JS target
│   │   ├── wasmJsMain/                  Wasm target
│   │   ├── androidHostTest/             Android+resources tests
│   │   ├── commonTest/                  Shared tests
│   │   └── composeResources/            Shared assets (icons, strings, etc.)
│   └── build.gradle.kts
│
├── androidApp/                          Android shell
│   ├── src/main/kotlin/
│   │   └── com/mila/langualinker/
│   │       └── MainActivity.kt          Calls App()
│   └── build.gradle.kts
│
├── webApp/                              Web shell (JS/Wasm)
│   ├── src/webMain/
│   │   ├── kotlin/
│   │   │   └── com/mila/langualinker/
│   │   │       └── main.kt              ComposeViewport(App())
│   │   └── resources/
│   │       ├── index.html               Entry HTML
│   │       └── styles.css
│   └── build.gradle.kts
│
├── iosApp/                              iOS shell (Swift)
│   ├── iosApp/
│   │   ├── iOSApp.swift                 SwiftUI root
│   │   ├── ContentView.swift            UIViewControllerRepresentable bridge
│   │   └── Info.plist
│   └── iosApp.xcodeproj/
│
├── gradle/
│   ├── libs.versions.toml               Centralized versions (Kotlin 2.3.21, Compose 1.11.0)
│   └── wrapper/
│
├── build.gradle.kts                     Root plugins (apply false)
├── settings.gradle.kts                  Includes: :androidApp, :shared, :webApp
├── gradlew.bat / gradlew                Build wrapper
└── README.md (this file)
```

***

## 🚀 Getting Started

### Prerequisites
- JDK 11+ installed
- Gradle wrapper (included in repo)
- For Android: Android SDK (API 24+)
- For web: Node.js 24.10.0+ (Gradle downloads it)
- For iOS: Xcode 15.0+

### Clone & Setup
```bash
git clone <repo>
cd LanguaLinker
```

### Verify Setup
```bash
./gradlew.bat --version      # Windows
./gradlew --version          # macOS/Linux
```

***

## 🔨 Build & Run

### Android
```bash
./gradlew.bat :androidApp:assembleDebug      # Build APK
./gradlew.bat :androidApp:installDebug       # Install on device/emulator (if connected)
```

### Web (Wasm)
```bash
./gradlew.bat :webApp:wasmJsBrowserDevelopmentRun
# Opens http://localhost:8080 (hot reload enabled)
```

### Web (JS)
```bash
./gradlew.bat :webApp:jsBrowserDevelopmentRun
# Opens http://localhost:8080
```

### Shared Tests
```bash
./gradlew.bat :shared:testAndroidHostTest        # Android + resources
./gradlew.bat :shared:jsTest                     # JS target
./gradlew.bat :shared:wasmJsTest                 # Wasm target
./gradlew.bat :shared:iosSimulatorArm64Test      # iOS simulator
```

### iOS
```bash
cd iosApp
open iosApp.xcodeproj
# Or from Xcode: Product → Run (Cmd+R)
# Builds :shared static framework (Shared.framework) via Gradle
```

***

## 📦 Modules

| Module | Role | Depends on |
|---|---|---|
| `shared` | Core: Compose UI, state, platform APIs | gradle libs |
| `androidApp` | Android entry point | shared |
| `webApp` | Web entry point (JS/Wasm) | shared |
| `iosApp` | iOS entry point (Swift) | shared (framework) |

***

## 🛠️ Tech Stack

### Core
- **Kotlin Multiplatform** (2.3.21)
- **Compose Multiplatform** (1.11.0)
- **Gradle** (wrapper 8.5+)

### Android
- compileSdk 36, minSdk 24, targetSdk 36
- AndroidX (lifecycle, activity, appcompat)
- Compose Material 3

### Web
- JS + Wasm backends
- Node.js 24.10.0 (gradle-managed)
- Kotlin browser wrappers

### iOS
- SwiftUI shell → shared Compose tree
- Static Xcode framework (iosArm64 + iosSimulatorArm64)

### Build Plugins
- `org.jetbrains.compose` (Compose Multiplatform)
- `org.jetbrains.kotlin.multiplatform` (KMP)
- `com.android.application` / `com.android.library` (Android)
- `org.jetbrains.kotlin.plugin.compose` (Compiler)

***

## 🧪 Testing

Organized by platform:
- `commonTest/` — Shared unit tests (runs on all targets)
- `androidHostTest/` — Android tests with resources
- `jsTest/` — JS target tests
- `wasmJsTest/` — Wasm target tests
- `iosTest/` — iOS simulator tests

Run all:
```bash
./gradlew.bat :shared:allTests
```

***

## 👷 Development

### Add Shared Code
New logic goes in `shared/src/commonMain/kotlin/` by default.

### Platform-Specific Logic
Define `expect` in `commonMain`, then implement `actual` in:
- `androidMain/` → Android
- `iosMain/` → iOS framework
- `jsMain/` → JS
- `wasmJsMain/` → Wasm

Example: `shared/src/commonMain/kotlin/com/mila/langualinker/Platform.kt`
```kotlin
expect fun getPlatform(): Platform
```

Implement in each target's actual source set.

### Update Dependencies
Edit `gradle/libs.versions.toml` (centralized), then sync Gradle.

### Hot Reload
- **Android**: Use Android Studio's "Apply Code Changes"
- **Web**: Dev server auto-reloads (Wasm/JS)
- **iOS**: Rebuild in Xcode

***

## 📝 Notes

- `shared` is the real app core; Android/web/iOS are thin shells
- Compose tree is single-source; each platform mounts `App()`
- Assets in `shared/src/commonMain/composeResources/` — auto-generated as `langualinker.shared.generated.resources.Res`
- Gradle cache enabled (`gradle.properties`); avoid disabling it
- Kotlin official style enforced
- JVM 11 target for Android compilation

***

## ⚙️ Getting Started

### Clone the repository

```bash
git clone https://github.com/username/myapp.git
cd myapp
```

### Android

```bash
./gradlew :composeApp:assembleDebug
# or run the 'composeApp' configuration in Android Studio
```

### Desktop (JVM)

```bash
./gradlew :composeApp:run
```

### iOS

```bash
cd iosApp
pod install
open iosApp.xcworkspace
# Run the project in Xcode (⌘+R)
```

> **Note:** Building for iOS requires macOS with Xcode installed.

***

## 📦 Modules

### `shared`

Contains all platform-independent business logic:

- **`domain/`** — domain entities, repository interfaces, use cases
- **`data/`** — repository implementations, HTTP clients (Ktor), local database (SQLDelight)
- **`di/`** — Koin modules for Dependency Injection

### `composeApp`

Shared presentation layer for Android and Desktop:

- Screens and components in **Compose Multiplatform**
- **ViewModel** based on `kotlinx.coroutines`
- Navigation via **Decompose** or **Navigation Compose**

### `iosApp`

Native SwiftUI application integrating with the `shared` module via the generated Kotlin/Native framework.

***

## 🛠️ Tech Stack

| Category              | Library / Tool                  | Version  |
|-----------------------|---------------------------------|----------|
| UI                    | Compose Multiplatform           | 1.7+     |
| Networking            | Ktor Client                     | 3.0+     |
| Database              | SQLDelight                      | 2.0+     |
| DI                    | Koin                            | 4.0+     |
| Serialization         | kotlinx.serialization           | 1.7+     |
| Async                 | kotlinx.coroutines              | 1.9+     |
| Navigation            | Decompose                       | 3.0+     |
| Logging               | Napier                          | 2.7+     |
| Testing               | kotlin.test + Turbine           | latest   |
| Build                 | Gradle + Version Catalog        | 8.5+     |

***

## 🧪 Testing

### Run unit tests (shared module)

```bash
./gradlew :shared:testDebugUnitTest          # Android JVM
./gradlew :shared:iosSimulatorArm64Test      # iOS Simulator (macOS only)
./gradlew :shared:desktopTest                # Desktop JVM
```

### UI tests (Android)

```bash
./gradlew :composeApp:connectedAndroidTest
```

Shared tests in `commonTest` use `kotlin.test` and run on all target platforms.

***

## 🔄 CI/CD

The project uses **GitHub Actions** with the following workflows:

- **`build.yml`** — compile and test on every push/PR (Android + Desktop)
- **`ios.yml`** — iOS build on a macOS runner (main branch only)
- **`release.yml`** — automated release creation and Google Play publishing

Example workflow:

```yaml
# .github/workflows/build.yml
name: Build & Test

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Build & Test
        run: ./gradlew build :shared:allTests
```

***

## 🤝 Contributing

1. **Fork** the repository
2. Create a branch: `git checkout -b feature/my-feature`
3. Make your changes and add tests
4. Open a **Pull Request** with a clear description

Before submitting a PR, make sure:
- [ ] Code compiles on all target platforms
- [ ] All unit tests pass
- [ ] New features are covered by tests
- [ ] Code style is consistent with `ktlint`

***

## 📄 License

```
MIT License

Copyright (c) 2026 [Your Name]

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
provided, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.
```

***

<p align="center">Built with ❤️ using Kotlin Multiplatform</p>