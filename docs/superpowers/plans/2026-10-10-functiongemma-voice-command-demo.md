# FunctionGemma Offline Voice Command Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an isolated Android demo that converts Chinese or English speech into the existing whitelisted `CommandEvent` values using exact matching first and FunctionGemma fallback second.

**Architecture:** A separate Activity owns a wake-grammar/free-dictation Vosk controller and a CPU-only LiteRT-LM engine. Pure Java routing and validation sit between them, while a hash-pinned model store downloads or imports the `.litertlm` file outside the APK.

**Tech Stack:** Java 8, Android XML Views, Vosk Android 0.3.75, LiteRT-LM Android 0.18.0, JUnit 4, AndroidX ActivityScenario

**Spec:** `docs/superpowers/specs/2026-10-10-functiongemma-voice-command-demo-design.md`

## Global Constraints

- Keep `minSdkVersion 21`; LiteRT-LM is usable only for `arm64-v8a` and `x86_64`, with a controlled unsupported message on 32-bit devices.
- Pin `com.google.ai.edge.litertlm:litertlm-android:0.18.0`; use `Backend.CPU()` only.
- Never package the 289 MB model in the APK or commit it to Git.
- Never automatically execute tools; accept exactly one manually inspected tool call from a strict allowlist.
- Emit existing `CommandEvent` values only and never access RS232, GPIO, PLC, or oxygen-machine APIs.
- Exact `CommandCatalog` matches must bypass FunctionGemma.
- Model installation requires exact byte size and SHA-256; a failed install must preserve the prior valid model.
- Keep the existing Demo pages and behavior unchanged.

## Review Focus

- A cloud URL that returns an HTML login/error page must fail hash validation without replacing the installed model; covered by Task 2 stream-install tests.
- Zero, duplicate, multiple, unknown, or over-parameterized tool calls must not emit an event; covered by Task 3 validator/router tests.
- Duplicate Vosk `onResult` and `onFinalResult` callbacks for one utterance must trigger at most one inference; covered by Task 5 session tests.
- Activity destruction or language switching during model initialization/inference must discard stale callbacks and release native resources; covered by Tasks 4 and 6 tests.
- A 32-bit device must open the page and show an unsupported state without loading LiteRT-LM JNI; covered by Task 6 device-support unit test and manual ABI inspection.

---

### Task 1: Acquire and fingerprint the licensed model

**Files:**
- Create: `docs/models/functiongemma-mobile-actions.md`
- Modify: `.gitignore`
- Artifact outside Git: `downloads/functiongemma/mobile_actions_q8_ekv1024.litertlm`

**Interfaces:**
- Consumes: accepted Gemma Terms and an authenticated Hugging Face download session.
- Produces: exact `sizeBytes` and lowercase SHA-256 for Tasks 2 and 4.

- [ ] **Step 1: Confirm the license gate**

Run an unauthenticated HEAD request to the official model URL. Expected: `401` with `GatedRepo`; do not bypass it.

- [ ] **Step 2: Download the approved artifact**

After the user account has accepted Gemma Terms, download only `mobile_actions_q8_ekv1024.litertlm` from `litert-community/functiongemma-270m-ft-mobile-actions` into `downloads/functiongemma/`. Expected: a regular file near 289 MB, not HTML.

- [ ] **Step 3: Fingerprint and document it**

Run `stat`, `file`, and `shasum -a 256`; record repository, revision if exposed, filename, exact bytes, SHA-256, Gemma license URL, and source URL in `docs/models/functiongemma-mobile-actions.md`.

- [ ] **Step 4: Keep the artifact out of Git**

Add only `/downloads/functiongemma/` to `.gitignore`. Run `git status --short` and verify the model is absent while the documentation is present.

- [ ] **Step 5: Commit**

```bash
git add .gitignore docs/models/functiongemma-mobile-actions.md
git commit -m "docs: record FunctionGemma mobile model"
```

### Task 2: Hash-pinned model store and cloud download

**Files:**
- Create: `app/src/main/java/com/huimei/voice/functiongemma/FunctionGemmaModelManifest.java`
- Create: `app/src/main/java/com/huimei/voice/functiongemma/FunctionGemmaModelStore.java`
- Create: `app/src/main/java/com/huimei/voice/functiongemma/FunctionGemmaModelDownloader.java`
- Test: `app/src/test/java/com/huimei/voice/functiongemma/FunctionGemmaModelStoreTest.java`
- Test: `app/src/test/java/com/huimei/voice/functiongemma/FunctionGemmaModelDownloaderTest.java`

**Interfaces:**
- Consumes: Task 1 exact model size and SHA-256.
- Produces: `FunctionGemmaModelManifest.MODEL`; `FunctionGemmaModelStore(File root, Entry entry)` with `isInstalled()`, `installedFile()`, `install(InputStream)`, and `delete()`; `FunctionGemmaModelDownloader(ConnectionFactory)` with `download(URL, ModelStore, ProgressListener)`.

- [ ] **Step 1: Write failing model-store tests**

Cover a valid byte stream, wrong size, wrong hash, HTML content with matching nonzero length but wrong hash, replacement of an invalid target, preservation of a valid target on failed install, and canonical-path containment.

- [ ] **Step 2: Run the focused tests and verify RED**

Run `./gradlew :app:testDebugUnitTest --tests '*FunctionGemmaModelStoreTest'`. Expected: compilation failure because the classes do not exist.

- [ ] **Step 3: Implement the manifest and atomic store**

Use a staging file under `files/functiongemma/staging/`, 64 KiB buffered copy, `FileDescriptor.sync()`, exact length plus SHA-256 verification, and rename only after verification. Do not expose a method that installs without verification.

- [ ] **Step 4: Run the focused tests and verify GREEN**

Run the Task 2 store test command. Expected: all store tests pass.

- [ ] **Step 5: Write failing downloader policy tests**

Test that only `https` URLs are accepted, user-info credentials are rejected, redirects remain HTTPS, progress never exceeds declared content length, and error responses do not call `install`.

- [ ] **Step 6: Implement the minimal downloader**

Use an injected package-private `ConnectionFactory` returning `HttpURLConnection` so tests use deterministic fake connections. Run it on a caller-supplied worker thread, enforce bounded redirects, connect/read timeouts and status-code validation, stream progress, then call the store's verified `install(InputStream)`. Do not log the URL.

- [ ] **Step 7: Run Task 2 tests and the full JVM suite**

Run `./gradlew :app:testDebugUnitTest --tests '*FunctionGemmaModel*'` followed by `./gradlew :app:testDebugUnitTest`. Expected: both pass.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/huimei/voice/functiongemma app/src/test/java/com/huimei/voice/functiongemma
git commit -m "feat: add verified FunctionGemma model store"
```

### Task 3: Strict tool-call validation and hybrid routing

**Files:**
- Create: `app/src/main/java/com/huimei/voice/functiongemma/FunctionGemmaToolCall.java`
- Create: `app/src/main/java/com/huimei/voice/functiongemma/FunctionGemmaInference.java`
- Create: `app/src/main/java/com/huimei/voice/functiongemma/FunctionGemmaEngine.java`
- Create: `app/src/main/java/com/huimei/voice/functiongemma/FunctionGemmaCommandValidator.java`
- Create: `app/src/main/java/com/huimei/voice/functiongemma/HybridCommandResult.java`
- Create: `app/src/main/java/com/huimei/voice/functiongemma/HybridCommandRouter.java`
- Test: `app/src/test/java/com/huimei/voice/functiongemma/FunctionGemmaCommandValidatorTest.java`
- Test: `app/src/test/java/com/huimei/voice/functiongemma/HybridCommandRouterTest.java`

**Interfaces:**
- Consumes: `CommandCatalog.forLanguage(language).find(text)` and `CommandEvent`.
- Produces: `FunctionGemmaEngine.initialize(File)`, `infer(String)`, `close()`; `HybridCommandRouter.route(VoiceLanguage, String)` returning `EXACT`, `MODEL_ACCEPTED`, or `REJECTED`.

- [ ] **Step 1: Write failing validator tests**

Assert correct mappings for `power_on`, `power_off`, and `set_gas_duration` values `30/60/120/480`. Assert rejection for zero or two calls, unknown names, arguments on power tools, missing/extra/string/decimal/negative/unsupported duration, and null maps.

- [ ] **Step 2: Run validator tests and verify RED**

Run `./gradlew :app:testDebugUnitTest --tests '*FunctionGemmaCommandValidatorTest'`. Expected: compilation failure for missing production classes.

- [ ] **Step 3: Implement immutable call/result types and validator**

Keep all classes pure Java. Compare exact tool names and exact argument-key sets; accept integral `Number` values only when they round-trip to the allowed integer.

- [ ] **Step 4: Run validator tests and verify GREEN**

Expected: all validator tests pass.

- [ ] **Step 5: Write failing router tests**

Use a counting fake engine. Assert existing Chinese/English phrases return `EXACT` with zero engine calls; natural text calls the engine once; accepted calls return `MODEL_ACCEPTED`; empty input, input over 200 Unicode code points, engine exception, no call, multiple calls, and illegal calls return `REJECTED`.

- [ ] **Step 6: Implement the minimal router**

Normalize only through existing `CommandCatalog`; do not add hidden synonyms. Record elapsed time from an injected `TimeSource` and never retry inference implicitly.

- [ ] **Step 7: Run Task 3 tests and full JVM suite**

Expected: focused tests and `:app:testDebugUnitTest` pass.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/huimei/voice/functiongemma app/src/test/java/com/huimei/voice/functiongemma
git commit -m "feat: add whitelisted FunctionGemma command routing"
```

### Task 4: LiteRT-LM Java runtime adapter

**Files:**
- Modify: `app/build.gradle`
- Modify: `app/proguard-rules.pro`
- Create: `app/src/main/java/com/huimei/voice/functiongemma/FunctionGemmaToolSchema.java`
- Create: `app/src/main/java/com/huimei/voice/functiongemma/LiteRtFunctionGemmaEngine.java`
- Test: `app/src/test/java/com/huimei/voice/functiongemma/FunctionGemmaToolSchemaTest.java`
- Test: `app/src/test/java/com/huimei/voice/functiongemma/LiteRtFunctionGemmaContractTest.java`

**Interfaces:**
- Consumes: Task 2 installed model file and Task 3 `FunctionGemmaEngine`/inference types.
- Produces: a CPU-only engine adapter that returns LiteRT-LM `Message.toolCalls` without executing them.

- [ ] **Step 1: Write failing schema and dependency contract tests**

Assert exactly three tool definitions, exact names, no parameters on power tools, and integer enum `30/60/120/480`. Through package-private config factory methods, assert CPU with four threads, 1024 engine tokens, application cache path, `automaticToolCalling=false`, temperature `0.0`, and `maxOutputToken=64` without loading JNI or a model.

- [ ] **Step 2: Run focused tests and verify RED**

Expected: missing dependency/classes failure.

- [ ] **Step 3: Add the pinned dependency and adapter**

Add `implementation 'com.google.ai.edge.litertlm:litertlm-android:0.18.0'`. Add package-private `createEngineConfig(File model, File cacheDir)` and `createConversationConfig()` methods that expose configuration to JVM tests without initializing JNI. Build `EngineConfig` with `new Backend.CPU(4, null)` and a 1024 token engine cap; initialize synchronously only inside the Activity-owned executor. Create each conversation with the three OpenAPI tools, temperature `0.0`, `automaticToolCalling=false`, and 64 output tokens. Convert `Message.getToolCalls()` to Task 3 types, close the conversation, and never call `OpenApiTool.execute`.

- [ ] **Step 4: Add only required R8 keep rules**

Use the AAR's consumer rules if present; add project rules only for runtime/reflection classes proven necessary by release build or documented annotation lookup.

- [ ] **Step 5: Run focused tests, full JVM suite, and Debug/Release builds**

Run `:app:testDebugUnitTest`, `:app:assembleDebug`, and `:app:assembleRelease`. Expected: all pass; inspect APKs to confirm no `.litertlm` model and LiteRT-LM JNI only under `arm64-v8a`/`x86_64`.

- [ ] **Step 6: Commit**

```bash
git add app/build.gradle app/proguard-rules.pro app/src/main/java/com/huimei/voice/functiongemma app/src/test/java/com/huimei/voice/functiongemma
git commit -m "feat: integrate LiteRT-LM FunctionGemma runtime"
```

### Task 5: Wake-grammar to free-dictation Vosk controller

**Files:**
- Create: `app/src/main/java/com/huimei/voice/functiongemma/HybridVoiceState.java`
- Create: `app/src/main/java/com/huimei/voice/functiongemma/HybridVoiceSession.java`
- Create: `app/src/main/java/com/huimei/voice/functiongemma/HybridVoiceCommandController.java`
- Create: `app/src/main/java/com/huimei/voice/functiongemma/HybridVoiceCommandListener.java`
- Test: `app/src/test/java/com/huimei/voice/functiongemma/HybridVoiceSessionTest.java`
- Test: `app/src/test/java/com/huimei/voice/functiongemma/HybridVoiceControllerContractTest.java`

**Interfaces:**
- Consumes: existing `CommandCatalog`, `RecognitionResultParser`, Vosk assets, and Task 3 router input text.
- Produces: callbacks for state, wake event, one finalized command transcript, timeout, diagnostic, and error.

- [ ] **Step 1: Write failing pure session tests**

Cover sleep ignoring non-wake text; wake transition; one command transcript only; duplicate final suppression; 8-second timeout; reset on language change; inference completion returning to sleep; stop/close invalidating callbacks.

- [ ] **Step 2: Run session tests and verify RED**

Expected: missing state/session classes.

- [ ] **Step 3: Implement the pure session state machine**

Use `SLEEPING`, `DICTATING`, `INFERENCING`, `STOPPED`; inject `TimeSource`; identify the wake phrase through `CommandCatalog`; assign an utterance generation so duplicate callbacks cannot submit twice.

- [ ] **Step 4: Run session tests and verify GREEN**

Expected: all session tests pass.

- [ ] **Step 5: Write the controller contract test**

Assert by source/reflection contract that sleeping uses `new Recognizer(model, 16000f, catalog.grammarJson())`, dictating uses `new Recognizer(model, 16000f)`, switching stops/shuts down the old `SpeechService`, stale generations close loaded models, and close releases service/recognizer/model.

- [ ] **Step 6: Implement the isolated Android controller**

Reuse one loaded Vosk `Model` per selected language, recreate only recognizer/service when moving between wake grammar and free dictation, stop microphone during inference, then restore wake mode. Do not modify `VoiceRecognitionController`.

- [ ] **Step 7: Run Task 5 tests and full JVM suite**

Expected: focused and full tests pass.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/huimei/voice/functiongemma app/src/test/java/com/huimei/voice/functiongemma
git commit -m "feat: add hybrid Vosk voice session"
```

### Task 6: Demo Activity, model controls, and launcher integration

**Files:**
- Create: `app/src/main/java/com/huimei/voice/FunctionGemmaCommandActivity.java`
- Create: `app/src/main/java/com/huimei/voice/functiongemma/FunctionGemmaDeviceSupport.java`
- Create: `app/src/main/java/com/huimei/voice/functiongemma/LatestOperationGate.java`
- Create: `app/src/main/res/layout/activity_functiongemma_command.xml`
- Modify: `app/src/main/java/com/huimei/voice/DemoDestination.java`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/test/java/com/huimei/voice/DemoDestinationTest.java`
- Create: `app/src/test/java/com/huimei/voice/functiongemma/FunctionGemmaDeviceSupportTest.java`
- Create: `app/src/test/java/com/huimei/voice/functiongemma/LatestOperationGateTest.java`
- Create: `app/src/androidTest/java/com/huimei/voice/FunctionGemmaCommandNavigationTest.java`
- Create: `app/src/androidTest/java/com/huimei/voice/FunctionGemmaCommandLifecycleTest.java`

**Interfaces:**
- Consumes: Tasks 2-5 model store/downloader, engine, router, and Vosk controller.
- Produces: the eighth launcher entry and a fully local Demo page.

- [ ] **Step 1: Update the launcher test first**

Expect `FUNCTIONGEMMA_COMMAND` after `FACE_RECOGNITION`, targeting `com.huimei.voice.FunctionGemmaCommandActivity`; run it and verify RED.

- [ ] **Step 2: Write failing Activity tests**

Navigation test opens the eighth row and checks the page title. Lifecycle test launches without a model and checks `模型未安装`. Pure JVM tests feed 32-bit-only ABI arrays into `FunctionGemmaDeviceSupport.isSupported(String[])` and exercise `LatestOperationGate.next()/isCurrent()/close()` so stale initialization and inference callbacks are rejected after replacement or close.

- [ ] **Step 3: Add layout, strings, manifest entry, and destination**

Use existing visual styles. Include language, status, model metrics, text input, parse button, start/stop listening, URL input, download, file import, delete model, clear log, and scrollable log. Keep destructive delete behind confirmation.

- [ ] **Step 4: Implement Activity orchestration**

Use one single-thread executor plus `LatestOperationGate`. Initialize LiteRT-LM only after a valid model is present and only when `FunctionGemmaDeviceSupport` confirms the current ABI before any engine class is instantiated. Route exact or model results on the worker, return UI updates on main only for the current token, and close controller/engine on destroy. Use `ACTION_OPEN_DOCUMENT` for import and never persist or log the URL.

- [ ] **Step 5: Run JVM and instrumentation tests**

Run `:app:testDebugUnitTest`, then connected tests on the selected device if online. Expected: all JVM tests pass; navigation/model-missing/lifecycle tests pass on device.

- [ ] **Step 6: Build and inspect APKs**

Run Debug and Release builds, record file sizes, list APK contents, and verify no model file. Expected: both build, and only runtime native libraries increase APK size.

- [ ] **Step 7: Commit**

```bash
git add app/src/main app/src/test app/src/androidTest
git commit -m "feat: add FunctionGemma voice command demo"
```

### Task 7: Model smoke test and real-device evidence

**Files:**
- Create: `docs/diagnostics/2026-10-10-functiongemma-command-evaluation.md`
- Create: `docs/diagnostics/2026-10-10-functiongemma-device-memory.log`
- Modify: `README.md`
- Modify: `PROJECT_NOTES.md`

**Interfaces:**
- Consumes: complete APK, Task 1 model, and an online ARM64 Android device.
- Produces: reproducible evidence for correctness, latency, memory, and remaining fine-tuning work.

- [ ] **Step 1: Run a desktop model smoke test**

Use LiteRT-LM CLI with the exact three-tool schema and record at least one valid English call, one Chinese attempt, and one irrelevant rejection. Expected: artifact loads; results are recorded verbatim without claiming production accuracy.

- [ ] **Step 2: Install the Debug APK and model on the selected device**

Use an explicit `adb -s <serial>`, launch `MainActivity`, enter the eighth Demo, import or download the hash-matching model, and initialize it. Expected: no `FATAL EXCEPTION` or ANR.

- [ ] **Step 3: Run the fixed evaluation set**

Evaluate exact phrases, 10 Chinese paraphrases, 10 English paraphrases, 10 irrelevant inputs, illegal durations, and conflicting/multi-action inputs. Record transcription, route, raw tool call, accepted event, latency, and pass/fail.

- [ ] **Step 4: Measure lifecycle memory**

Capture `dumpsys meminfo` before page entry, after model initialization, during/after inference, and after leaving the page. Record total PSS and RSS where available, plus cold initialization and per-request latency.

- [ ] **Step 5: Run final verification**

Run the full JVM suite, connected tests if the device remains online, Debug/Release builds, `git diff --check`, APK content checks, and logcat crash/ANR scan. Expected: all executable checks pass; unavailable hardware checks are explicitly reported, not inferred.

- [ ] **Step 6: Commit diagnostics and documentation**

```bash
git add docs README.md PROJECT_NOTES.md
git commit -m "docs: record FunctionGemma device evaluation"
```
