# 潓美医疗 Vosk 离线语音 Demo

这是一个基于官方 [vosk-android-demo](https://github.com/alphacep/vosk-android-demo) 改造的 Java/XML Android 示例。启动页以列表提供离线语音识别、医疗助手 Lottie V5、语音与动画联动，以及任意文字离线口播四个 Demo，均完全离线运行。

## 功能

- 中文唤醒词：`潓美医疗`（模型可能输出同音兼容文本 `惠美医疗`）。
- 英文唤醒词：`Hello Medical`。
- 唤醒前识别到命令时静默忽略，不写 UI 日志。
- 唤醒后 8 秒内第一个有效命令触发标准事件，随后自动回到休眠。
- 中英文切换会释放旧模型、加载新模型并恢复监听。
- UI 展示模型状态、唤醒倒计时、原始识别文字、标准词条和事件名。
- 使用 Android 原生 `MediaPlayer` 离线播放中英文唤醒、1 小时产气和 8 小时产气提示音。
- 使用 Lottie Android 离线加载医疗助手 V5，支持循环播放、暂停和重播。
- 语音与动画联动页在待命时固定人物和闭口口型，仅保留眼睛动画；播放提示音时恢复口型，播放结束后自动回到待命。
- 任意文字口播页使用 sherpa-onnx 和分语言 VITS 模型：中文 AISHELL3 两种女声，英文 Piper LJSpeech 女声；可切换 0.8/1.0/1.2 三档语速和 0.5×/1.0×/1.5× 三档标点停顿，并按 PCM 能量实时驱动四个嘴型。
- 中英文 TTS 模型按当前语言切换，内存中只保留一套；生成回调只把 PCM 放入有界队列，播放线程独立消费，避免标点分句时的数秒断口。

## 命令

| 中文说法 | English phrase | 事件 |
|---|---|---|
| 半小时产气、三十分钟产气、30 分钟产气 | half hour gas production / thirty minute gas production | `GAS_30_MINUTES` |
| 一小时产气、1 小时产气 | one hour gas production | `GAS_1_HOUR` |
| 两小时产气、2 小时产气 | two hour(s) gas production | `GAS_2_HOURS` |
| 八小时产气、8 小时产气 | eight hour(s) gas production | `GAS_8_HOURS` |
| 开机 | power on | `POWER_ON` |
| 关机 | power off | `POWER_OFF` |

语音中的阿拉伯数字仍按中文读音说出，例如界面标准词条 `30分钟产气` 应读作“三十分钟产气”。

## 离线提示音

中文和 English 的 `WAKE_UP`、`GAS_1_HOUR`、`GAS_8_HOURS` 已配置用户提供的 MP3。播放期间 Vosk 会暂停，避免设备识别自己的扬声器；唤醒提示结束后重新开始完整 8 秒命令窗口。尚未提供音频的 30 分钟、2 小时、开机和关机事件仍正常触发并记录日志，但不播放声音。

提示音位于 `app/src/main/res/raw/`，使用标准事件命名。后续增加语言包时可以为相同事件提供对应语言音频。

## 构建

要求：

- Android SDK 36
- JDK 17 或更高版本；本机可直接使用 Android Studio 自带 JBR

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew lintDebug
```

Debug APK 输出到：

```text
app/build/outputs/apk/debug/app-debug.apk
```

启动后先选择 Demo。进入语音识别 Demo 时需要授予麦克风权限，默认加载中文模型并自动开始监听。

## 代码结构

- `CommandCatalog`：中英文限定语法和事件映射。
- `RecognitionResultParser`：解析 Vosk final/result JSON。
- `WakeCommandStateMachine`：休眠、唤醒和 8 秒超时逻辑。
- `VoiceRecognitionController`：模型解压、Vosk 识别器、麦克风和语言切换生命周期。
- `MainActivity`：Demo 列表和页面跳转。
- `VoiceRecognitionActivity`：权限、语言按钮、状态和日志 UI。
- `LottieDemoActivity`：从 `app/src/main/assets/medical-assistant-talking-v5.json` 加载并控制医疗助手动画。
- `VoiceAvatarActivity`：组合语音识别、提示音和医疗助手动画。
- `ArbitraryTtsActivity`：任意文字、语言、女声、语速、标点停顿和口播动画 UI。
- `LightweightTtsConfigFactory`：配置中文 AISHELL3 和英文 Piper LJSpeech VITS 模型。
- `OfflineTtsPlayer`：按语言加载模型，将流式生成与 `AudioTrack` 播放解耦。
- `LottieAvatarMotionController`：控制待命位置及闭口、小口、中口、大口四个嘴型。

## 中文模型词表说明

`vosk-model-small-cn-0.22` 的词表包含 `惠美`、`医疗`、`产`、`气`，但不包含品牌生僻字词元 `潓美`，也不包含组合词元 `产气`。因此底层限定语法使用模型已有词元，例如 `惠美 医疗` 和 `半小时 产 气`；业务层会去除中文词元间空格，将唤醒事件统一显示为 `潓美医疗`。这不会改变用户需要说出的口令。

## 验收与安全边界

JVM 测试和 APK 构建只能验证程序逻辑与打包。中文品牌发音、英文口音、麦克风距离和工业噪声必须在目标 Android 真机上测试。

本 Demo 的 `POWER_ON`、`POWER_OFF` 等仅为日志事件，不控制真实医疗设备。量产接入必须通过 `VoiceRecognitionListener` 对接设备层，并保留实体安全联锁、危险动作确认和故障保护。

Vosk 0.3.75 的 Android 原生库还需要在目标设备上验证 16 KB 内存页兼容性；如需上架面向新设备的应用商店版本，应在发布前重新核对或重编 Vosk 原生库。

## 许可证

项目沿用 Apache License 2.0。第三方组件和模型来源见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
