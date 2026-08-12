# 潓美医疗 Vosk 离线语音 Demo

这是一个基于官方 [vosk-android-demo](https://github.com/alphacep/vosk-android-demo) 改造的 Java/XML Android 示例。应用完全离线运行，支持中英文模型切换，并且只有在唤醒后 8 秒内才响应固定命令。

## 功能

- 中文唤醒词：`潓美医疗`（模型可能输出同音兼容文本 `惠美医疗`）。
- 英文唤醒词：`Hello Medical`。
- 唤醒前识别到命令时静默忽略，不写 UI 日志。
- 唤醒后 8 秒内第一个有效命令触发标准事件，随后自动回到休眠。
- 中英文切换会释放旧模型、加载新模型并恢复监听。
- UI 展示模型状态、唤醒倒计时、原始识别文字、标准词条和事件名。

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

## 构建

要求：

- Android SDK 36
- JDK 17 或更高版本；本机可直接使用 Android Studio 自带 JBR

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Debug APK 输出到：

```text
app/build/outputs/apk/debug/app-debug.apk
```

首次运行需要授予麦克风权限。默认加载中文模型并自动开始监听。

## 代码结构

- `CommandCatalog`：中英文限定语法和事件映射。
- `RecognitionResultParser`：解析 Vosk final/result JSON。
- `WakeCommandStateMachine`：休眠、唤醒和 8 秒超时逻辑。
- `VoiceRecognitionController`：模型解压、Vosk 识别器、麦克风和语言切换生命周期。
- `MainActivity`：权限、语言按钮、状态和日志 UI。

## 中文模型词表说明

`vosk-model-small-cn-0.22` 的词表包含 `惠美`、`医疗`、`产`、`气`，但不包含品牌生僻字词元 `潓美`，也不包含组合词元 `产气`。因此底层限定语法使用模型已有词元，例如 `惠美 医疗` 和 `半小时 产 气`；业务层会去除中文词元间空格，将唤醒事件统一显示为 `潓美医疗`。这不会改变用户需要说出的口令。

## 验收与安全边界

JVM 测试和 APK 构建只能验证程序逻辑与打包。中文品牌发音、英文口音、麦克风距离和工业噪声必须在目标 Android 真机上测试。

本 Demo 的 `POWER_ON`、`POWER_OFF` 等仅为日志事件，不控制真实医疗设备。量产接入必须通过 `VoiceRecognitionListener` 对接设备层，并保留实体安全联锁、危险动作确认和故障保护。

Vosk 0.3.75 的 Android 原生库还需要在目标设备上验证 16 KB 内存页兼容性；如需上架面向新设备的应用商店版本，应在发布前重新核对或重编 Vosk 原生库。

## 许可证

项目沿用 Apache License 2.0。第三方组件和模型来源见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
