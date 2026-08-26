# TTS PCM 响度一致化方案（待实现）

日期：2026-08-26

状态：已完成现状诊断和方案记录，尚未修改播放器代码。

## 1. 问题与结论

第四个 Demo 的 TTS 声音整体低于项目内自录 MP3，10 个 AISHELL3 中文女声之间也存在明显音量差异。

这不是系统媒体音量不同造成的：

- `OfflineTtsPlayer` 使用 `AudioTrack` 播放 float PCM，当前直接写入模型输出，没有数字增益或响度归一化。
- `VoicePromptPlayer` 使用 `MediaPlayer` 播放 MP3，TTS 和 MP3 都使用媒体播放路径。
- 两个播放器的应用内增益均处于默认 unity gain（`1.0`）。提高系统媒体音量会同时放大两者，不能消除源音频的响度差异。
- sherpa-onnx `GenerationConfig` 提供 speaker、speed、silence scale 等参数，没有 volume/loudness 参数。

因此应在 `AudioTrack` 写入前处理模型生成的 PCM，而不是修改模型或单纯调用播放器音量 API。

## 2. 已测证据

测试文字：`潓美医疗，设备运行正常。`

环境：sherpa-onnx 1.13.6，使用工程内同一份 `vits-icefall-zh-aishell3` 模型、词表和规则 FST。

| speaker ID | 峰值 | 全段 RMS | 有效声音 RMS |
|---:|---:|---:|---:|
| 4 | -22.4 dBFS | -38.0 dBFS | -35.2 dBFS |
| 122 | -16.9 dBFS | -31.2 dBFS | -29.5 dBFS |
| 0 | -11.5 dBFS | -27.8 dBFS | -26.4 dBFS |
| 1 | -15.1 dBFS | -29.2 dBFS | -27.7 dBFS |
| 16 | -13.7 dBFS | -27.4 dBFS | -26.2 dBFS |
| 19 | -24.9 dBFS | -37.7 dBFS | -34.7 dBFS |
| 57 | -19.3 dBFS | -29.6 dBFS | -28.4 dBFS |
| 104 | -12.8 dBFS | -27.4 dBFS | -26.2 dBFS |
| 113 | -15.9 dBFS | -31.2 dBFS | -29.7 dBFS |
| 147 | -13.4 dBFS | -28.9 dBFS | -27.1 dBFS |

有效声音 RMS 忽略绝对值低于约 `-45 dBFS` 的样本，避免标点静音影响响度判断。10 个女声的有效 RMS 相差约 9 dB，峰值相差约 13 dB。

项目内 MP3 使用 FFmpeg `volumedetect` 的结果：

| 音频 | mean volume | max volume |
|---|---:|---:|
| 中文 1 小时产气 | -9.6 dB | 0.0 dB |
| 中文 8 小时产气 | -11.2 dB | -0.2 dB |
| 中文唤醒回应 | -12.7 dB | -0.1 dB |
| 英文 1 小时产气 | -17.0 dB | -0.4 dB |
| 英文 8 小时产气 | -16.9 dB | -0.2 dB |
| 英文唤醒回应 | -18.0 dB | -0.4 dB |

MP3 峰值已经接近满刻度，不能通过继续放大 MP3 解决。TTS PCM 则有较大峰值余量，可以在限幅保护下安全增益。

## 3. 推荐实现

### 3.1 用户选项

第四个 Demo 增加“口播音量”选择，不改变系统媒体音量：

| 档位 | 目标有效 RMS | 用途 |
|---|---:|---|
| 标准 | -20 dBFS | 安静环境或小型扬声器 |
| 增强（默认） | -18 dBFS | 工业设备默认口播 |
| 强音量 | -16 dBFS | 噪声较高的现场 |

目标值应做成稳定的内部枚举；UI 文案和算法参数分离。

### 3.2 PCM 处理算法

对 sherpa 回调产生的每个 PCM 块执行：

1. 统计原始峰值和有效 RMS；低于 `-45 dBFS` 的样本不参加 RMS 计算。
2. 根据目标 RMS 计算线性增益：`targetRms / activeRms`。
3. 最大增益限制为 `+18 dB`，避免极小信号或近静音被过度放大。
4. 根据原始峰值再次限制增益，确保处理后峰值不超过 `-1 dBFS`。
5. 使用最终增益生成待播放 PCM，不使用硬裁剪。
6. 在分块边界保留平滑或短增益渐变，避免突然切换增益产生点击声。

如果一块没有有效声音，则保持静音，不计算无限增益，也不放大底噪。

### 3.3 接入位置

建议新增一个纯 Java `PcmLoudnessNormalizer`，只负责统计和增益计算；`OfflineTtsPlayer` 仍负责模型、队列和 `AudioTrack` 生命周期。

处理放在播放线程拿到完整 PCM 块之后、调用 `AudioTrack.write()` 之前：

```text
sherpa callback
  -> 原始 PCM 有界队列
  -> 播放线程计算块级增益
  -> 每帧应用增益与峰值保护
  -> AudioTrack.write()
```

嘴型计算继续使用原始 PCM，避免响度增益把口型长期推到“大口”；实际写入喇叭的 PCM 使用处理后的数值。现有 TTS 生成线程、播放线程和队列结构保持不变。

### 3.4 诊断日志

每个 PCM 块记录：

- 输入有效 RMS / 峰值。
- 目标档位和目标 RMS。
- 实际应用的 gain dB。
- 峰值保护是否介入。
- 预计输出峰值。

日志只记录统计值，不保存用户输入的完整 PCM。

## 4. 测试和验收标准

### 4.1 JVM 单元测试

- 小音量正弦波能提升到目标范围。
- 大音量输入能衰减或受峰值保护，不超过 `-1 dBFS`。
- 全静音输入仍为静音，不产生 NaN/Infinity。
- 输入数组不被意外修改。
- 三个响度档位参数和默认档位正确。

### 4.2 小米真机自动化

- 10 个中文女声和英文女声均完成实际合成、归一化和播放。
- 相同文字处理后的有效 RMS 离散范围目标不超过 3 dB。
- 输出 PCM 峰值不超过 `-1 dBFS`，无削波计数。
- 标点后的后续队列等待继续保持约 0～1 ms。
- 首音延迟相对当前版本增加不超过 30 ms。
- 停止、语言切换、三档语速、三档停顿和嘴型动画无回归。

### 4.3 人工听感

- 在同一系统媒体音量下，对比 TTS 和自录 MP3。
- 确认不同女声音量接近，不出现忽大忽小、削波、爆音或明显底噪抬升。
- 最终默认档位应在目标工业设备的外置喇叭和工作噪声环境中确定。

## 5. 边界

- 第一版不修改或重新编码用户提供的 MP3。
- 不修改 sherpa 模型，不重新训练，也不增加新的模型文件。
- 不使用系统级 `AudioManager.setStreamVolume()` 强制改变用户音量。
- 不做完整 EBU R128/LUFS 双遍扫描，保留流式首音优势；如量产听感仍不稳定，再评估更复杂的压缩器或 LUFS 方案。
- 本方案不会改变模型常驻内存，仅增加少量逐样本乘法和统计，最终性能仍以真机日志为准。

## 6. 参考

- [sherpa-onnx GenerationConfig](https://github.com/k2-fsa/sherpa-onnx/blob/master/sherpa-onnx/csrc/offline-tts.h)
- [Android AudioTrack](https://developer.android.com/reference/android/media/AudioTrack)
- [Android MediaPlayer](https://developer.android.com/reference/android/media/MediaPlayer)
