# Vosk 工业语音控制 Android Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 构建一个可安装的 Java/XML Android Demo，使用 Vosk 在中文和英文之间切换，通过唤醒词开启 8 秒固定命令识别窗口，并在 UI 展示标准事件日志。

**Architecture:** 单个 Activity 通过 `VoiceRecognitionController` 管理当前语言的 Vosk `Model`、限定语法 `Recognizer` 与 `SpeechService`。纯 Java 的 `CommandCatalog`、`RecognitionResultParser` 和 `WakeCommandStateMachine` 负责可单元测试的词条映射、JSON 解析与状态门控；UI 只订阅状态、诊断和事件回调。

**Tech Stack:** Java 8、Android XML Views、Android Gradle Plugin 8.13.0、Gradle 8.13、Vosk Android 0.3.75、JNA 5.18.1、JUnit 4.13.2、两个 Apache-2.0 Vosk 小模型。

## Global Constraints

- 最低 Android 版本为 API 21，`compileSdk` 和 `targetSdk` 为 36。
- 唤醒窗口固定为 8,000 毫秒。
- 中文唤醒词为 `潓美医疗`，兼容识别别名 `惠美医疗`；英文唤醒词为 `Hello Medical`。
- 休眠时识别到命令必须静默，不写日志、不触发事件。
- 每次只加载当前语言模型；切换语言必须释放旧资源并重置唤醒状态。
- Demo 仅发出事件，不控制真实设备。
- 仅修改 `/Users/ws/project/零碎项目/语音识别 android`；基于官方 Demo，在本地分支 `feat/huimei-voice-demo` 分阶段提交。

---

## File Map

- `settings.gradle`, `build.gradle`, `gradle.properties`, `gradlew*`, `gradle/wrapper/*`: Gradle 工程与 wrapper。
- `app/build.gradle`: Android 应用、Vosk/JNA/JUnit 依赖和 ABI 配置。
- `models/build.gradle`, `models/src/main/assets/model-{cn,en-us}/**`: 模型资源模块。
- `app/src/main/java/com/huimei/voice/model/*`: 语言、状态、事件和动作值对象。
- `app/src/main/java/com/huimei/voice/recognition/CommandCatalog.java`: 固定词条与事件映射。
- `app/src/main/java/com/huimei/voice/recognition/RecognitionResultParser.java`: Vosk JSON 文本提取。
- `app/src/main/java/com/huimei/voice/recognition/WakeCommandStateMachine.java`: 8 秒状态机。
- `app/src/main/java/com/huimei/voice/recognition/VoiceRecognitionController.java`: Android/Vosk 生命周期适配。
- `app/src/main/java/com/huimei/voice/MainActivity.java`: 权限与 UI。
- `app/src/main/res/layout/activity_main.xml`: 单页功能界面。
- `app/src/test/java/com/huimei/voice/recognition/*Test.java`: 纯 JVM 行为测试。

---

### Task 1: Android 工程骨架与可执行测试环境

**Files:**
- Create: `settings.gradle`
- Create: `build.gradle`
- Create: `gradle.properties`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Create: `gradle/wrapper/gradle-wrapper.jar`
- Create: `gradlew`
- Create: `gradlew.bat`
- Create: `app/build.gradle`
- Create: `app/proguard-rules.pro`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `models/build.gradle`
- Create: `models/src/main/AndroidManifest.xml`
- Create: `local.properties`

**Interfaces:**
- Produces: 可运行的 `./gradlew :app:testDebugUnitTest` 和 `./gradlew :app:assembleDebug` 工程。

- [x] **Step 1: 创建最小 Gradle 工程**

  使用 AGP `8.13.0`、Gradle `8.13`、Java 8 source compatibility；应用 ID 为 `com.huimei.voice`。`app` 依赖 `appcompat:1.7.1`、`jna:5.18.1@aar`、`vosk-android:0.3.75@aar`、`project(':models')` 和 `junit:4.13.2`。

- [x] **Step 2: 获取可信 Gradle wrapper**

  下载 Gradle 8.13 官方发行包，复制其 wrapper jar，并使用官方 checksum 校验发行包。

- [x] **Step 3: 验证工程配置可解析**

  Run: `JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew tasks --all`

  Expected: exit 0，任务列表包含 `testDebugUnitTest` 和 `assembleDebug`。

---

### Task 2: 固定词条目录与中英文事件映射

**Files:**
- Create: `app/src/test/java/com/huimei/voice/recognition/CommandCatalogTest.java`
- Create: `app/src/main/java/com/huimei/voice/model/VoiceLanguage.java`
- Create: `app/src/main/java/com/huimei/voice/model/CommandEvent.java`
- Create: `app/src/main/java/com/huimei/voice/model/CommandMatch.java`
- Create: `app/src/main/java/com/huimei/voice/recognition/CommandCatalog.java`

**Interfaces:**
- Produces: `CommandCatalog.forLanguage(VoiceLanguage)`、`normalize(String)`、`find(String): Optional<CommandMatch>`、`grammarJson(): String`、`wakePhrase(): String`、`assetModelPath(): String`。

- [x] **Step 1: 写入失败测试**

  测试逐字断言：三种 30 分钟中文说法均为 `GAS_30_MINUTES`；中英文所有唤醒和命令别名映射到预期事件；大小写与连续空白被规范化；未知词条返回 empty；grammar 包含 `[unk]`。

- [x] **Step 2: 运行测试并确认 RED**

  Run: `./gradlew :app:testDebugUnitTest --tests '*CommandCatalogTest'`

  Expected: 编译失败，缺少 `CommandCatalog` 等生产类型。

- [x] **Step 3: 实现最小目录**

  使用不可变 `LinkedHashMap<String, CommandMatch>` 保存显式别名；`CommandMatch` 包含原始规范化文本、标准显示词条和 `CommandEvent`。不实现任意数字或模糊匹配。

- [x] **Step 4: 运行测试并确认 GREEN**

  Run: `./gradlew :app:testDebugUnitTest --tests '*CommandCatalogTest'`

  Expected: 所有 `CommandCatalogTest` 通过。

---

### Task 3: Vosk JSON 解析器

**Files:**
- Create: `app/src/test/java/com/huimei/voice/recognition/RecognitionResultParserTest.java`
- Create: `app/src/main/java/com/huimei/voice/recognition/RecognitionResultParser.java`

**Interfaces:**
- Produces: `RecognitionResultParser.parseText(String): Optional<String>`。

- [x] **Step 1: 写入失败测试**

  断言 `{"text":"开机"}` 返回 `开机`，`{"partial":"开"}`、空 text、null、空白和无效 JSON 返回 empty；带 JSON 转义的英文文本被正确解析。

- [x] **Step 2: 运行测试并确认 RED**

  Run: `./gradlew :app:testDebugUnitTest --tests '*RecognitionResultParserTest'`

  Expected: 编译失败，缺少解析器。

- [x] **Step 3: 实现最小解析器**

  使用 Android 自带 `org.json.JSONObject` 读取 `text`。通过 `unitTests.returnDefaultValues = true` 不足以执行 `org.json`，因此测试依赖加入 `org.json:json:20250517`，生产代码仍使用同一 API，不引入额外运行时层。

- [x] **Step 4: 运行测试并确认 GREEN**

  Run: `./gradlew :app:testDebugUnitTest --tests '*RecognitionResultParserTest'`

  Expected: 全部通过。

---

### Task 4: 唤醒与命令状态机

**Files:**
- Create: `app/src/test/java/com/huimei/voice/recognition/WakeCommandStateMachineTest.java`
- Create: `app/src/main/java/com/huimei/voice/model/ListeningState.java`
- Create: `app/src/main/java/com/huimei/voice/model/RecognitionAction.java`
- Create: `app/src/main/java/com/huimei/voice/recognition/TimeSource.java`
- Create: `app/src/main/java/com/huimei/voice/recognition/WakeCommandStateMachine.java`

**Interfaces:**
- Consumes: `CommandCatalog.find(String)`。
- Produces: `accept(String): RecognitionAction`、`pollTimeout(): RecognitionAction`、`reset(CommandCatalog)`、`remainingMillis()`、`state()`。

- [x] **Step 1: 写入失败测试**

  使用可变假时钟覆盖：休眠命令静默；中英文唤醒；8 秒内命令触发后回到休眠；截止时刻后命令不触发并返回超时；未知语音不关闭窗口；语言重置关闭窗口；重复唤醒刷新窗口。

- [x] **Step 2: 运行测试并确认 RED**

  Run: `./gradlew :app:testDebugUnitTest --tests '*WakeCommandStateMachineTest'`

  Expected: 编译失败，缺少状态机。

- [x] **Step 3: 实现最小状态机**

  `RecognitionAction` 的类型为 `NONE/WOKE_UP/COMMAND/TIMED_OUT`，命令动作携带 `CommandMatch`。超时判断使用 `now >= deadline`，窗口内合法命令完成后立即回到 `SLEEPING`。

- [x] **Step 4: 运行测试并确认 GREEN**

  Run: `./gradlew :app:testDebugUnitTest --tests '*WakeCommandStateMachineTest'`

  Expected: 全部通过。

---

### Task 5: Vosk Android 控制器与资源生命周期

**Files:**
- Create: `app/src/main/java/com/huimei/voice/recognition/VoiceRecognitionController.java`
- Create: `app/src/main/java/com/huimei/voice/recognition/VoiceRecognitionListener.java`

**Interfaces:**
- Consumes: `StorageService.unpack`、`Recognizer(Model, 16000f, grammarJson)`、`SpeechService`、解析器和状态机。
- Produces: `start(VoiceLanguage)`、`switchLanguage(VoiceLanguage)`、`stop()`、`close()`；回调 `onStatus`、`onDiagnostic`、`onWakeWindowChanged`、`onCommand`。

- [x] **Step 1: 编写控制器接口和最小编译实现**

  使用单调递增 generation token 阻止过期模型加载覆盖新语言；每次开始/切换先释放旧 `SpeechService`、`Recognizer` 和 `Model`。partial JSON 不进入状态机，只有 result/final result 被解析。

- [x] **Step 2: 添加主线程 250ms 倒计时轮询**

  唤醒后更新剩余秒数；`pollTimeout()` 产生 `TIMED_OUT` 时写诊断并通知 UI 恢复休眠。

- [x] **Step 3: 编译验证**

  Run: `./gradlew :app:compileDebugJavaWithJavac`

  Expected: exit 0，无 Java 编译错误。

---

### Task 6: MainActivity 与日志 UI

**Files:**
- Create: `app/src/main/java/com/huimei/voice/MainActivity.java`
- Create: `app/src/main/java/com/huimei/voice/ui/EventLogFormatter.java`
- Create: `app/src/main/res/layout/activity_main.xml`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/colors.xml`
- Create: `app/src/main/res/values/themes.xml`
- Create: `app/src/main/res/drawable/status_background.xml`

**Interfaces:**
- Consumes: `VoiceRecognitionController` 回调。
- Produces: 权限流程、语言 RadioGroup、开始/停止、清空日志、状态/倒计时/日志显示。

- [x] **Step 1: 创建功能型 XML 布局**

  页面包含标题、语言 RadioGroup、模型/唤醒词/状态/倒计时 TextView、开始停止与清空按钮、ScrollView 内日志 TextView。

- [x] **Step 2: 实现 Activity**

  默认中文；首次启动请求 `RECORD_AUDIO`；授予后自动加载并监听。切换语言调用 controller，开始按钮在停止/运行间切换，清空只清 UI。事件行包括时间、语言、类型、原始词条、标准词条与事件名。

- [x] **Step 3: 配置 Manifest 与主题**

  仅申请 `RECORD_AUDIO`；Launcher Activity 按 Android 要求导出；应用标签为 `潓美医疗语音 Demo`。

- [x] **Step 4: 编译资源和应用**

  Run: `./gradlew :app:compileDebugJavaWithJavac :app:processDebugResources`

  Expected: exit 0。

---

### Task 7: 下载、校验并打包中英文模型

**Files:**
- Create: `models/src/main/assets/model-cn/**`
- Create: `models/src/main/assets/model-en-us/**`
- Create: `models/src/main/assets/model-cn/uuid`
- Create: `models/src/main/assets/model-en-us/uuid`
- Create: `THIRD_PARTY_NOTICES.md`
- Create: `README.md`

**Interfaces:**
- Produces: `StorageService.unpack(context, "model-cn", "model", ...)` 与英文对应路径可用。

- [x] **Step 1: 从 Vosk 官方模型地址下载两个压缩包**

  下载 `vosk-model-small-cn-0.22.zip` 和 `vosk-model-small-en-us-0.15.zip` 到临时目录，记录 SHA-256 到第三方说明，不把 zip 留在工程中。

- [x] **Step 2: 解压并规范化 assets 目录名**

  保留模型全部运行文件，添加内容版本 UUID；不修改模型内部图文件。

- [x] **Step 3: 写 README 和许可证说明**

  说明构建、安装、唤醒/命令表、语言切换、真机验收、安全边界、模型来源和 Apache 2.0 许可证。

- [x] **Step 4: 验证资源存在**

  Run: `find models/src/main/assets -type f | sort` 并检查两个模型各有 `am/final.mdl`、`conf/mfcc.conf`、`graph/Gr.fst`、`uuid`。

---

### Task 8: 全量验证与 APK 检查

**Files:**
- Modify only if verification exposes an in-scope defect.

**Interfaces:**
- Produces: 通过测试、可构建 Debug APK 和验证报告。

- [x] **Step 1: 运行所有 JVM 测试**

  Run: `JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew clean testDebugUnitTest`

  Expected: exit 0，0 failures。

- [x] **Step 2: 构建 Debug APK**

  Run: `JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew assembleDebug`

  Expected: exit 0，生成 `app/build/outputs/apk/debug/app-debug.apk`。

- [x] **Step 3: 检查 APK 内容**

  Run: `unzip -l app/build/outputs/apk/debug/app-debug.apk`

  Expected: 包含两套模型 assets，以及 `arm64-v8a`、`armeabi-v7a`、`x86_64` 的 `libvosk.so`/JNA 原生库；不要求 x86。

- [x] **Step 4: 汇总不可替代的真机验证**

  报告 APK 路径、文件大小、测试数、构建结果，并明确真实中文品牌词发音、英文词条和工业噪声仍需用户在 Android 真机验证。
