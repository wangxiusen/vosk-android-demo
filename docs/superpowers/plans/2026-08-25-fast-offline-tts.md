# Fast Offline TTS Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the fourth demo's slow Kokoro playback path with language-specific lightweight female models and non-blocking queued PCM playback.

**Architecture:** A language model factory creates exactly one AISHELL3 or Piper LJSpeech VITS `OfflineTts` configuration at a time. The sherpa callback copies PCM into a bounded session queue and returns immediately, while a dedicated playback worker writes 20ms frames to `AudioTrack` and drives mouth animation from played PCM.

**Tech Stack:** Android Java, sherpa-onnx 1.13.6, AISHELL3/Piper VITS ONNX models, JUnit 4, Gradle, adb.

**Spec:** `docs/superpowers/specs/2026-08-25-fast-offline-tts-design.md`

## Global Constraints

- Keep the first three demo behaviors unchanged.
- Load only the selected language's TTS model.
- Keep all synthesis offline and use female voices for Chinese and English.
- Preserve the existing three speed choices and Lottie mouth animation.
- Verify on Xiaomi 2201122C, Android 15, arm64-v8a.

---

### Task 1: Lightweight model configuration and voice catalog

**Files:**
- Create: `app/src/main/java/com/huimei/voice/tts/LightweightTtsConfigFactory.java`
- Modify: `app/src/main/java/com/huimei/voice/tts/TtsVoiceCatalog.java`
- Test: `app/src/test/java/com/huimei/voice/LightweightTtsConfigFactoryTest.java`
- Test: `app/src/test/java/com/huimei/voice/TtsVoiceCatalogTest.java`

**Interfaces:**
- Consumes: `TtsLanguage`.
- Produces: `LightweightTtsConfigFactory.create(TtsLanguage, String)`, Chinese female speaker IDs 4 and 122, and English speaker ID 0.

- [x] Write tests asserting literal Chinese and English model paths, rule FSTs, copied English data directory, four threads and speaker IDs.
- [x] Run the focused tests and confirm they fail because the lightweight factory and new voice contract do not exist.
- [x] Implement the factory and minimal catalog change.
- [x] Run the focused tests and confirm they pass.

### Task 2: Bounded cancellable PCM queue

**Files:**
- Create: `app/src/main/java/com/huimei/voice/tts/PcmChunkQueue.java`
- Test: `app/src/test/java/com/huimei/voice/PcmChunkQueueTest.java`

**Interfaces:**
- Produces: `offer(float[])`, `take()`, `finish()`, `cancel()`, with an immutable end marker and bounded chunk capacity.

- [x] Write tests proving FIFO delivery, producer copy isolation, end-of-stream delivery, cancellation wake-up and capacity rejection.
- [x] Run the test and confirm it fails because the queue class does not exist.
- [x] Implement the smallest synchronized queue that satisfies those behaviors.
- [x] Run the test and confirm it passes.

### Task 3: Non-blocking generation and dedicated playback

**Files:**
- Modify: `app/src/main/java/com/huimei/voice/tts/OfflineTtsPlayer.java`
- Test: `app/src/test/java/com/huimei/voice/SherpaCallbackSignatureTest.java`

**Interfaces:**
- Consumes: `PcmChunkQueue` and `LightweightTtsConfigFactory`.
- Produces: `initialize(TtsLanguage)` and `loadLanguage(TtsLanguage)`; sherpa callback only enqueues PCM; playback worker alone owns `AudioTrack.write`.

- [x] Extend the callback test to require construction around a queue rather than an `AudioTrack`, while preserving the JNI `invoke(float[])` signature.
- [x] Run the test and confirm it fails against the blocking callback implementation.
- [x] Split model/generation and playback executors, implement cancellable sessions, queue-based playback and performance logging.
- [x] Run all unit tests and confirm they pass.

### Task 4: Language model lifecycle in the fourth page

**Files:**
- Modify: `app/src/main/java/com/huimei/voice/ArbitraryTtsActivity.java`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `OfflineTtsPlayer.initialize(TtsLanguage)` and `loadLanguage(TtsLanguage)`.
- Produces: loading/ready UI state for the selected language.

- [x] Make the page initialize Chinese and reload the selected model on language changes.
- [x] Disable play controls while loading and keep stop/speed behavior unchanged.
- [x] Run unit tests and lint.

### Task 5: Model assets, license record and device verification

**Files:**
- Create: `app/src/main/assets/vits-icefall-zh-aishell3/**`
- Create: `app/src/main/assets/vits-piper-en_US-ljspeech-medium/**`
- Modify: `THIRD_PARTY_NOTICES.md`
- Modify: `PROJECT_NOTES.md`
- Create: `docs/diagnostics/2026-08-25-lightweight-tts-performance.md`
- Create: `docs/diagnostics/2026-08-25-lightweight-tts-performance.log`

**Interfaces:**
- Consumes: official sherpa-onnx release archives.
- Produces: reproducible offline assets and measured acceptance evidence.

- [x] Download official archives, extract exact asset directories, record file hashes and model notices.
- [x] Build the debug APK, install it on device `3da06fb`, launch the fourth page and exercise Chinese and English punctuation samples plus stop/language switching.
- [x] Capture `HuimeiTtsPerf`, PSS, crash and AudioTrack underrun evidence.
- [x] Move superseded Kokoro/Matcha/Vocos assets out of the project after both VITS models pass device playback.
- [x] Run fresh full unit tests, lint and debug build; review `git diff` against every requirement.
