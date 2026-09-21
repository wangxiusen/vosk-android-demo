# Sherpa-ONNX 与 Vosk 双声纹 Demo 设计

## 目标

在现有 Java/XML Android 示例中新增两个完全自主实现的离线声纹 Demo：

1. `Sherpa-ONNX 声纹识别`
2. `Vosk 声纹识别`

两个 Demo 使用相同的注册录音、测试录音、人员数据、质量门槛和指标展示，只替换底层声纹引擎，以便在工业 Android Pad 上公平比较模型体积、加载时间、识别耗时、内存占用和实际区分效果。

现有语音命令识别、Lottie 动画、固定音频播放和任意文字离线 TTS 保持不变。本阶段不复制 sherpa-onnx 或 Vosk 官方 Demo 的页面或业务代码，只调用其公开 SDK API。

## 范围

### 包含

- 每个人录制三段注册语音，每段目标时长五秒。
- 共用的 16 kHz、单声道、PCM 16-bit 录音链路。
- 注册人员列表、删除单人、清空全部数据。
- 1:N 识别：从已注册人员中选出最相似者，否则返回“未知人员”。
- 1:1 验证：选择一个已注册人员，判断测试语音是否属于该人员。
- 显示相似度、通过阈值、有效语音时长、模型加载耗时、推理耗时和进程 PSS。
- 保存 Demo 所需的注册样本和两套引擎生成的声纹向量。
- 页面退出时停止录音并释放模型，避免和 Vosk 命令识别、TTS 抢占麦克风与内存。
- JVM 单元测试、Android UI/生命周期测试、Debug 构建和真机验证。

### 不包含

- 不删除或重构现有第四个 TTS 页面。
- 不做云端上传、服务端声纹库或账号体系。
- 不做 RKNN/NPU 转换，首版仅使用 CPU。
- 不把声纹结果用于氧气机关键操作授权。
- 不承诺防录音回放、合成语音或深度伪造；页面明确标记为算法评估 Demo。
- 不训练新模型。

## 对比原则

两套引擎必须处理同一批 PCM 样本，不能要求用户分别录两套数据。固定测试语句为：

> 潓美医疗声纹测试，一二三四五六七八九十，现在开始识别我的声音。

模型本身采用文本无关或弱文本相关的声纹特征，但固定语句能减少比较时的内容变量，并保证 Vosk 获得足够长的语音帧。页面允许重复录制不合格样本。

阈值按引擎分别配置，初始值只用于 Demo 启动，不作为最终结论。真机测试将收集同人和异人分数，再确定各自阈值。

## 页面与交互

主列表新增两个入口。两个页面布局一致，只显示不同的引擎名称、模型名称和模型状态。

页面区域：

1. **引擎信息**：引擎、模型、初始化状态、加载时间、当前 PSS。
2. **注册**：人员名称、固定测试语句、录音进度、三段样本状态、保存按钮。
3. **人员管理**：人员列表、样本数量、删除按钮。
4. **识别与验证**：录制测试语音、1:N 识别结果、选定人员的 1:1 验证结果。
5. **日志**：时间、操作、音频质量、分数、阈值、耗时和错误信息；支持清空。

录音状态为 `空闲 → 录音中 → 质量检查 → 提取声纹 → 完成/失败`。录音中禁用其他录音与识别按钮。Activity 进入后台或销毁时立即停止录音和后台任务。

## 架构

### 共享业务层

`VoiceprintEngine` 定义两套 SDK 必须实现的最小契约：

- 初始化和释放；
- 从一段 PCM 提取归一化声纹向量；
- 返回引擎名称、模型信息和向量维度。

注册、多段向量平均、余弦相似度、阈值判断和 1:N 排序统一放在共享业务层，确保算法外的比较条件一致。引擎不负责 UI 和人员持久化。

核心组件：

- `VoiceprintAudioRecorder`：录制 16 kHz 单声道 PCM。
- `VoiceprintAudioQuality`：计算时长、RMS、峰值和削波比例，拒绝静音或严重削波。
- `VoiceprintEngine`：统一声纹提取接口。
- `SherpaVoiceprintEngine`：sherpa-onnx 实现。
- `VoskVoiceprintEngine`：Vosk 实现。
- `VoiceprintMatcher`：向量归一化、平均、余弦相似度、排序与阈值判断。
- `VoiceprintRepository`：人员、共享注册 WAV 和各引擎 embedding 的应用私有存储。
- `VoiceprintController`：状态机、后台任务和 Activity 回调。
- `BaseVoiceprintActivity`：共用页面行为。
- `SherpaVoiceprintActivity`、`VoskVoiceprintActivity`：仅提供引擎类型和页面标题。

### Sherpa-ONNX 引擎

- 使用当前工程已有的 `sherpa-onnx-1.13.6.aar`。
- 使用 `3dspeaker_speech_campplus_sv_zh-cn_16k-common.onnx`。
- 使用 `SpeakerEmbeddingExtractor` 创建 stream、输入 float PCM 并计算 embedding。
- 不使用官方 Demo 的 Compose 页面或其业务封装。
- 首版 `numThreads=2`、CPU provider；真机结果不理想时再比较 1/2/4 线程。

### Vosk 引擎

- 使用当前工程已有的 `vosk-android:0.3.75` 和中文小模型。
- 增加 Apache 2.0 的 `vosk-model-spk-0.4`。
- 使用 `SpeakerModel` 与 `Recognizer(Model, 16000, SpeakerModel)`。
- 将完整 PCM 输入 recognizer，从 final result 的 `spk` 和 `spk_frames` 字段解析 x-vector。
- 若没有 `spk`、帧数不足或 JSON 非法，明确返回失败，不生成伪向量。
- 不启用 max alternatives，避免与 speaker vector 输出冲突。

## 数据存储

数据保存在应用私有目录，不申请存储权限：

```text
files/voiceprint/
  profiles.json
  samples/<profile-id>/enroll-1.wav
  samples/<profile-id>/enroll-2.wav
  samples/<profile-id>/enroll-3.wav
  embeddings/sherpa/<profile-id>.f32
  embeddings/vosk/<profile-id>.f32
```

`profile-id` 使用随机 UUID，人员显示名称不进入文件名。`profiles.json` 只保存 ID、显示名称、创建时间和样本状态。向量以固定字节序二进制保存，并记录维度和引擎版本。模型或维度变化时丢弃对应旧向量并从共享 WAV 重新计算。

原始 WAV 仅为本地对比 Demo 保留，页面提供“清空全部声纹数据”。日志不打印完整 embedding，也不导出人员录音。

## 模型交付

两个声纹模型随 Demo APK 放入 assets：

- Sherpa CAM++：约 27 MiB。
- Vosk speaker model：约 13 MiB。

Sherpa Java API 通过 `AssetManager` 直接加载 ONNX，不额外复制模型。Vosk `SpeakerModel` 只接受文件系统目录，因此首次使用时把 speaker model 和现有 `model-cn` 复制到应用私有模型目录；中文 ASR 模型仍复用现有 `models` 模块资产，不重复打包。工业 Pad 版本后续可增加 `arm64-v8a` 专用构建以减少多 ABI 原生库体积，但首版不改变现有 ABI 配置。

每个模型必须在 `THIRD_PARTY_NOTICES.md` 中记录来源、版本、下载地址、文件哈希和许可证。代码仓库许可证不能替代具体模型资产的许可证核对。

## 错误处理

- 麦克风权限被拒绝：展示原因并禁用录音按钮。
- AudioRecord 初始化失败：展示设备录音不可用，不重试死循环。
- 音频过短、过轻或削波：不写入注册样本，要求重录。
- 模型缺失或复制失败：停止初始化并展示具体文件名。
- 声纹向量为空、NaN、维度错误：拒绝保存和匹配。
- Vosk 没有返回 `spk`：提示有效语音不足或模型输出失败。
- 没有已注册人员：禁止开始识别并提示先注册。
- 页面退出：取消任务、停止录音、关闭 recognizer/model/extractor。

## 测试与验收

### 自动测试

- 音频质量：静音、正常信号、削波、时长边界。
- 向量运算：归一化、平均、余弦相似度、空向量和维度不一致。
- Vosk JSON：正常 `spk`、缺失字段、非法数字、帧数不足。
- Repository：新增、覆盖、删除、清空、模型版本失效重算。
- Controller：录音状态、并发按钮、生命周期取消和错误恢复。
- 主列表：两个新入口指向正确页面。

### 构建与设备验收

1. JVM 测试全部通过。
2. Android instrumentation 测试通过。
3. Debug APK 构建成功。
4. 在带有效麦克风的 Android 真机上完成注册、验证、识别和清空。
5. RK3576 重新连接且麦克风或 USB 麦克风可用后安装测试。
6. 每套引擎记录冷启动模型加载时间、五秒语音提取时间和前后 PSS。
7. 至少三个人，每人三段注册语音；每人十次本人测试，并进行至少二十次异人测试。
8. 输出对比表，但不以少量 Demo 样本宣称生产级准确率。

## 已知硬件边界

此前 RK3576 的 ES7202 和 ES8388 两路采集只有数字底噪。实现必须能在普通手机或外接 USB 麦克风上完成闭环，但只有在 RK3576 获得有效 PCM 后，才能确认目标 Pad 上的真实声纹表现。模型成功加载不等于目标设备录音链路通过。

## 安全边界

声纹属于可被录音回放和合成攻击的生物特征。本 Demo 只比较离线说话人 embedding，不实现活体检测，也不能作为开机、关机或治疗参数调整的唯一授权条件。生产化需单独设计挑战口令、反欺骗模型、数据保护、阈值标定和失败回退。
