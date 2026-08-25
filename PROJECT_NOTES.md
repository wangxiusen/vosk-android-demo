# 潓美医疗离线语音 Demo：功能与工程知识记录

> 更新日期：2026-08-25
>
> 当前分支：`feat/huimei-voice-demo`
>
> 应用包名：`com.huimei.voice`

本文记录当前工程已经实现的功能、关键代码位置、Vosk 模型加载方式、验证结果，以及以后扩展词条、多语言、动态模型和氧气机控制时需要保留的工程边界。

`README.md` 用于快速使用说明；本文用于开发交接和后续维护。文中标注为“后续方案”的内容尚未在当前 Demo 中实现。

## 1. 当前技术方案

- Android Java + XML Views。
- 最低 Android API 21，当前使用 Android SDK 36 构建。
- 完全离线识别，不依赖网络和云端账号。
- Vosk Android：`com.alphacephei:vosk-android:0.3.75@aar`。
- JNA：`net.java.dev.jna:jna:5.18.1@aar`。
- 中文模型：`vosk-model-small-cn-0.22`。
- 英文模型：`vosk-model-small-en-us-0.15`。
- Android 原生 `MediaPlayer` 播放离线 MP3，不依赖 TTS SDK。
- Lottie Android 6.7.1 播放离线医疗助手 V5 JSON。
- sherpa-onnx 1.13.6 + 中文 AISHELL3 VITS + 英文 Piper LJSpeech VITS 提供任意文字离线 TTS。
- TTS 切换语言时释放旧模型，仅当前语言模型常驻；生成回调与 `AudioTrack` 播放通过有界 PCM 队列解耦。
- 当前模型、Vosk、JNA及工程代码所使用的许可证信息见 `THIRD_PARTY_NOTICES.md`。

工程模块：

```text
app/       Android 应用、UI、识别业务逻辑和提示音
models/    打包中英文 Vosk 模型的 Android Library
docs/      已确认的设计文档和实施计划
```

## 2. 已实现功能

### 2.1 中英文离线识别

- 默认加载中文模型。
- UI 可在中文和 English 之间切换。
- 切换语言会停止监听、释放旧识别器和旧模型，再加载目标语言模型。
- 内存中长期只保留当前语言的一套模型，不会同时常驻中英文模型。
- 使用 16 kHz 单声道麦克风音频进行识别。

### 2.2 唤醒后识别命令

- 中文唤醒词：`潓美医疗`。
- 英文唤醒词：`Hello Medical`。
- 未唤醒时，即使识别到命令，也会静默忽略，不触发业务事件，也不写命令日志。
- 识别到唤醒词后打开 8 秒命令窗口。
- 窗口内第一个有效命令触发事件，随后立即回到休眠监听状态。
- 8 秒内没有有效命令则自动超时并回到休眠监听状态。
- Vosk 的 partial result 不触发业务，只处理确定的 result/final result。

这不是独立的低功耗硬件 KWS。休眠状态下 Vosk 仍然持续读取麦克风并识别，只是业务状态机不响应普通命令。

### 2.3 当前词条和事件

多种说法可映射到同一个稳定事件。日志同时保留 Vosk 原始识别文字和标准词条。

| 中文说法 | 英文说法 | 内部事件 |
|---|---|---|
| 潓美医疗、惠美医疗 | Hello Medical | `WAKE_UP` |
| 半小时产气、三十分钟产气、30分钟产气 | half hour gas production、thirty minute gas production | `GAS_30_MINUTES` |
| 一小时产气、1小时产气 | one hour gas production | `GAS_1_HOUR` |
| 两小时产气、2小时产气 | two hour gas production、two hours gas production | `GAS_2_HOURS` |
| 八小时产气、8小时产气 | eight hour gas production、eight hours gas production | `GAS_8_HOURS` |
| 开机 | power on | `POWER_ON` |
| 关机 | power off | `POWER_OFF` |

阿拉伯数字是标准显示形式，不是用户的发音方式。例如 `30分钟产气` 仍应说“三十分钟产气”。

### 2.4 UI日志

页面展示：

- 当前语言和模型目录名。
- 当前唤醒词。
- 模型加载、休眠、唤醒、播放提示音等状态。
- 唤醒剩余时间。
- 原始识别文字、标准词条和内部事件名。
- 模型加载、语言切换、停止监听、超时和异常等诊断日志。

页面还支持开始/停止监听及清空日志。

应用启动页现在是 Demo 列表：

- “离线语音识别 Demo”进入原有语音页面。
- “医疗助手 Lottie V5 Demo”从应用 Assets 离线加载动画，支持循环播放、暂停和重播。
- “语音 + 医疗助手动画 Demo”复用原有中英文识别和提示音逻辑。待命时人物居中、闭口并仅保留眼睛活动；提示音播放期间恢复 V5 口型，结束或失败后自动恢复待命。
- “任意文字口播动画 Demo”支持中英文文本、两种女声音色和 0.8/1.0/1.2 三档语速；TTS 流式 PCM 每 20ms 映射为四个嘴型，眼睛动画保持独立。

V5 动画文件为 `app/src/main/assets/medical-assistant-talking-v5.json`，包含口型、眨眼和视线动画所需的内嵌图片，不依赖网络资源。

### 2.5 离线提示音

以下六个 MP3 已打包到 `app/src/main/res/raw/`：

| 语言 | 事件 | 文件 |
|---|---|---|
| 中文 | `WAKE_UP` | `zh_wake_up.mp3` |
| 中文 | `GAS_1_HOUR` | `zh_gas_1_hour.mp3` |
| 中文 | `GAS_8_HOURS` | `zh_gas_8_hours.mp3` |
| English | `WAKE_UP` | `en_wake_up.mp3` |
| English | `GAS_1_HOUR` | `en_gas_1_hour.mp3` |
| English | `GAS_8_HOURS` | `en_gas_8_hours.mp3` |

播放逻辑：

1. 识别到已配置音频的事件。
2. 暂停 `SpeechService`，停止唤醒倒计时。
3. 使用单一 `MediaPlayer` 实例播放对应 MP3。
4. 播放完成或播放失败后恢复识别。
5. 如果刚才播放的是唤醒回应，则从恢复时刻重新开始完整的 8 秒命令窗口。

这样可以避免设备把自己的喇叭提示音再次识别为用户命令。

`GAS_30_MINUTES`、`GAS_2_HOURS`、`POWER_ON`、`POWER_OFF` 尚未配置音频；这些事件仍会正常触发和记录日志，只是不播放提示音。

## 3. 核心代码位置

| 文件 | 职责 |
|---|---|
| `app/src/main/java/com/huimei/voice/MainActivity.java` | Demo 列表和页面跳转 |
| `app/src/main/java/com/huimei/voice/VoiceRecognitionActivity.java` | 权限、语言按钮、UI状态、日志、提示音触发 |
| `app/src/main/java/com/huimei/voice/LottieDemoActivity.java` | V5 动画加载、播放、暂停、重播和生命周期管理 |
| `app/src/main/java/com/huimei/voice/VoiceAvatarActivity.java` | 中英文识别、提示音和 V5 动画联动页面 |
| `app/src/main/java/com/huimei/voice/ArbitraryTtsActivity.java` | 任意文字 TTS、语言、女声、语速和动画 UI |
| `app/src/main/java/com/huimei/voice/avatar/VoiceAvatarStateMachine.java` | 待命与口播状态转换 |
| `app/src/main/java/com/huimei/voice/avatar/LottieAvatarMotionController.java` | 待命时锁定闭口口型和人物位置，口播时恢复 V5 动画 |
| `app/src/main/java/com/huimei/voice/avatar/AudioEnergyMouthMapper.java` | PCM 均方根能量到四个嘴型的映射 |
| `app/src/main/java/com/huimei/voice/tts/LightweightTtsConfigFactory.java` | 中文 AISHELL3 和英文 Piper LJSpeech VITS 配置 |
| `app/src/main/java/com/huimei/voice/tts/PcmChunkQueue.java` | 生成线程与播放线程之间的有界、可取消 PCM 队列 |
| `app/src/main/java/com/huimei/voice/tts/OfflineTtsPlayer.java` | 模型切换、流式合成、队列播放、停止和释放 |
| `app/src/main/java/com/huimei/voice/tts/TtsVoiceCatalog.java` | AISHELL3 中文女声和 LJSpeech 英文女声 speaker ID 映射 |
| `app/src/main/java/com/huimei/voice/recognition/VoiceRecognitionController.java` | 模型加载、识别器、麦克风、语言切换、暂停恢复和资源生命周期 |
| `app/src/main/java/com/huimei/voice/recognition/CommandCatalog.java` | 每种语言的模型目录、唤醒词、固定语法和词条事件映射 |
| `app/src/main/java/com/huimei/voice/recognition/WakeCommandStateMachine.java` | 休眠、唤醒、8秒超时和单次命令状态机 |
| `app/src/main/java/com/huimei/voice/recognition/RecognitionResultParser.java` | 解析 Vosk JSON 中的最终识别文字 |
| `app/src/main/java/com/huimei/voice/recognition/VoiceRecognitionListener.java` | 将状态、唤醒和命令事件传递给UI或后续设备层 |
| `app/src/main/java/com/huimei/voice/model/CommandEvent.java` | 稳定的业务事件枚举 |
| `app/src/main/java/com/huimei/voice/audio/VoicePromptCatalog.java` | 语言、事件到 raw 音频资源的映射 |
| `app/src/main/java/com/huimei/voice/audio/VoicePromptPlayer.java` | 单实例 `MediaPlayer` 播放和释放 |
| `app/src/main/java/com/huimei/voice/ui/EventLogFormatter.java` | UI事件与诊断日志格式化 |

## 4. 模型存放、切换和加载

### 4.1 模型源文件

模型随 APK 打包，位于：

```text
models/src/main/assets/model-cn/
models/src/main/assets/model-en-us/
```

每套模型包含声学模型、解码图、配置、i-vector文件及 `uuid` 缓存标记，例如：

```text
am/final.mdl
conf/mfcc.conf
conf/model.conf
graph/Gr.fst
graph/HCLr.fst
ivector/...
uuid
```

`app/build.gradle` 通过下面的依赖把 `models` 模块及其 Assets 合入 APK：

```gradle
implementation project(':models')
```

### 4.2 语言与模型目录映射

`CommandCatalog.forLanguage(...)` 选择当前语言配置：

```text
CHINESE -> model-cn    -> 潓美医疗 + 中文命令语法
ENGLISH -> model-en-us -> Hello Medical + 英文命令语法
```

中文底层语法使用当前模型词表实际存在的词元。例如模型能识别 `惠美`，但没有品牌生僻字 `潓`；因此底层使用 `惠美 医疗`，业务层统一显示为 `潓美医疗`。`产气`同样使用模型已有的 `产`、`气`词元组合。

### 4.3 切换调用链

```text
用户点击中文或 English
  -> MainActivity.selectLanguage(...)
  -> VoiceRecognitionController.switchLanguage(...)
  -> start(目标语言)
  -> CommandCatalog.forLanguage(目标语言)
  -> releaseRecognitionResources()
  -> StorageService.unpack(...)
  -> onModelLoaded(...)
  -> new Recognizer(model, 16000, grammarJson)
  -> new SpeechService(recognizer, 16000)
  -> speechService.startListening(...)
```

核心加载代码使用：

```java
StorageService.unpack(
        context,
        catalog.assetModelPath(),
        "cache-" + catalog.assetModelPath(),
        loadedModel -> onModelLoaded(requestGeneration, requestedLanguage, loadedModel),
        error -> onModelLoadFailed(requestGeneration, error));
```

`StorageService.unpack` 在后台线程完成以下工作：

1. 读取 Assets 模型目录中的 `uuid`。
2. 检查应用专属文件目录中的已解压副本。
3. UUID一致时复用缓存；不一致或首次运行时重新复制模型文件。
4. 用解压后的本地路径创建 `Model`。
5. 在主线程回调加载成功或失败。

首次加载某个语言时需要解压，所以较慢；后续切换一般直接复用缓存。

### 4.4 固定词条语法

当前不是对完整语言自由听写，而是将固定词条生成 JSON 数组后传给识别器：

```java
recognizer = new Recognizer(model, 16_000.0f, catalog.grammarJson());
```

语法尾部包含 `[unk]`。限定语法可以减少搜索空间、提升固定命令场景的速度和准确率，但它不会把完整声学模型缩减成只含几条命令的微型模型。

### 4.5 快速连续切换保护

模型解压和加载是异步操作。控制器使用单调递增的 `generation` 标记每次加载请求；回调到达时必须同时满足：

- 控制器未关闭。
- 用户仍然希望保持运行。
- generation仍是最新值。
- 当前语言仍等于该回调请求的语言。

过期回调拿到的 `Model` 会立即关闭，不能覆盖用户最后选择的语言。

### 4.6 资源释放顺序

切换、停止或页面销毁时按以下顺序释放：

```text
停止并 shutdown SpeechService
  -> close Recognizer
  -> close Model
```

模型是主要内存占用来源。仅把业务状态设为“未唤醒”不会减少模型内存；完全释放模型才会显著降低该部分内存，但下一次启动需要重新创建模型和识别器。

## 5. 构建与已完成验证

推荐使用 Android Studio 自带 JBR：

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew clean testDebugUnitTest assembleDebug lintDebug
```

APK输出：

```text
app/build/outputs/apk/debug/app-debug.apk
```

截至本文记录时，最近一次完整验证结果为：

- Gradle构建成功。
- 20个JVM单元测试通过。
- Debug APK约98 MB。
- 中文模型目录约65 MB。
- 英文模型目录约68 MB。
- 华为 Android 12 真机完成中文和英文识别验证。
- 已验证中文、英文唤醒以及1小时、8小时产气提示音触发。
- 英文真机日志确认识别 `Hello Medical` 和 `eight hours gas production`，并触发 `GAS_8_HOURS`。

这些结果是历史验收记录，不代表以后修改代码、模型、音频或目标硬件后仍自动成立。每次发布都应重新构建并在目标工业机上完成真机验收。

## 6. 性能、存储和工业机结论

针对当前 UI、单个Vosk小模型、固定词条、提示音及串口控制请求的负载：

- RK3568 + 4GB RAM + 32GB eMMC足够使用。
- 两套模型都会占APK和磁盘空间，但运行时只加载当前语言模型。
- 32GB eMMC可以容纳多套小型语言模型；仍需为系统、日志、升级包和模型双版本回退预留空间。
- eMMC品质、散热、供电稳定、硬件看门狗和系统固件长期支持比继续增加CPU或内存更重要。

PSS与RSS的简单理解：

- RSS是进程当前驻留在物理内存中的页面总量，其中共享页可能在多个进程中重复计算。
- PSS会把共享页按共享进程数分摊，更适合观察某个Android应用对系统内存的实际贡献。
- Java Heap只覆盖Java对象，不包含Vosk/JNA原生内存、映射文件和共享库，因此不能只看Java Heap判断总占用。

最终量产前应在目标RK3568整机上测量冷启动、中文/英文切换、持续监听、连续提示音播放、24至72小时运行、温升、PSS和CPU占用。

## 7. 后续扩展方法

### 7.1 增加固定词条

当前本地打包方式下：

1. 在 `CommandEvent` 增加新事件（只有确实需要新的业务语义时才增加）。
2. 在 `CommandCatalog` 的对应语言中增加识别说法、标准显示词条和事件映射。
3. 确认模型词表能够表示该说法；中文尤其要检查分词和生僻字。
4. 增加状态机或目录单元测试。
5. 如需播报，在 `res/raw` 添加音频并更新 `VoicePromptCatalog`。
6. 重新构建、安装和真机验证。

如果只是为已有事件增加同义说法，不要创建新事件，应把不同说法映射到同一个 `CommandEvent`。

### 7.2 增加一种语言

当前代码至少需要：

1. 在 `VoiceLanguage` 增加语言。
2. 准备对应的Vosk模型。
3. 在 `CommandCatalog` 增加该语言的模型路径、唤醒词、固定词条和语法。
4. 在UI增加语言选项。
5. 如需声音反馈，增加该语言的事件音频及映射。
6. 加入模型许可证、来源和校验值说明。
7. 在目标硬件上验证发音、口音、环境噪声、切换耗时和内存。

Vosk不是只支持Android，也可用于Linux、Windows、macOS及多种语言。Android只是本项目选择的运行端。

### 7.3 动态词条

当前词条编译在 `CommandCatalog.java` 中，修改后需要重新发布APK。

后续可以把每种语言的词条表改为签名或校验过的本地JSON配置。服务端或U盘下发新配置后：

1. 校验配置版本、哈希或数字签名。
2. 校验事件ID只使用App支持的白名单。
3. 停止并释放当前 `Recognizer`。
4. 用当前 `Model` 和新 `grammarJson` 创建新的 `Recognizer`。
5. 重新开始监听。

动态更新固定词条一般不需要重新下载声学模型，但新增说法必须能由当前模型词表和解码图表达。否则即使JSON里加入了文字，也可能无法正常识别。

### 7.4 动态模型

当前模型随APK打包，尚未实现网络下载。

后续可将模型下载到应用专属目录，然后使用：

```java
Model downloadedModel = new Model(localModelDirectory);
```

建议的安全流程：

1. 下载模型清单，包含语言、版本、大小、SHA-256、许可证和最低App版本。
2. 下载到临时目录。
3. 校验完整性和签名。
4. 解压到版本化目录，禁止路径穿越。
5. 检查必需模型文件齐全。
6. 停止并释放旧识别资源。
7. 加载新模型并运行冒烟识别。
8. 成功后切换“当前版本”指针；失败则回退旧版本。
9. 至少保留一个可用旧版本，避免断电或损坏导致设备失去语音功能。

不要覆盖正在使用的模型目录，也不要在校验完成前加载下载内容。

### 7.5 真正精简模型

把固定词条传给 `Recognizer` 只是运行时限定语法，不会自动删减以下模型文件：

- 声学模型。
- `HCLr.fst`。
- `Gr.fst`。
- i-vector相关文件。

真正缩小每种语言的模型需要Kaldi/Vosk训练与图构建流程，准备语音数据、发音词典、音素表、语言模型并重新构建解码图。它不是在Android项目中删除几个文件就能安全完成的工作；任意删除模型组成部分通常会导致加载失败或识别质量下降。

对当前设备，更稳妥的优先级是：使用官方small模型、运行时限定语法、按语言动态下载、只加载当前语言。只有模型体积或内存经过实测仍不满足要求时，再投入自定义模型训练。

## 8. 当前尚未实现

- 真实RS232通信和氧气机控制协议。
- 动态下发词条JSON。
- 动态下载、签名校验、版本管理和回退模型。
- 五种或更多语言的模型和UI。
- 30分钟、2小时、开机、关机事件提示音。
- 低功耗独立KWS或DSP唤醒。
- Android后台服务、开机自启和系统Kiosk模式。
- 正式医疗器械软件生命周期、风险管理和注册检验资料。
- 工业噪声环境下的量产级识别率统计。

## 9. 氧气机集成与安全边界

Android工业屏适合负责：

- UI显示和用户设置。
- 语音识别与语音提示。
- 操作日志。
- 向设备主控发送控制请求。
- 展示主控返回的状态和报警信息。

Android不应独立负责：

- 氧浓度、流量、压力、温度的安全监测。
- 压缩机、电磁阀的最终安全控制。
- 硬件看门狗、安全停机和故障保护。
- 唯一的医疗声光报警。

这些安全闭环应由独立MCU完成。语音识别到“开机”或某个产气时长，只能形成操作请求；MCU仍需检查传感器、当前状态、互锁条件和命令合法性，决定是否执行并返回结果。

生产级RS232建议至少具备：

- 真正的RS232电平和电气隔离。
- ESD、EFT/浪涌保护和屏蔽布线。
- 帧头、版本、长度、命令ID和序列号。
- CRC校验。
- ACK/NACK。
- 超时、有限次数重试。
- 重复命令去重。
- 通信中断后的明确定义安全状态。

## 10. 医疗合规提示

CPU、内存或某块工业屏不能单独证明整台氧气机满足医疗标准。合规对象是整机及其附件、软件过程和风险控制。

国内设计和注册时应由法规、安规及检测机构根据产品预期用途确认适用标准。此前讨论过的主要标准链包括：

- `GB 9706.1-2020`：医用电气设备基本安全和基本性能通用要求。
- `YY 9706.269-2021`：氧气浓缩器专用要求。
- `YY 9706.102-2021`：电磁兼容要求和试验。
- `YY 9706.108-2021`：报警系统要求。
- `YY 9706.111-2021`：家庭护理环境要求（产品预期用于家庭时）。
- `YY/T 9706.106-2021`：可用性工程。
- `YY/T 0664-2020`：医疗器械软件生命周期过程。
- `GB/T 42062-2022`：医疗器械风险管理。

国际出口需要按目标市场重新确认版本和法规路径。`ISO 80601-2-69:2020` 已被 `ISO 80601-2-69:2026` 替代，不能仅照搬旧版标准清单。

正式项目开始结构设计前，应让具备医疗电气经验的检测机构参与预评估，重点覆盖电击防护、漏电流、爬电距离与电气间隙、温升、机械安全、防火、EMC、报警、单一故障、家庭环境及软件风险。

## 11. 发布前最低检查清单

- [ ] 检查模型、提示音、词条和事件映射版本一致。
- [ ] 运行全部单元测试、APK构建和Lint。
- [ ] 在目标ABI和目标Android版本上安装启动。
- [ ] 分别测试所有语言的唤醒、每条命令、超时和未唤醒静默行为。
- [ ] 验证播放提示音时不会识别设备自己的声音。
- [ ] 验证快速语言切换不会被旧异步回调覆盖。
- [ ] 测量目标工业机上的PSS、CPU、温升和连续运行稳定性。
- [ ] 在压缩机、风扇和电磁阀实际噪声下统计识别率。
- [ ] 验证停止、切换、退出后麦克风、识别器和模型正确释放。
- [ ] 如已接MCU，验证CRC、ACK、超时、重试、去重和安全状态。
- [ ] 重新确认第三方许可证和目标市场适用标准。
