# 离线事件提示音 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 使用 Android 原生 `MediaPlayer` 为中英文唤醒、1 小时产气和 8 小时产气事件播放用户提供的离线 MP3，并在播放期间安全暂停和恢复 Vosk。

**Architecture:** `VoicePromptCatalog` 负责标准事件到 `raw` 资源的纯映射，`VoicePromptPlayer` 独占并释放一个 `MediaPlayer` 实例，`MainActivity` 在现有识别回调中编排播放。`VoiceRecognitionController` 提供提示音暂停/恢复能力，`WakeCommandStateMachine` 在唤醒提示音结束后重启完整 8 秒窗口。

**Tech Stack:** Java 8、Android `MediaPlayer`、Android raw resources、Vosk Android 0.3.75、JUnit 4.13.2。

## Global Constraints

- 不增加第三方播放器或 TTS 依赖。
- 只为用户提供的 `WAKE_UP`、`GAS_1_HOUR`、`GAS_8_HOURS` 播放中英文音频。
- 未配置音频的事件仍正常触发和记录日志。
- 播放期间暂停 Vosk；完成或失败后恢复。
- 唤醒音结束后重新提供完整 8,000 毫秒命令窗口。
- 切换语言、停止监听和销毁页面时停止并释放音频。

---

### Task 1: 音频资源与事件映射

**Files:**
- Create: `app/src/test/java/com/huimei/voice/audio/VoicePromptCatalogTest.java`
- Create: `app/src/main/java/com/huimei/voice/audio/VoicePromptCatalog.java`
- Create: `app/src/main/res/raw/zh_wake_up.mp3`
- Create: `app/src/main/res/raw/zh_gas_1_hour.mp3`
- Create: `app/src/main/res/raw/zh_gas_8_hours.mp3`
- Create: `app/src/main/res/raw/en_wake_up.mp3`
- Create: `app/src/main/res/raw/en_gas_1_hour.mp3`
- Create: `app/src/main/res/raw/en_gas_8_hours.mp3`

**Interfaces:**
- Produces: `VoicePromptCatalog.rawResourceFor(VoiceLanguage, CommandEvent): int`，未配置返回 `0`。

- [ ] **Step 1: 写入失败测试**

  断言中文和英文的三个事件分别返回对应 `R.raw`；30 分钟、2 小时、开机和关机返回 `0`。

- [ ] **Step 2: 运行测试并确认 RED**

  Run: `./gradlew :app:testDebugUnitTest --tests '*VoicePromptCatalogTest'`

  Expected: 编译失败，缺少 `VoicePromptCatalog`。

- [ ] **Step 3: 复制六个 MP3 并实现最小映射**

  使用显式 `switch`，不通过文件名反射查找资源。

- [ ] **Step 4: 运行测试并确认 GREEN**

  Run: `./gradlew :app:testDebugUnitTest --tests '*VoicePromptCatalogTest'`

  Expected: 全部通过。

- [ ] **Step 5: 提交**

  Commit: `feat: add bilingual event prompt assets`

---

### Task 2: 唤醒窗口暂停与重新计时

**Files:**
- Modify: `app/src/test/java/com/huimei/voice/recognition/WakeCommandStateMachineTest.java`
- Modify: `app/src/main/java/com/huimei/voice/recognition/WakeCommandStateMachine.java`
- Modify: `app/src/main/java/com/huimei/voice/recognition/VoiceRecognitionController.java`

**Interfaces:**
- Produces: `WakeCommandStateMachine.restartWakeWindow(): boolean`。
- Produces: `VoiceRecognitionController.pauseForPrompt(): boolean`、`resumeAfterPrompt(): void`。

- [ ] **Step 1: 写入失败测试**

  使用假时钟唤醒、推进时间、调用 `restartWakeWindow()`，断言剩余时间恢复为 8,000 毫秒；休眠状态调用返回 `false` 且不打开窗口。

- [ ] **Step 2: 运行测试并确认 RED**

  Run: `./gradlew :app:testDebugUnitTest --tests '*WakeCommandStateMachineTest'`

  Expected: 编译失败，缺少 `restartWakeWindow()`。

- [ ] **Step 3: 实现状态机最小能力**

  仅在 `AWAKE` 时把 deadline 更新为 `now + WAKE_WINDOW_MILLIS` 并返回 `true`。

- [ ] **Step 4: 实现控制器暂停/恢复**

  暂停时设置门控、移除倒计时并调用 `SpeechService.setPause(true)`；播放期间丢弃迟到识别结果。恢复时解除暂停并调用 `setPause(false)`；若仍为 `AWAKE`，重启 8 秒窗口、更新 UI 并恢复倒计时。`start`、`stop`、语言切换和资源释放均清除暂停标记。

- [ ] **Step 5: 运行单元测试和 Java 编译**

  Run: `./gradlew :app:testDebugUnitTest :app:compileDebugJavaWithJavac`

  Expected: exit 0。

- [ ] **Step 6: 提交**

  Commit: `feat: pause recognition during voice prompts`

---

### Task 3: MediaPlayer 与识别回调集成

**Files:**
- Create: `app/src/main/java/com/huimei/voice/audio/VoicePromptPlayer.java`
- Modify: `app/src/main/java/com/huimei/voice/MainActivity.java`
- Modify: `README.md`

**Interfaces:**
- Produces: `VoicePromptPlayer.play(int, Listener): boolean`、`stop(): void`、`close(): void`。
- Listener: `onCompleted()`、`onError(String)`。

- [ ] **Step 1: 实现单实例播放器生命周期**

  `play` 先释放旧播放器，再用 `MediaPlayer.create` 加载 raw 资源；完成与错误回调都只触发一次、释放资源。`stop` 不触发旧回调，防止语言切换后恢复过期识别会话。

- [ ] **Step 2: 接入 Activity**

  `onWakeUp` 和 `onCommand` 写日志后查询映射；有资源时先 `pauseForPrompt()` 再播放，完成或错误均调用 `resumeAfterPrompt()`。切换语言、停止按钮和 `onDestroy` 先停止播放器。

- [ ] **Step 3: 更新 README**

  记录已配置事件、播放期间暂停识别、未提供音频事件保持静默以及后续语言包可替换同名文件。

- [ ] **Step 4: 全量验证**

  Run: `./gradlew clean testDebugUnitTest assembleDebug lintDebug`

  Expected: 0 test failures，构建成功，lint 0 errors。

- [ ] **Step 5: APK 内容检查**

  Run: `unzip -l app/build/outputs/apk/debug/app-debug.apk | rg 'res/raw/.+\\.mp3'`

  Expected: 六个 MP3 全部打包。

- [ ] **Step 6: 真机安装与状态验证**

  安装 APK、授予录音权限并启动；确认中文模型进入休眠监听。由实际发音验证中文/英文唤醒、1 小时和 8 小时提示音，观察播放期间无自身回声事件，唤醒音结束后倒计时从 8 秒开始。

- [ ] **Step 7: 提交**

  Commit: `feat: play offline prompts for voice events`
