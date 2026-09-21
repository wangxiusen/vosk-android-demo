# Offline Face Recognition Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a fifth offline face-recognition Demo that enrolls five camera samples and performs stabilized real-time 1:N matching on Android.

**Architecture:** CameraX supplies latest-only RGBA frames to a single-threaded controller. OpenCV YuNet/SFace performs detection, quality checks, alignment and feature extraction; pure-Java matcher, stabilizer and repository components keep decision and persistence logic testable without a camera.

**Tech Stack:** Java 8, Android XML, CameraX 1.4.2, OpenCV Android 4.13.0, YuNet 2023mar FP32, SFace 2021dec INT8, JUnit 4, AndroidX instrumentation.

**Spec:** `docs/superpowers/specs/2026-09-21-face-recognition-demo-design.md`

## Global Constraints

- Keep `minSdkVersion 21`, `compileSdkVersion 36`, and all existing voice, animation, TTS and voiceprint behavior.
- Store face data only under `files/face-recognition/`; never log or upload feature arrays or portrait bytes.
- Require exactly one quality-approved face for enrollment and recognition.
- Enroll five samples; confirm recognition only after three consecutive frames for the same profile.
- Use cosine threshold `0.363` only as a Demo starting point and label it uncalibrated.
- Every CameraX analyzer path closes `ImageProxy` in `finally`.
- Do not add liveness, cloud sync, vector databases or RKNN in this branch.

## Review Focus

- Corrupt/truncated metadata or embedding files must produce a visible recoverable error, not silently match a person.
- Empty or traversal-like display names/profile IDs must not escape the repository root.
- Unknown → candidate → different candidate transitions must reset the three-frame confirmation counter.
- Camera frames arriving while model initialization or a prior analysis is active must be dropped, not queued without bound.
- Activity destruction during model work must prevent stale UI callbacks and release camera/executor/OpenCV resources.

---

### Task 1: Pure face matching and stabilization domain

**Files:**
- Create: `app/src/main/java/com/huimei/voice/face/FaceProfile.java`
- Create: `app/src/main/java/com/huimei/voice/face/FaceMatch.java`
- Create: `app/src/main/java/com/huimei/voice/face/FaceMatcher.java`
- Create: `app/src/main/java/com/huimei/voice/face/FaceRecognitionStabilizer.java`
- Test: `app/src/test/java/com/huimei/voice/face/FaceMatcherTest.java`
- Test: `app/src/test/java/com/huimei/voice/face/FaceRecognitionStabilizerTest.java`

**Interfaces:**
- Produces: `FaceMatcher.normalize(float[])`, `FaceMatcher.averageNormalized(List<float[]>)`, `FaceMatcher.best(float[], List<FaceProfile>, Map<String,float[]>, float)`.
- Produces: `FaceRecognitionStabilizer.accept(FaceMatch)` returning a confirmed match only on the third consecutive passing frame.
- Produces: immutable `FaceProfile(String id, String displayName, int sampleCount, long createdAtMillis)` and `FaceMatch(profile, score, accepted)`.

- [ ] **Step 1: Write failing matcher tests**

```java
@Test public void normalizesAndSelectsBestAcceptedProfile() {
    FaceProfile alice = new FaceProfile("a", "Alice", 5, 1L);
    FaceProfile bob = new FaceProfile("b", "Bob", 5, 2L);
    Map<String, float[]> vectors = new HashMap<>();
    vectors.put("a", new float[]{1f, 0f});
    vectors.put("b", new float[]{0f, 1f});
    FaceMatch match = FaceMatcher.best(new float[]{0.9f, 0.1f},
            Arrays.asList(alice, bob), vectors, 0.363f);
    assertEquals("a", match.getProfile().getId());
    assertTrue(match.isAccepted());
}

@Test public void rejectsInvalidVectorsAndDimensionMismatch() {
    assertThrows(IllegalArgumentException.class,
            () -> FaceMatcher.normalize(new float[]{0f, 0f}));
    assertThrows(IllegalArgumentException.class,
            () -> FaceMatcher.cosine(new float[]{1f}, new float[]{1f, 0f}));
}
```

- [ ] **Step 2: Run matcher tests and verify RED**

Run: `./gradlew -Dorg.gradle.java.home='/Applications/Android Studio.app/Contents/jbr/Contents/Home' :app:testDebugUnitTest --tests 'com.huimei.voice.face.FaceMatcherTest'`

Expected: compilation fails because the face domain classes do not exist.

- [ ] **Step 3: Implement immutable domain objects and minimal vector math**

Implement strict finite-value/dimension validation, L2 normalization, average-then-normalize and descending cosine Top-1 selection. An empty profile list returns `FaceMatch.unknown()`; do not add ranking or databases.

- [ ] **Step 4: Write and run stabilizer RED tests**

```java
@Test public void confirmsOnlyThirdConsecutiveAcceptedMatch() {
    FaceRecognitionStabilizer value = new FaceRecognitionStabilizer(3);
    FaceMatch alice = accepted("a", 0.8f);
    assertFalse(value.accept(alice).isPresent());
    assertFalse(value.accept(alice).isPresent());
    assertEquals("a", value.accept(alice).get().getProfile().getId());
}

@Test public void unknownOrCandidateChangeResetsProgress() {
    FaceRecognitionStabilizer value = new FaceRecognitionStabilizer(3);
    value.accept(accepted("a", 0.8f));
    value.accept(FaceMatch.unknown());
    value.accept(accepted("a", 0.8f));
    assertFalse(value.accept(accepted("a", 0.8f)).isPresent());
    value.accept(accepted("b", 0.9f));
    assertFalse(value.accept(accepted("a", 0.8f)).isPresent());
}
```

Expected RED: `FaceRecognitionStabilizer` is missing.

- [ ] **Step 5: Implement stabilizer and run the full unit suite**

Run: `./gradlew -Dorg.gradle.java.home='/Applications/Android Studio.app/Contents/jbr/Contents/Home' :app:testDebugUnitTest`

Expected: all tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/huimei/voice/face app/src/test/java/com/huimei/voice/face
git commit -m "feat: add face matching domain"
```

### Task 2: Private face-profile persistence

**Files:**
- Create: `app/src/main/java/com/huimei/voice/face/FaceRepository.java`
- Test: `app/src/test/java/com/huimei/voice/face/FaceRepositoryTest.java`

**Interfaces:**
- Consumes: `FaceProfile`, normalized finite `float[]`.
- Produces: `saveEnrollment(String, float[], byte[])`, `listProfiles()`, `loadEmbedding(String,int)`, `loadPortrait(String)`, `deleteProfile(String)`, `clearAll()`.

- [ ] **Step 1: Write repository RED tests**

Test that save/list/load round-trips a 128-value vector and JPEG bytes; saving the same case-insensitive name replaces it; corrupt magic/version/dimension/trailing bytes fail; unknown profile fails; delete/clear remove only repository children; blank names and `../` IDs are rejected.

- [ ] **Step 2: Run repository tests and verify RED**

Run: `./gradlew -Dorg.gradle.java.home='/Applications/Android Studio.app/Contents/jbr/Contents/Home' :app:testDebugUnitTest --tests 'com.huimei.voice.face.FaceRepositoryTest'`

Expected: compilation fails because `FaceRepository` does not exist.

- [ ] **Step 3: Implement minimal atomic repository**

Use `profiles.json` plus per-profile directory containing `embedding.f32` and `portrait.jpg`. Binary header is ASCII `HFAC`, version 1, dimension, then little-endian float bits. Canonicalize every child against the repository root before read/write/delete; write metadata and embedding through `.tmp` plus rename.

- [ ] **Step 4: Run repository and full unit tests**

Expected: all tests pass with no data written outside JUnit temporary folders.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/huimei/voice/face/FaceRepository.java app/src/test/java/com/huimei/voice/face/FaceRepositoryTest.java
git commit -m "feat: persist local face profiles"
```

### Task 3: Camera/OpenCV dependencies and verified models

**Files:**
- Modify: `app/build.gradle`
- Create: `app/src/main/assets/face/face_detection_yunet_2023mar.onnx`
- Create: `app/src/main/assets/face/face_recognition_sface_2021dec_int8.onnx`
- Create: `app/src/main/java/com/huimei/voice/face/FaceModelManifest.java`
- Create: `app/src/main/java/com/huimei/voice/face/FaceModelInstaller.java`
- Test: `app/src/test/java/com/huimei/voice/face/FaceModelManifestTest.java`

**Interfaces:**
- Produces: `FaceModelInstaller.install()` returning canonical detector/recognizer files after exact size and SHA-256 validation.

- [ ] **Step 1: Write model-manifest RED test**

```java
@Test public void pinsOfficialModelMetadata() {
    assertEquals(232589L, FaceModelManifest.YUNET.sizeBytes());
    assertEquals("8f2383e4dd3cfbb4553ea8718107fc0423210dc964f9f4280604804ed2552fa4",
            FaceModelManifest.YUNET.sha256());
    assertEquals(9896933L, FaceModelManifest.SFACE.sizeBytes());
    assertEquals("2b0e941e6f16cc048c20aee0c8e31f569118f65d702914540f7bfdc14048d78a",
            FaceModelManifest.SFACE.sha256());
}
```

- [ ] **Step 2: Run the manifest test and verify RED**

Expected: `FaceModelManifest` is missing.

- [ ] **Step 3: Add pinned dependencies and models**

Add `androidx.camera:camera-camera2:1.4.2`, `camera-lifecycle:1.4.2`, `camera-view:1.4.2`, and `org.opencv:opencv:4.13.0`. Copy the already verified temporary models into `assets/face/`; do not redownload under a different URL or rename their contents.

- [ ] **Step 4: Implement manifest and installer, then build**

Run: `./gradlew -Dorg.gradle.java.home='/Applications/Android Studio.app/Contents/jbr/Contents/Home' :app:testDebugUnitTest :app:assembleDebug`

Expected: manifest tests pass, dependencies resolve, APK contains each model once.

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle app/src/main/assets/face app/src/main/java/com/huimei/voice/face/FaceModelManifest.java app/src/main/java/com/huimei/voice/face/FaceModelInstaller.java app/src/test/java/com/huimei/voice/face/FaceModelManifestTest.java
git commit -m "feat: add OpenCV face models"
```

### Task 4: Frame quality and OpenCV engine

**Files:**
- Create: `app/src/main/java/com/huimei/voice/face/FaceFrameQuality.java`
- Create: `app/src/main/java/com/huimei/voice/face/FaceFrame.java`
- Create: `app/src/main/java/com/huimei/voice/face/FaceEngine.java`
- Create: `app/src/main/java/com/huimei/voice/face/FaceObservation.java`
- Create: `app/src/main/java/com/huimei/voice/face/OpenCvFaceEngine.java`
- Test: `app/src/test/java/com/huimei/voice/face/FaceFrameQualityTest.java`
- Test: `app/src/test/java/com/huimei/voice/face/OpenCvFaceContractTest.java`

**Interfaces:**
- Consumes: installed model paths and an immutable upright RGBA `FaceFrame`.
- Produces: `FaceEngine.initialize()`, `analyze(FaceFrame)` returning face count, quality reason, normalized 128-vector, detection score, elapsed milliseconds and aligned portrait bytes, plus `close()`.

- [ ] **Step 1: Write quality RED tests**

Pin pure decisions: zero/multiple faces rejected; confidence below `0.90`, face-width ratio below `0.20`, mean grayscale outside `45..220`, or Laplacian variance below `60` rejected; exact boundaries accepted.

- [ ] **Step 2: Run tests and verify RED**

Expected: `FaceFrameQuality` is missing.

- [ ] **Step 3: Implement quality decisions and engine contract**

Initialize with `OpenCVLoader.initLocal()`. Convert `FaceFrame` bytes to a temporary RGBA `Mat`, then create YuNet with `(320,320)`, confidence `0.90`, NMS `0.30`, topK `5000`; call `setInputSize` for each actual frame. For one accepted row, use `alignCrop`, `feature`, copy 128 floats, normalize with `FaceMatcher`, and encode the aligned face as JPEG. Release all temporary `Mat` objects in `finally`; `close()` releases detector/recognizer.

- [ ] **Step 4: Run focused tests and assemble**

Expected: pure tests pass and Java bindings compile against OpenCV 4.13.0.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/huimei/voice/face app/src/test/java/com/huimei/voice/face
git commit -m "feat: extract OpenCV face embeddings"
```

### Task 5: Enrollment and recognition controller

**Files:**
- Create: `app/src/main/java/com/huimei/voice/face/FaceRecognitionListener.java`
- Create: `app/src/main/java/com/huimei/voice/face/FaceRecognitionController.java`
- Test: `app/src/test/java/com/huimei/voice/face/FaceRecognitionControllerTest.java`

**Interfaces:**
- Consumes: `FaceRepository`, `FaceEngine`, immutable `FaceFrame` objects.
- Produces: states `NEW/LOADING/IDLE/ENROLLING/RECOGNIZING/CLOSED`; methods `initialize`, `startEnrollment(name)`, `startRecognition`, `acceptFrame(FaceFrame,long)`, `stop`, `delete`, `clear`, `close`.

- [ ] **Step 1: Write controller RED tests using a small engine interface fake**

Cover five accepted samples saving an averaged vector; rejected-quality samples not increasing progress; 150 ms frame throttling; third same-person frame confirming once; candidate changes resetting; close suppressing stale callbacks; missing profiles blocking recognition; repository error reaching `onError` and returning to IDLE.

- [ ] **Step 2: Run controller tests and verify RED**

Expected: controller/listener interfaces are missing.

- [ ] **Step 3: Implement minimal single-threaded state machine**

Use one `ExecutorService`, a monotonically increasing lifecycle token, immutable callback payloads, and main-thread `Handler`. The controller calls the small `FaceEngine` interface and never receives CameraX/OpenCV types, so JVM tests use a deterministic fake engine.

- [ ] **Step 4: Run focused and full unit suites**

Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/huimei/voice/face app/src/test/java/com/huimei/voice/face
git commit -m "feat: orchestrate face enrollment and matching"
```

### Task 6: CameraX Activity and demo navigation

**Files:**
- Create: `app/src/main/java/com/huimei/voice/FaceRecognitionActivity.java`
- Create: `app/src/main/java/com/huimei/voice/face/RgbaFrameConverter.java`
- Create: `app/src/main/res/layout/activity_face_recognition.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/huimei/voice/DemoDestination.java`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values/colors.xml` only if the existing palette lacks status colors.
- Modify: `app/src/test/java/com/huimei/voice/DemoDestinationTest.java`
- Create: `app/src/androidTest/java/com/huimei/voice/FaceRecognitionNavigationTest.java`

**Interfaces:**
- Consumes: controller and OpenCV engine.
- Produces: seventh launcher-list entry and a self-contained CameraX screen.

- [ ] **Step 1: Add RED navigation tests**

Assert `DemoDestination.FACE_RECOGNITION` targets `FaceRecognitionActivity`, title/summary resources exist, and the instrumentation test can launch/recreate the Activity without granting camera permission while seeing the permission explanation.

- [ ] **Step 2: Run tests and verify RED**

Expected: missing enum/activity/resources fail compilation.

- [ ] **Step 3: Build the page and permission flow**

Layout uses a top `PreviewView`, compact status/result/performance cards, name input, profile spinner, buttons for “开始录入 / 开始识别 / 停止 / 删除 / 清空”, and a scrollable timestamped log. Add `CAMERA` permission. Prefer front camera, fall back to back camera, and show “设备未检测到可用摄像头” when neither exists.

- [ ] **Step 4: Bind CameraX safely**

Use RGBA `ImageAnalysis`, `STRATEGY_KEEP_ONLY_LATEST`, a single analysis executor and `try/finally { image.close(); }`. Apply rotation before engine analysis, drop frames while busy, and close/unbind in `onDestroy`. Disable destructive/profile actions during enrollment.

- [ ] **Step 5: Run unit tests and build instrumentation APK**

Run: `./gradlew -Dorg.gradle.java.home='/Applications/Android Studio.app/Contents/jbr/Contents/Home' :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest`

Expected: all tasks succeed.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/java/com/huimei/voice app/src/main/res app/src/test/java/com/huimei/voice/DemoDestinationTest.java app/src/androidTest/java/com/huimei/voice/FaceRecognitionNavigationTest.java
git commit -m "feat: add offline face recognition screen"
```

### Task 7: Documentation and real-device verification

**Files:**
- Modify: `README.md`
- Modify: `PROJECT_NOTES.md`
- Modify: `docs/face-recognition-research.md`
- Create: `docs/face-recognition-verification.md`
- Modify: `THIRD_PARTY_NOTICES.md`

**Interfaces:**
- Consumes: final APK, test APK, device evidence.
- Produces: reproducible build/install instructions and a fact/inference/unknown-separated validation record.

- [ ] **Step 1: Run final clean verification**

Run: `./gradlew -Dorg.gradle.java.home='/Applications/Android Studio.app/Contents/jbr/Contents/Home' clean :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest`

Expected: zero test failures and both APKs produced.

- [ ] **Step 2: Inspect packaged assets and native libraries**

Verify exact ONNX entry count, asset SHA-256 after extraction, ABI list, APK byte size and SHA-256. Record OpenCV/CameraX/model licenses and the SFace training-data legal-review caveat.

- [ ] **Step 3: Install and run instrumentation tests on the connected physical device**

Use explicit serial; preserve app data with `adb install -r -t --no-streaming`; require terminal `Success`. Run `am instrument -w`, then launch the Activity and separately record camera enumeration, foreground Activity, process status and crash log.

- [ ] **Step 4: Perform bounded live observations when hardware is available**

Record model-load time, PSS and at least one enrollment/match interaction only when a human supplies genuine frames. If no camera/device/user is available, mark those rows “未验证” rather than injecting stored photos.

- [ ] **Step 5: Update docs and commit**

```bash
git add README.md PROJECT_NOTES.md docs/face-recognition-research.md docs/face-recognition-verification.md THIRD_PARTY_NOTICES.md
git commit -m "docs: record face recognition verification"
```
