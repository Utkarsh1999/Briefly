# Briefly

On-device notification summarizer for Android. Briefly listens to your incoming
notifications, clusters them by app and conversation, and uses a local LLM
(via [llama.cpp](https://github.com/ggerganov/llama.cpp)) to produce a short
digest — without sending anything off the device.

> Internal package name is still `com.notifyai` (the project was renamed mid-way).

> **Notes on what shipping this actually looked like** — capability probe
> design, hardware ceilings, the 1 GB → 114 MB APK story, with logcat and
> code refs: **[NOTES.md](NOTES.md)**.

## Features

- **NotificationListenerService** captures incoming notifications and stores them in Room.
- **Clustering** groups notifications by app / conversation thread.
- **On-device summarization** runs a quantized GGUF model through llama.cpp.
  Falls back to a heuristic summary on devices that can't sustain real-time inference.
- **WorkManager** runs periodic summarization in the background.
- **Jetpack Compose UI** with Home, History, and Insights screens.

## Tech stack

- Kotlin + Jetpack Compose, Material 3
- Hilt (DI), Room (storage), WorkManager (background), DataStore (preferences)
- llama.cpp (C++ inference, built via CMake / NDK)
- Min SDK 29, target SDK 35

## Project layout

```
app/src/main/
├── cpp/                       # JNI glue + vendored llama.cpp
│   ├── ai_chat.cpp
│   ├── CMakeLists.txt
│   └── llama.cpp/             # upstream source (vendored)
├── java/com/notifyai/
│   ├── ai/                    # engines, summarizer, clustering, model downloader
│   ├── data/                  # Room DAOs, entities, repositories
│   ├── di/                    # Hilt modules
│   ├── domain/                # models, repository interfaces, use cases
│   ├── service/               # NotificationListenerService
│   ├── ui/                    # Compose screens + view models
│   └── worker/                # WorkManager workers
└── java/com/arm/aichat/       # InferenceEngine abstraction over llama.cpp
```

## Third-party code

A few directories contain vendored upstream sources rather than first-party
code. They live in-tree because their upstreams don't ship through Maven/Gradle.

### `app/src/main/cpp/llama.cpp/`

Vendored copy of [ggerganov/llama.cpp](https://github.com/ggerganov/llama.cpp)
pinned at release `b9524`. The NDK build compiles it from source:

1. [app/build.gradle.kts](app/build.gradle.kts) points CMake at
   [app/src/main/cpp/CMakeLists.txt](app/src/main/cpp/CMakeLists.txt).
2. That `CMakeLists.txt` includes `llama.cpp/CMakeLists.txt`, producing
   `libllama.so` and `libggml*.so`.
3. The JNI glue in [ai_chat.cpp](app/src/main/cpp/ai_chat.cpp) `#include`s
   llama.cpp headers and links against those libraries.

Pinning a specific commit guarantees reproducible builds — llama.cpp moves fast
and frequently breaks its ABI. A git submodule would work equally well; the
checked-in copy was chosen for simplicity.

### `app/src/main/cpp/vendor/`

Single-header C++ dependencies used by the JNI layer. Currently just
[`nlohmann/json`](https://github.com/nlohmann/json) (vendored at
[`vendor/nlohmann/json.hpp`](app/src/main/cpp/vendor/nlohmann/json.hpp)) for
JSON parsing in `ai_chat.cpp`.

### `com.arm.aichat` package

Vendored Kotlin wrapper around llama.cpp's JNI surface, adapted from Arm's
reference [`arm/ai-chat-android`](https://github.com/arm/ai-chat-android)
project. It sits between the app and the C++ engine:

- [`InferenceEngine`](app/src/main/java/com/arm/aichat/InferenceEngine.kt) —
  typed Kotlin API (`loadModel`, `setSystemPrompt`, `sendUserPrompt`, `bench`)
  with a `StateFlow<State>` lifecycle (`LoadingModel` → `ModelReady` →
  `Generating` → …).
- [`AiChat`](app/src/main/java/com/arm/aichat/AiChat.kt) — one-call entry point
  (`AiChat.getInferenceEngine(context)`).
- [`gguf/GgufMetadataReader`](app/src/main/java/com/arm/aichat/gguf/GgufMetadataReader.kt)
  — reads architecture, quant type, and context length from a GGUF file so
  unsupported models are rejected before loading.
- [`internal/InferenceEngineImpl`](app/src/main/java/com/arm/aichat/internal/InferenceEngineImpl.kt)
  — calls into JNI, manages the native context, and surfaces generated tokens
  as a `Flow<String>`.

It's kept in its own `com.arm.aichat` package (rather than folded into
`com.notifyai`) to make the upstream boundary explicit, preserve attribution,
and keep an easy update path. Inside the app it has exactly one consumer:
[`LlamaCppEngine.kt`](app/src/main/java/com/notifyai/ai/engine/LlamaCppEngine.kt),
which adapts the generic `InferenceEngine` to the app's `LocalLlmEngine`
domain interface.

## Build

Prerequisites:
- Android Studio (Koala or newer)
- Android NDK `29.0.13113456`
- CMake `3.31.6`
- A device or emulator running Android 10+ (`minSdk = 29`)

```bash
# Configure local SDK path
echo "sdk.dir=/path/to/Android/sdk" > local.properties

# Build a debug APK
./gradlew :app:assembleDebug

# Install on a connected device
./gradlew :app:installDebug
```

The first launch downloads the GGUF model (~1 GB) into the app's private files
directory. Configure the URL in
[`ModelDownloader.kt`](app/src/main/java/com/notifyai/ai/engine/ModelDownloader.kt).

## Permissions

Briefly needs the **Notification Access** special permission. After install,
grant it via *Settings → Notifications → Device & app notifications →
Notification access → Briefly*. The app routes the user there from the
onboarding screen.

It also requests `POST_NOTIFICATIONS`, `INTERNET` (for the one-time model
download), and runs a foreground service during inference.

## Privacy

Notification content never leaves the device. The only network traffic is the
initial model download from a public URL.

## License

See [LICENSE](LICENSE).
