# GoEpub — One-Tap EPUB E-Book Translator for Android

[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-0D9488?style=flat-square&logo=android)](https://github.com/bamboysan/GoEpub)
[![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-0284C7?style=flat-square&logo=kotlin)](https://kotlinlang.org/)
[![License](https://img.shields.io/badge/License-MIT-F59E0B?style=flat-square)](LICENSE)
[![Release](https://img.shields.io/github/v/release/bamboysan/GoEpub?style=flat-square&color=10B981&label=Latest%20APK)](https://github.com/bamboysan/GoEpub/releases/latest)
[![Ko-fi](https://img.shields.io/badge/Ko--fi-Support%20Developer-FF5E5B?style=flat-square&logo=ko-fi&logoColor=white)](https://ko-fi.com/bamboysan)
[![Saweria](https://img.shields.io/badge/Saweria-QRIS%20%2F%20E--Wallet-F59E0B?style=flat-square)](https://saweria.co/bamboysan)

**GoEpub** is a fast, lightweight, 100% free, ad-free, and open-source native Android application built with **Kotlin**, **Jetpack Compose (Material 3)**, and **MVVM architecture** to automatically translate entire **EPUB (EPUB 2 & EPUB 3)** e-books into your preferred language in a single tap — while keeping original HTML/CSS styling, images, covers, and table of contents completely intact.

---

## 📥 Download APK

Ready to install on any Android 8.0+ (API 26+) device — no API keys, subscriptions, or sign-ups required:

👉 **[Download GoEpub v1.1.0 APK (Latest Release)](https://github.com/bamboysan/GoEpub/releases/latest/download/GoEpub-v1.1.0.apk)**

Or browse all versions on the **[GitHub Releases Page](https://github.com/bamboysan/GoEpub/releases)**.

### How to Install the `.apk` on Android
1. Download **`GoEpub-v1.1.0.apk`** on your Android phone using the link above.
2. Tap the downloaded `.apk` file in your browser or **Files / Downloads** app.
3. If prompted for security permissions, tap **Settings** and enable **"Allow from this source"** (*Install Unknown Apps / Install from Unknown Sources*).
4. Tap **Install**, then **Open** to start translating your EPUB books!

---

## ✨ Key Features

- **One-Tap Whole-Book Translation**: Pick any DRM-free `.epub` file, choose your source language (or leave it on **Auto-Detect**) and target language, and tap **Start Translation**.
- **100% Original Formatting Preserved**:
  - Parses chapter `.xhtml` / `.html` files using **Jsoup XML mode** (`prettyPrint(false)` + strict `EscapeMode.xhtml`).
  - Translates *only* readable text nodes while leaving HTML tags, inline styles (`<b>`, `<i>`, `<em>`, `<a>`), CSS stylesheets, SVG/raster images, HTML entities (`&amp;`, `&nbsp;`), and navigation metadata (`toc.ncx` & `nav.xhtml`) untouched.
  - Repackages valid **OCF 3.3 EPUB** archives (`mimetype` stored uncompressed first via `ZipEntry.STORED` with zero extra header bytes).
- **100% Free, Private & Offline-Capable**:
  - Powered by **Google ML Kit On-Device Translation** & **Language Identification** — zero API rate limits, zero cloud costs, and your books never leave your phone.
  - Built-in **Offline Language Pack Manager** to pre-download or delete ~30 MB language models across 20+ supported languages.
- **Reliable Background Execution**:
  - Runs via Android **`WorkManager` (`CoroutineWorker` + Foreground Service)** so long novel translations continue smoothly even if you lock your screen or switch apps, showing live percentage and chapter title progress.
- **Bilingual App Interface (`EN` / `ID`)**:
  - Clean, bright, minimalist Material 3 UI available in **English (Default)** and **Bahasa Indonesia**, switchable with a single tap in the top bar.
- **Modular Translation Engine**:
  - Clean `TranslationEngine` interface with `GoogleTranslateManager` (on-device default) and `CloudTranslationEngine` (ready-to-use adapter for Google Cloud Translation API v2).

---

## 💖 Support the Developer

**GoEpub** is 100% free, ad-free, and open-source. If this project helps you read and enjoy more books across languages, consider buying the developer a coffee to support continued maintenance and new features:

- ☕ **Ko-fi (International — PayPal / Credit & Debit Card):** [https://ko-fi.com/bamboysan](https://ko-fi.com/bamboysan)
- 🇮🇩 **Saweria (Indonesia — QRIS / GoPay / OVO / DANA):** [https://saweria.co/bamboysan](https://saweria.co/bamboysan)

[![Support on Ko-fi](https://img.shields.io/badge/Support%20on%20Ko--fi-bamboysan-FF5E5B?style=for-the-badge&logo=ko-fi&logoColor=white)](https://ko-fi.com/bamboysan)
[![Support on Saweria](https://img.shields.io/badge/Support%20on%20Saweria-QRIS%20%26%20E--Wallet-F59E0B?style=for-the-badge)](https://saweria.co/bamboysan)

---

## 🛠️ Tech Stack & Architecture

- **Language & Concurrency**: Kotlin, Coroutines (`suspendCancellableCoroutine`), StateFlow / SharedFlow
- **UI**: Jetpack Compose (Material 3), Edge-to-Edge, Adaptive + Android 13+ Monochrome Icon
- **Architecture**: Clean MVVM (`ui` → `worker` / `data` → `domain`)
- **Background Jobs**: AndroidX `WorkManager` (`work-runtime-ktx`)
- **EPUB & XHTML Engine**: `java.util.zip` (OCF-compliant packaging + Zip-Slip protection + DRM detection) + **Jsoup**
- **Translation & Language ID**: Google ML Kit (`com.google.mlkit:translate` & `com.google.mlkit:language-id`)

```text
app/src/main/java/com/goepub/app/
├── MainActivity.kt                          # Edge-to-Edge host, SAF OpenDocument & runtime permissions
├── data/
│   └── TranslationHistoryRepository.kt      # Local translated book history in app-private storage
├── domain/
│   ├── epub/
│   │   ├── EpubModels.kt                    # Book metadata, translation stages, & typed EPUB errors
│   │   ├── EpubProcessor.kt                 # Safe ZIP extract, DRM check, Jsoup XML parser, OCF repack
│   │   └── TextChunker.kt                   # Smart sentence-boundary chunker & inline space preserver
│   └── translator/
│       ├── TranslationEngine.kt             # Modular translation engine contract
│       ├── GoogleTranslateManager.kt        # ML Kit On-Device wrapper + offline model manager
│       └── CloudTranslationEngine.kt        # Optional Google Cloud Translation API v2 implementation
├── util/
│   └── AppConstants.kt                      # Centralized donation & project URLs + safe browser intent
├── worker/
│   └── TranslateEpubWorker.kt               # Foreground CoroutineWorker with live chapter progress
└── ui/
    ├── components/
    │   └── GoEpubComponents.kt              # Custom vector brand mark, support, offline pack & privacy sheets
    ├── i18n/
    │   └── AppStrings.kt                    # Bilingual UI localization (English default & Indonesian)
    ├── main/
    │   ├── MainScreen.kt                    # Minimalist Material 3 screens (Idle -> Success/Error)
    │   └── MainViewModel.kt                 # MVVM state machine & WorkManager Flow observer
    └── theme/
        ├── Color.kt                         # Bright Sky-Teal, Warm Amber, & Cloud Paper palette
        └── Theme.kt                         # Light/Dark Material 3 color schemes & typography
```

---

## 🚀 Clone & Build from Source

### Prerequisites
- **Android Studio** (Ladybug / 2024.2+ or newer)
- **JDK 17+** (or Android Studio bundled JBR)
- **Android SDK Platform 34+**

### Build Steps

1. **Clone the repository:**
   ```bash
   git clone https://github.com/bamboysan/GoEpub.git
   cd GoEpub
   ```

2. **Build the Debug APK via Gradle Wrapper:**
   - **Linux / macOS:**
     ```bash
     ./gradlew assembleDebug
     ```
   - **Windows (PowerShell / CMD):**
     ```powershell
     .\gradlew.bat assembleDebug
     ```
   The compiled APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`.

3. **Run Unit Tests:**
   ```bash
   ./gradlew testDebugUnitTest
   ```

---

## 🔒 Privacy & License

- **Privacy Policy**: GoEpub processes all e-book files strictly on-device. Read the full details in [PRIVACY_POLICY.md](PRIVACY_POLICY.md).
- **License**: Distributed under the [MIT License](LICENSE).
