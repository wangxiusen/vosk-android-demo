# Dual Voiceprint Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add two independently accessible, fully offline Android voiceprint demos that compare sherpa-onnx and Vosk with the same enrollment and test recordings.

**Architecture:** A shared Java/XML voiceprint layer owns PCM capture, WAV persistence, quality checks, enrollment averaging, cosine matching, UI state, and metrics. Two small engine adapters extract embeddings through sherpa-onnx CAM++ or Vosk `SpeakerModel`; two thin Activities select an adapter while reusing one screen implementation.

**Tech Stack:** Java 8, Android XML/AppCompat, `AudioRecord` 16 kHz mono PCM16, sherpa-onnx 1.13.6 AAR, Vosk Android 0.3.75, `org.json`, JUnit 4, AndroidX instrumentation tests.

**Spec:** `docs/superpowers/specs/2026-09-21-dual-voiceprint-demo-design.md`

## Global Constraints

- Do not copy source or UI code from either library's official demo; call only public SDK APIs.
- Preserve the existing Vosk command recognition, Lottie, fixed MP3, and offline TTS behavior.
- Keep the current `minSdkVersion 21`, `targetSdkVersion 36`, Java 8, and current ABI list.
- Both engines consume exactly the same 16 kHz mono PCM16 registration and test samples.
- Each profile has exactly three accepted enrollment clips, with a five-second target duration.
- Store all profiles, WAV files, and embeddings only under the app-private `files/voiceprint` tree.
- Never print a full embedding or export enrollment WAV files in logs.
- Do not claim replay resistance or production authentication suitability.
- CPU inference only; no RKNN/NPU conversion in this plan.
- Treat target-Pad microphone validation as a separate hardware gate; successful model inference does not prove usable capture hardware.
- Preserve the user's unrelated untracked avatar files and do not include them in voiceprint commits.

## Review Focus

- Silent or near-silent PCM must be rejected before a profile or embedding is persisted; pin this in Task 2 quality tests.
- A duplicate display name must update the existing logical profile instead of creating an ambiguous second identity; pin this in Task 3 repository tests.
- A malformed Vosk result, missing `spk`, non-finite number, or insufficient `spk_frames` must fail closed; pin this in Task 1 parser tests.
- Activity pause/destroy during recording or inference must stop capture and ignore late callbacks; pin this in Task 5 controller and Task 6 lifecycle tests.
- A model-version or embedding-dimension mismatch must invalidate only that engine's vector while preserving shared WAV enrollment clips; pin this in Task 3 repository tests.

---

## File Structure

### New production files

- `app/src/main/java/com/huimei/voice/voiceprint/VoiceprintEngine.java` — SDK-independent embedding extractor contract.
- `app/src/main/java/com/huimei/voice/voiceprint/VoiceprintEngineType.java` — stable `sherpa`/`vosk` IDs, labels, model IDs, and initial thresholds.
- `app/src/main/java/com/huimei/voice/voiceprint/VoiceprintProfile.java` — immutable profile ID/name/sample metadata.
- `app/src/main/java/com/huimei/voice/voiceprint/VoiceprintMatch.java` — identity, score, threshold, and accepted result.
- `app/src/main/java/com/huimei/voice/voiceprint/VoiceprintMatcher.java` — normalize, average, verify, and 1:N search.
- `app/src/main/java/com/huimei/voice/voiceprint/VoiceprintAudioQuality.java` — duration/RMS/peak/clipping analysis and rejection reason.
- `app/src/main/java/com/huimei/voice/voiceprint/WavIO.java` — deterministic PCM16 mono WAV read/write.
- `app/src/main/java/com/huimei/voice/voiceprint/VoskSpeakerVectorParser.java` — strict `spk`/`spk_frames` JSON parsing.
- `app/src/main/java/com/huimei/voice/voiceprint/VoiceprintRepository.java` — app-private profile, sample, and per-engine embedding persistence.
- `app/src/main/java/com/huimei/voice/voiceprint/VoiceprintAudioRecorder.java` — bounded five-second `AudioRecord` capture.
- `app/src/main/java/com/huimei/voice/voiceprint/SherpaVoiceprintEngine.java` — CAM++ adapter.
- `app/src/main/java/com/huimei/voice/voiceprint/VoskVoiceprintEngine.java` — Vosk x-vector adapter and model-directory installation.
- `app/src/main/java/com/huimei/voice/voiceprint/VoiceprintController.java` — serial background workflow and lifecycle generation guard.
- `app/src/main/java/com/huimei/voice/voiceprint/VoiceprintListener.java` — UI callbacks.
- `app/src/main/java/com/huimei/voice/BaseVoiceprintActivity.java` — shared Java/XML screen.
- `app/src/main/java/com/huimei/voice/SherpaVoiceprintActivity.java` — sherpa entry.
- `app/src/main/java/com/huimei/voice/VoskVoiceprintActivity.java` — Vosk entry.
- `app/src/main/res/layout/activity_voiceprint.xml` — shared scrollable tablet-friendly layout.

### Modified production files

- `app/src/main/java/com/huimei/voice/DemoDestination.java` — add two list entries.
- `app/src/main/AndroidManifest.xml` — register two Activities.
- `app/src/main/res/values/strings.xml` — voiceprint labels, status, errors, and list summaries.
- `app/build.gradle` — keep ONNX uncompressed and add no new runtime dependency.
- `THIRD_PARTY_NOTICES.md` — add exact model provenance, hashes, and licenses.
- `README.md` — document two demos, model sizes, limitations, and device test instructions.

### Model assets

- `app/src/main/assets/voiceprint/3dspeaker_speech_campplus_sv_zh-cn_16k-common.onnx`
- `app/src/main/assets/voiceprint/vosk-model-spk-0.4/mfcc.conf`
- `app/src/main/assets/voiceprint/vosk-model-spk-0.4/final.ext.raw`
- `app/src/main/assets/voiceprint/vosk-model-spk-0.4/mean.vec`
- `app/src/main/assets/voiceprint/vosk-model-spk-0.4/transform.mat`

### New tests

- `app/src/test/java/com/huimei/voice/voiceprint/VoiceprintMatcherTest.java`
- `app/src/test/java/com/huimei/voice/voiceprint/VoiceprintAudioQualityTest.java`
- `app/src/test/java/com/huimei/voice/voiceprint/WavIOTest.java`
- `app/src/test/java/com/huimei/voice/voiceprint/VoskSpeakerVectorParserTest.java`
- `app/src/test/java/com/huimei/voice/voiceprint/VoiceprintRepositoryTest.java`
- `app/src/test/java/com/huimei/voice/voiceprint/VoiceprintControllerTest.java`
- `app/src/androidTest/java/com/huimei/voice/VoiceprintNavigationTest.java`

---

### Task 1: Pure voiceprint math and strict Vosk parsing

**Files:**
- Create: `app/src/main/java/com/huimei/voice/voiceprint/VoiceprintEngineType.java`
- Create: `app/src/main/java/com/huimei/voice/voiceprint/VoiceprintProfile.java`
- Create: `app/src/main/java/com/huimei/voice/voiceprint/VoiceprintMatch.java`
- Create: `app/src/main/java/com/huimei/voice/voiceprint/VoiceprintMatcher.java`
- Create: `app/src/main/java/com/huimei/voice/voiceprint/VoskSpeakerVectorParser.java`
- Test: `app/src/test/java/com/huimei/voice/voiceprint/VoiceprintMatcherTest.java`
- Test: `app/src/test/java/com/huimei/voice/voiceprint/VoskSpeakerVectorParserTest.java`

**Interfaces:**
- Produces: `float[] VoiceprintMatcher.normalize(float[])`
- Produces: `float[] VoiceprintMatcher.average(List<float[]>)`
- Produces: `float VoiceprintMatcher.cosine(float[], float[])`
- Produces: `VoiceprintMatch VoiceprintMatcher.search(Map<VoiceprintProfile,float[]>, float[], float)`
- Produces: `float[] VoskSpeakerVectorParser.parse(String json, int minimumFrames)`

- [ ] **Step 1: Write failing matcher tests**

```java
@Test public void cosineRejectsDimensionMismatch() {
    assertThrows(IllegalArgumentException.class,
            () -> VoiceprintMatcher.cosine(new float[]{1f}, new float[]{1f, 2f}));
}

@Test public void searchReturnsUnknownBelowThreshold() {
    VoiceprintProfile alice = new VoiceprintProfile("id-a", "Alice", 3, 1L);
    Map<VoiceprintProfile, float[]> enrolled = new LinkedHashMap<>();
    enrolled.put(alice, new float[]{1f, 0f});
    VoiceprintMatch result = VoiceprintMatcher.search(
            enrolled, new float[]{0f, 1f}, 0.60f);
    assertFalse(result.isAccepted());
    assertNull(result.getProfile());
}

@Test public void averageNormalizesResult() {
    float[] value = VoiceprintMatcher.average(Arrays.asList(
            new float[]{1f, 0f}, new float[]{1f, 0f}));
    assertArrayEquals(new float[]{1f, 0f}, value, 0.0001f);
}
```

- [ ] **Step 2: Write failing parser tests**

```java
@Test public void parsesFiniteVectorWithEnoughFrames() throws Exception {
    float[] value = VoskSpeakerVectorParser.parse(
            "{\"spk\":[0.1,-0.2],\"spk_frames\":240}", 200);
    assertArrayEquals(new float[]{0.1f, -0.2f}, value, 0.0001f);
}

@Test public void rejectsMissingVector() {
    assertThrows(IllegalArgumentException.class,
            () -> VoskSpeakerVectorParser.parse("{\"spk_frames\":240}", 200));
}

@Test public void rejectsInsufficientFramesAndNonFiniteNumbers() {
    assertThrows(IllegalArgumentException.class,
            () -> VoskSpeakerVectorParser.parse(
                    "{\"spk\":[1.0],\"spk_frames\":10}", 200));
    assertThrows(IllegalArgumentException.class,
            () -> VoskSpeakerVectorParser.parse(
                    "{\"spk\":[\"NaN\"],\"spk_frames\":240}", 200));
}
```

- [ ] **Step 3: Run the tests and verify RED**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.huimei.voice.voiceprint.VoiceprintMatcherTest' --tests 'com.huimei.voice.voiceprint.VoskSpeakerVectorParserTest'
```

Expected: compilation fails because production classes do not exist.

- [ ] **Step 4: Implement immutable domain objects, vector validation, matching, and parser**

Use explicit `Float.isNaN` / `Float.isInfinite` checks (compatible with the app's API 21 floor), reject null/empty/zero-norm vectors, normalize averaged embeddings, and return a rejected `VoiceprintMatch` without leaking the best profile below threshold. Use stable engine IDs `sherpa` and `vosk`; the separately named, calibration-visible initial cosine-similarity thresholds are `0.60f` for sherpa and `0.50f` for Vosk.

- [ ] **Step 5: Run focused and full JVM tests**

Run the focused command above, then:

```bash
./gradlew :app:testDebugUnitTest
```

Expected: all tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/huimei/voice/voiceprint app/src/test/java/com/huimei/voice/voiceprint
git commit -m "feat: add voiceprint matching domain"
```

---

### Task 2: PCM quality analysis and WAV persistence

**Files:**
- Create: `app/src/main/java/com/huimei/voice/voiceprint/VoiceprintAudioQuality.java`
- Create: `app/src/main/java/com/huimei/voice/voiceprint/WavIO.java`
- Test: `app/src/test/java/com/huimei/voice/voiceprint/VoiceprintAudioQualityTest.java`
- Test: `app/src/test/java/com/huimei/voice/voiceprint/WavIOTest.java`

**Interfaces:**
- Produces: `VoiceprintAudioQuality.Result VoiceprintAudioQuality.analyze(short[], int)`
- Produces: `void WavIO.writeMono16(File, short[], int)`
- Produces: `WavIO.Audio WavIO.readMono16(File)`

- [ ] **Step 1: Write failing audio-quality tests**

```java
@Test public void rejectsDigitalSilence() {
    short[] samples = new short[5 * 16000];
    VoiceprintAudioQuality.Result result = VoiceprintAudioQuality.analyze(samples, 16000);
    assertFalse(result.isAccepted());
    assertEquals(VoiceprintAudioQuality.Reason.TOO_QUIET, result.getReason());
}

@Test public void rejectsShortAndClippedAudio() {
    assertEquals(VoiceprintAudioQuality.Reason.TOO_SHORT,
            VoiceprintAudioQuality.analyze(new short[16000], 16000).getReason());
    short[] clipped = new short[5 * 16000];
    Arrays.fill(clipped, Short.MAX_VALUE);
    assertEquals(VoiceprintAudioQuality.Reason.CLIPPED,
            VoiceprintAudioQuality.analyze(clipped, 16000).getReason());
}

@Test public void acceptsAudibleFiveSecondSignal() {
    short[] samples = sineWave(440.0, 0.15, 5, 16000);
    assertTrue(VoiceprintAudioQuality.analyze(samples, 16000).isAccepted());
}
```

- [ ] **Step 2: Write failing WAV round-trip and malformed-header tests**

```java
@Test public void mono16RoundTripsExactly() throws Exception {
    short[] expected = new short[]{-32768, -1, 0, 1, 32767};
    WavIO.writeMono16(file, expected, 16000);
    WavIO.Audio actual = WavIO.readMono16(file);
    assertEquals(16000, actual.getSampleRate());
    assertArrayEquals(expected, actual.getSamples());
}

@Test public void rejectsStereoOrTruncatedWav() {
    assertThrows(IOException.class, () -> WavIO.readMono16(truncatedFile));
}
```

- [ ] **Step 3: Run tests and verify RED**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.huimei.voice.voiceprint.VoiceprintAudioQualityTest' --tests 'com.huimei.voice.voiceprint.WavIOTest'
```

- [ ] **Step 4: Implement quality metrics and little-endian RIFF/WAVE I/O**

Use explicit constants: minimum 3.0 seconds, maximum 7.0 seconds, quiet threshold `-50.0 dBFS`, and clipping rejection when at least `1%` of samples have an absolute amplitude at or above `0.98 * Short.MAX_VALUE`. Include measured duration, RMS dBFS, peak dBFS, clipping ratio, and rejection reason in the result.

- [ ] **Step 5: Run focused and full JVM tests, then commit**

```bash
./gradlew :app:testDebugUnitTest
git add app/src/main/java/com/huimei/voice/voiceprint app/src/test/java/com/huimei/voice/voiceprint
git commit -m "feat: add voiceprint audio validation"
```

---

### Task 3: App-private profile and embedding repository

**Files:**
- Create: `app/src/main/java/com/huimei/voice/voiceprint/VoiceprintRepository.java`
- Test: `app/src/test/java/com/huimei/voice/voiceprint/VoiceprintRepositoryTest.java`

**Interfaces:**
- Consumes: `VoiceprintProfile`, `WavIO`, `VoiceprintEngineType`.
- Produces: `VoiceprintProfile saveEnrollment(String displayName, List<short[]> samples, int sampleRate)`
- Produces: `List<VoiceprintProfile> listProfiles()`
- Produces: `List<WavIO.Audio> loadEnrollment(String profileId)`
- Produces: `void saveEmbedding(String profileId, VoiceprintEngineType, String modelId, float[])`
- Produces: `Optional<float[]> loadEmbedding(String profileId, VoiceprintEngineType, String modelId, int expectedDim)`
- Produces: `void deleteProfile(String profileId)` and `void clearAll()`

- [ ] **Step 1: Write failing repository tests using a temporary directory**

Cover:

```java
@Test public void duplicateDisplayNameReplacesLogicalProfile() { ... }
@Test public void embeddingRoundTripsWithExactFloatBits() { ... }
@Test public void modelOrDimensionMismatchReturnsEmptyButKeepsWavs() { ... }
@Test public void deleteRemovesProfileSamplesAndBothEngineVectors() { ... }
@Test public void clearAllLeavesAnEmptyRepository() { ... }
@Test public void malformedMetadataFailsClosedWithoutEscapingRoot() { ... }
```

- [ ] **Step 2: Run test and verify RED**

```bash
./gradlew :app:testDebugUnitTest --tests 'com.huimei.voice.voiceprint.VoiceprintRepositoryTest'
```

- [ ] **Step 3: Implement atomic metadata and binary embedding persistence**

Write metadata to `profiles.json.tmp`, flush/close, then rename over `profiles.json`. Generate UUID directory names. Compare duplicate display names after trimming and case-folding. Encode embeddings with a small header containing magic, format version, engine ID, model ID, dimension, then little-endian float payload. Canonicalize every file path and reject any path outside the repository root.

- [ ] **Step 4: Run repository and full JVM tests, then commit**

```bash
./gradlew :app:testDebugUnitTest
git add app/src/main/java/com/huimei/voice/voiceprint/VoiceprintRepository.java app/src/test/java/com/huimei/voice/voiceprint/VoiceprintRepositoryTest.java
git commit -m "feat: persist local voiceprint profiles"
```

---

### Task 4: Model assets and two engine adapters

**Files:**
- Create: `app/src/main/java/com/huimei/voice/voiceprint/VoiceprintEngine.java`
- Create: `app/src/main/java/com/huimei/voice/voiceprint/SherpaVoiceprintEngine.java`
- Create: `app/src/main/java/com/huimei/voice/voiceprint/VoskVoiceprintEngine.java`
- Create: `app/src/main/java/com/huimei/voice/voiceprint/VoiceprintModelInstaller.java`
- Create: model assets listed in File Structure.
- Modify: `THIRD_PARTY_NOTICES.md`
- Modify: `app/build.gradle`
- Test: add contract and reflection tests under `app/src/test/java/com/huimei/voice/voiceprint/`.

**Interfaces:**
- Produces:

```java
public interface VoiceprintEngine extends AutoCloseable {
    VoiceprintEngineType type();
    String modelId();
    int dimension();
    void initialize() throws Exception;
    float[] extract(short[] pcm16, int sampleRate) throws Exception;
    @Override void close();
}
```

- [ ] **Step 1: Write failing interface/reflection tests**

Verify the two implementations expose the exact contract, sherpa AAR contains `SpeakerEmbeddingExtractor`, and Vosk runtime contains `SpeakerModel` plus the three-argument `Recognizer` constructor. Add a model manifest test that checks asset-relative names and expected SHA-256 values without loading native libraries.

- [ ] **Step 2: Run the focused tests and verify RED**

```bash
./gradlew :app:testDebugUnitTest --tests 'com.huimei.voice.voiceprint.*Engine*Test' --tests 'com.huimei.voice.voiceprint.VoiceprintModelManifestTest'
```

- [ ] **Step 3: Download and verify official model artifacts**

Sherpa model:

```text
https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/3dspeaker_speech_campplus_sv_zh-cn_16k-common.onnx
```

Vosk model:

```text
https://alphacephei.com/vosk/models/vosk-model-spk-0.4.zip
```

Record exact byte sizes and SHA-256 digests in `THIRD_PARTY_NOTICES.md`; extract only the required Vosk model directory into `app/src/main/assets/voiceprint`. Do not execute downloaded content.

- [ ] **Step 4: Implement sherpa adapter**

Construct:

```java
SpeakerEmbeddingExtractorConfig config =
        new SpeakerEmbeddingExtractorConfig(
                "voiceprint/3dspeaker_speech_campplus_sv_zh-cn_16k-common.onnx",
                2,
                false,
                "cpu");
extractor = new SpeakerEmbeddingExtractor(context.getAssets(), config);
```

Convert PCM16 to `float` in `[-1,1]`, call `stream.acceptWaveform(samples, sampleRate)`, `inputFinished()`, require `extractor.isReady(stream)`, compute, normalize, and release the stream in `finally`.

- [ ] **Step 5: Implement Vosk adapter**

Copy `voiceprint/vosk-model-spk-0.4` and existing `model-cn` assets once using completion markers written only after every file succeeds. Construct `Model`, `SpeakerModel`, and a fresh `Recognizer(model, 16000f, speakerModel)` per extraction, feed PCM in bounded chunks, parse `getFinalResult()` through `VoskSpeakerVectorParser`, normalize, and close in reverse order. Do not set max alternatives.

- [ ] **Step 6: Run tests and a Debug native-link build**

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Expected: all tests pass and APK packages both model families and required native libraries.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/huimei/voice/voiceprint app/src/test/java/com/huimei/voice/voiceprint app/src/main/assets/voiceprint app/build.gradle THIRD_PARTY_NOTICES.md
git commit -m "feat: add sherpa and vosk voiceprint engines"
```

---

### Task 5: Recorder and lifecycle-safe controller

**Files:**
- Create: `app/src/main/java/com/huimei/voice/voiceprint/VoiceprintAudioRecorder.java`
- Create: `app/src/main/java/com/huimei/voice/voiceprint/VoiceprintListener.java`
- Create: `app/src/main/java/com/huimei/voice/voiceprint/VoiceprintController.java`
- Test: `app/src/test/java/com/huimei/voice/voiceprint/VoiceprintControllerTest.java`

**Interfaces:**
- Consumes: repository, matcher, engine, quality analyzer.
- Produces: `initialize`, `recordEnrollment`, `finishEnrollment`, `identify`, `verify`, `deleteProfile`, `clearAll`, `cancelActiveOperation`, and `close`.

- [ ] **Step 1: Write controller tests with fake recorder, engine, repository, executor, and listener**

Cover initialization, exactly three accepted enrollment clips, rejected silent clip not incrementing progress, identification with cached vectors, model-version vector regeneration, no-profile guard, concurrent button rejection, pause cancellation, late callback generation guard, and error recovery to idle.

- [ ] **Step 2: Run controller tests and verify RED**

```bash
./gradlew :app:testDebugUnitTest --tests 'com.huimei.voice.voiceprint.VoiceprintControllerTest'
```

- [ ] **Step 3: Implement five-second AudioRecord capture**

Use `MediaRecorder.AudioSource.VOICE_RECOGNITION`, `CHANNEL_IN_MONO`, `ENCODING_PCM_16BIT`, and a buffer at least twice `getMinBufferSize`. Stop at five seconds or cancellation, validate every `read` result, and always `stop/release` in `finally`.

- [ ] **Step 4: Implement serial controller workflow**

Use one background `ExecutorService`, a main-thread `Handler`, explicit operation state, and monotonically increasing generation. Never invoke listener callbacks from a stale generation. Cache model initialization only while the page is alive; `close()` cancels recording, shuts down the executor, closes the engine, and posts no later UI work.

- [ ] **Step 5: Run all JVM tests and commit**

```bash
./gradlew :app:testDebugUnitTest
git add app/src/main/java/com/huimei/voice/voiceprint app/src/test/java/com/huimei/voice/voiceprint
git commit -m "feat: orchestrate voiceprint recording flows"
```

---

### Task 6: Two custom Java/XML pages and navigation

**Files:**
- Create: `app/src/main/java/com/huimei/voice/BaseVoiceprintActivity.java`
- Create: `app/src/main/java/com/huimei/voice/SherpaVoiceprintActivity.java`
- Create: `app/src/main/java/com/huimei/voice/VoskVoiceprintActivity.java`
- Create: `app/src/main/res/layout/activity_voiceprint.xml`
- Modify: `app/src/main/java/com/huimei/voice/DemoDestination.java`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/test/java/com/huimei/voice/DemoDestinationTest.java`
- Create: `app/src/androidTest/java/com/huimei/voice/VoiceprintNavigationTest.java`

**Interfaces:**
- Consumes: `VoiceprintController`, `VoiceprintListener`, and engine type.
- Produces: two user-visible list entries with identical behavior and distinct engines.

- [ ] **Step 1: Extend failing navigation unit tests**

Assert six total destinations, sherpa and Vosk titles, and exact Activity classes. Run `DemoDestinationTest` and verify failure before production edits.

- [ ] **Step 2: Add failing instrumentation tests**

Launch each Activity with microphone permission granted, assert the engine title, fixed sentence, disabled action buttons during model load, and safe recreation. Add a test that backgrounds an Activity during a fake/controlled recording state and verifies no crash or stale UI callback.

- [ ] **Step 3: Build the custom shared XML page**

Use a vertical `ScrollView` containing engine status, fixed sentence, name `EditText`, three-sample progress, registration controls, profile `Spinner`, identify/verify controls, result card, metrics, destructive clear button, and monospace log. Keep minimum touch targets suitable for the 11.6-inch tablet and follow existing colors/dimensions.

- [ ] **Step 4: Implement the shared Activity and two thin subclasses**

The base Activity requests `RECORD_AUDIO`, creates the repository/controller, maps every listener callback to the UI, disables conflicting controls by state, confirms destructive clear, and calls `cancelActiveOperation` in `onStop` plus `close` in `onDestroy`. Subclasses return only `VoiceprintEngineType.SHERPA` or `.VOSK`.

- [ ] **Step 5: Register navigation/resources and run tests**

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

When a device is available:

```bash
./gradlew :app:connectedDebugAndroidTest
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/huimei/voice app/src/main/res app/src/main/AndroidManifest.xml app/src/test app/src/androidTest
git commit -m "feat: add dual voiceprint demo screens"
```

---

### Task 7: End-to-end verification, device metrics, and documentation

**Files:**
- Modify: `README.md`
- Modify: `PROJECT_NOTES.md`
- Create: `docs/voiceprint-comparison.md`

**Interfaces:**
- Consumes: completed APK and real device output.
- Produces: reproducible build/install steps and an evidence table that distinguishes algorithm, phone, and RK3576 hardware results.

- [ ] **Step 1: Run clean static verification**

```bash
./gradlew clean :app:testDebugUnitTest :app:assembleDebug
```

Expected: BUILD SUCCESSFUL. Record the exact APK path and size.

- [ ] **Step 2: Inspect packaged assets and ABI payloads**

Use `unzip -l` on the APK to verify both models exist exactly once and no unexpected archive or download files are packaged. Confirm existing Vosk CN/EN, TTS, Lottie, and MP3 assets remain present.

- [ ] **Step 3: Install on the currently connected microphone-capable phone**

Use explicit ADB serial. Grant microphone permission, launch from the exported main Activity, navigate to each demo, register at least one profile, identify it, verify it, delete it, clear all, and inspect logcat for crashes/native errors.

- [ ] **Step 4: Capture performance evidence**

For each engine record cold model-load milliseconds, five-second embedding extraction milliseconds, process PSS before/after model load, APK/model sizes, score, threshold, and result. Do not compare scores across engines as though their scales were identical.

- [ ] **Step 5: Run the three-person comparison when participants are available**

Use three enrollment clips per person, ten genuine tests per person, and at least twenty impostor tests. Sweep thresholds offline from recorded scores, report false rejects/accepts for the small sample, and explicitly label results as Demo-only.

- [ ] **Step 6: Re-test on RK3576 only after valid microphone input exists**

Confirm ADB identity, microphone PCM level, app process, foreground Activity, model load, inference, PSS, and result. If only digital silence remains, record the run as hardware-blocked rather than an algorithm failure.

- [ ] **Step 7: Update documentation with confirmed evidence only**

Document setup, model licenses/hashes, UI flow, limits, APK location, phone results, and separate RK3576 status. Do not mark unexecuted multi-person or RK3576 tests as passed.

- [ ] **Step 8: Final full verification and commit**

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
git status --short
git add README.md PROJECT_NOTES.md docs/voiceprint-comparison.md
git commit -m "docs: record voiceprint demo verification"
```

Expected: only the user's pre-existing unrelated untracked avatar files remain outside commits.
