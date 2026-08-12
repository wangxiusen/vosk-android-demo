# Vosk 工业语音控制 Android Demo 设计

## 目标

在空目录中创建一个开源、免费、完全离线的 Android Demo。Demo 使用 Vosk Android，在中文和英文之间切换，以固定唤醒词开启 8 秒命令窗口，识别指定的产气时长与开关机命令，并将标准事件及诊断信息展示在 UI 日志中。

Demo 只发出标准事件回调，不直接控制真实医疗或工业设备。实际接入 PLC、串口或设备 SDK 时，由集成方实现事件回调。

## 范围

### 包含

- Java 8 与传统 XML View Android 工程。
- 官方 Vosk Android AAR、JNA Android AAR 和官方小型中英文模型。
- 录音权限、模型解压、持续离线识别、语言切换和资源释放。
- 固定语法词表、唤醒状态机、命令事件映射和 UI 日志。
- JVM 单元测试与 Debug APK 构建验证。

### 不包含

- 真实设备、PLC、串口、网络接口或医疗操作。
- 后台 Service、开机自启动和锁屏常驻。
- 自动语言检测、云端识别、自由文本听写和语音播报。
- 训练自定义声学模型。
- 在模拟器中宣称验证真实麦克风或工业噪声识别效果。

## 技术选型

- Android Java/XML，最低 Android 5.0（API 21）。
- `com.alphacephei:vosk-android:0.3.75@aar`。
- `net.java.dev.jna:jna:5.18.1@aar`。
- 中文模型 `vosk-model-small-cn-0.22`。
- 英文模型 `vosk-model-small-en-us-0.15`。
- 只使用 Maven Central 和模型的官方发布地址。

用户提供的 `com.alphacephei:vosk` 和桌面 JNA 写法适用于 Linux、macOS 与 Windows 的 Java。Android 工程采用官方 Android Demo 当前使用的 `vosk-android` 与 `jna@aar` 依赖。

Vosk API 和以上两个选定模型均采用 Apache 2.0 许可证。工程保留第三方许可证说明。

## 识别架构

每次只加载当前语言的一个模型和一个识别器。当前语言的唤醒词与命令词同时进入 Vosk 限定语法。业务状态机决定是否消费识别结果：

1. 休眠状态持续监听。
2. 休眠时只有唤醒词会被消费；命令词和其他结果静默丢弃，不写 UI 日志。
3. 命中唤醒词后进入 `AWAKE` 状态并启动 8 秒窗口。
4. 窗口内第一个有效命令触发标准事件、写日志，并立即返回休眠状态。
5. 8 秒内没有有效命令则记录唤醒超时，并返回休眠状态。
6. 语言切换停止录音并释放旧资源，加载新模型与词表，重置为休眠，再自动恢复监听。

持续识别的音频生命周期沿用官方 Demo：`StorageService` 解压 assets 模型，`Model` 与带限定语法的 `Recognizer` 创建识别器，`SpeechService` 读取麦克风，并通过 `RecognitionListener` 返回 JSON 结果。

## 唤醒词和事件映射

内部事件枚举：

- `WAKE_UP`
- `GAS_30_MINUTES`
- `GAS_1_HOUR`
- `GAS_2_HOURS`
- `GAS_8_HOURS`
- `POWER_ON`
- `POWER_OFF`

### 中文

| 原始可识别词条 | 标准显示词条 | 事件 |
|---|---|---|
| 潓美医疗、惠美医疗 | 潓美医疗 | `WAKE_UP` |
| 半小时产气、三十分钟产气、30分钟产气 | 30分钟产气 | `GAS_30_MINUTES` |
| 一小时产气、1小时产气 | 1小时产气 | `GAS_1_HOUR` |
| 两小时产气、2小时产气 | 2小时产气 | `GAS_2_HOURS` |
| 八小时产气、8小时产气 | 8小时产气 | `GAS_8_HOURS` |
| 开机 | 开机 | `POWER_ON` |
| 关机 | 关机 | `POWER_OFF` |

`惠美医疗` 是中文声学识别对品牌生僻字的兼容别名。品牌在 UI 中始终显示为 `潓美医疗`，诊断日志保留 Vosk 原始识别文字。

### 英文

| 原始可识别词条 | 标准显示词条 | 事件 |
|---|---|---|
| hello medical | Hello Medical | `WAKE_UP` |
| half hour gas production、thirty minute gas production | 30-minute gas production | `GAS_30_MINUTES` |
| one hour gas production | 1-hour gas production | `GAS_1_HOUR` |
| two hour gas production、two hours gas production | 2-hour gas production | `GAS_2_HOURS` |
| eight hour gas production、eight hours gas production | 8-hour gas production | `GAS_8_HOURS` |
| power on | Power on | `POWER_ON` |
| power off | Power off | `POWER_OFF` |

识别输入在映射前执行两侧空白清理、转小写以及连续空白合并。中文阿拉伯数字与中文数字通过显式别名处理，不实现任意数字解析。

## 组件边界

### `CommandCatalog`

提供每种语言的模型目录、唤醒词、限定语法词条以及“原始词条到标准事件”的映射。它不访问 Android UI 或麦克风。

### `RecognitionResultParser`

从 Vosk 的 final/result JSON 中只提取 `text` 字段。缺失、空文本或无效 JSON 返回空结果并忽略，不让识别线程崩溃。

### `WakeCommandStateMachine`

维护 `SLEEPING` 和 `AWAKE` 状态、8 秒截止时间，并消费已规范化的词条。时间由可注入的时钟提供，使唤醒、命令和超时逻辑可在 JVM 中确定性测试。

状态机返回以下动作之一：

- `NONE`：静默忽略。
- `WOKE_UP`：显示唤醒状态并记录唤醒日志。
- `COMMAND`：携带 `CommandEvent`、标准词条和原始词条。
- `TIMED_OUT`：记录超时并回到休眠。

### `VoiceRecognitionController`

拥有当前 `Model`、`Recognizer` 与 `SpeechService`。负责异步模型加载、开始/停止监听、语言切换以及在 Activity 销毁时按顺序释放资源。它把 Vosk JSON 交给解析器和状态机，不直接操作 View。

### `CommandEventListener`

向设备集成层暴露标准事件回调。Demo 实现只把事件写入 UI 日志。后续实际设备控制不得在识别线程中阻塞执行。

### `MainActivity`

负责麦克风权限、语言选择、开始/停止按钮、清空日志、状态与倒计时显示。Activity 不包含词条映射规则。

## UI

单页面功能型界面，包含：

- 标题 `Vosk 工业语音控制 Demo`。
- 中文和 English 语言选择。
- 当前模型、当前唤醒词和识别状态。
- 唤醒后的剩余秒数。
- 开始/停止监听按钮。
- 清空日志按钮。
- 可滚动事件日志。

状态至少包含：

- 模型加载中。
- 休眠监听中。
- 已唤醒，请说命令。
- 已停止。
- 麦克风权限被拒绝。
- 模型加载或录音启动错误。

业务事件日志格式：

```text
时间 | 语言 | 类型 | 原始识别文字 | 标准词条 | 事件
```

示例：

```text
10:32:08 | 中文 | 唤醒 | 惠美医疗 | 潓美医疗 | WAKE_UP
10:32:11 | 中文 | 命令 | 三十分钟产气 | 30分钟产气 | GAS_30_MINUTES
```

模型加载、开始监听、停止监听、语言切换、唤醒超时和错误使用诊断日志行。休眠时识别到普通命令不产生任何日志或响应。

## 错误处理和安全边界

- 麦克风权限未授予时不启动录音，并在 UI 显示可恢复错误。
- 模型加载失败时释放已创建的资源，保持停止状态并写错误日志。
- 录音初始化失败或麦克风被占用时停止识别并写错误日志。
- 快速重复切换语言时，以最后一次选择为准；过期的异步模型加载结果不得覆盖当前语言。
- Activity 销毁时取消倒计时、停止 `SpeechService` 并关闭识别器与模型。
- `POWER_ON` 和 `POWER_OFF` 在 Demo 中仅为事件，不构成医疗设备控制或安全联锁。量产系统必须保留实体安全回路和危险动作确认机制。

## 模型打包

中英文模型存放于单独的 Android library 模块 `models` 的 assets 中，并各自包含 `uuid` 文件，沿用 Vosk `StorageService` 的版本缓存机制。模型被打入 Debug APK，因此 APK 会增加两个压缩后模型的体积。运行时只解压和加载当前选定语言。

## 测试与验收

### JVM 自动测试

- 休眠时命令返回 `NONE`。
- 中文 `潓美医疗` 与 `惠美医疗` 均唤醒。
- 英文 `Hello Medical` 经过规范化后唤醒。
- 唤醒后 8 秒内有效命令产生正确事件并回到休眠。
- 唤醒截止时刻之后的命令不触发事件。
- `半小时产气`、`三十分钟产气` 与 `30分钟产气` 均映射到 `GAS_30_MINUTES`。
- 所有中英文词条映射到设计表中的事件。
- 切换语言重置唤醒状态。
- 无效或空 Vosk JSON 被安全忽略。

### 构建验证

- `./gradlew testDebugUnitTest` 成功且无失败测试。
- `./gradlew assembleDebug` 成功并生成 Debug APK。
- 检查 APK 包含中英文模型资源及 `arm64-v8a`、`armeabi-v7a` 和 `x86_64` 原生库。

### 真机验收

- 首次启动请求麦克风权限并能加载默认中文模型。
- 未说唤醒词直接说任意命令，UI 无响应、无事件日志。
- 说“潓美医疗”或兼容发音后，UI 进入 8 秒唤醒状态。
- 8 秒内说每个中文命令，产生正确事件日志。
- 切换英文后使用 `Hello Medical` 唤醒并验证每个英文命令。
- 超时后直接说命令，不产生响应。
- 多次切换语言、停止和重新开始监听不崩溃。

真机验收是识别效果的必要条件；Debug APK 构建成功不能替代麦克风、发音和现场噪声验证。

