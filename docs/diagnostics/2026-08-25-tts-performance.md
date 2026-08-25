# 离线 TTS 首音延迟和标点断口诊断

日期：2026-08-25

设备：Huawei HMA-AL00，Kirin 980，Android 10 / API 29

引擎：sherpa-onnx 1.13.6 + `kokoro-int8-multi-lang-v1_1`

## 结论

用户感知的两个问题均已复现：

1. 模型已就绪后点击“开始口播”，首个 PCM 仍需等待 3.6～11.6 秒，取决于第一句的长度。
2. 标点将文本分成多句后，句子之间出现 3.3～5.9 秒无音。

这不是 UI 线程卡顿，也不是标点生成了几秒静音。根因是 Kokoro 按句生成，而当前 Java 回调在同一生成线程中使用 `AudioTrack.WRITE_BLOCKING` 把整句播完才返回。回调返回后模型才开始生成下一句，因此播放器断料。

## 实测对比

| 用例 | 字符 / 标点 | 首 PCM | 句间等待 | 音频内最长低能量段 | AudioTrack 欠载 |
|---|---:|---:|---:|---:|---:|
| 默认中文 | 23 / 2 | 3.607 s | 5.928 s | 0.200 s | 2 |
| 英文无标点 | 131 / 0 | 11.617 s | 无中间回调断口 | 0.200 s | 1 |
| 相同英文加标点 | 135 / 4 | 6.939 s | 3.343 s、3.566 s | 0.260 s | 3 |

有标点用例的真实音频长度是 7.255 秒，但从点击到播放完成用了 21.214 秒。每次几秒级断口后，`AudioTrack.getUnderrunCount()` 都增加 1，证明播放缓冲已经耗尽。

原始时间线见 [2026-08-25-tts-performance.log](2026-08-25-tts-performance.log)。

## 代码和上游证据

- 本项目 `OfflineTtsPlayer.StreamingCallback.invoke()` 直接调用阻塞式 `streamSamples()`，回调持续时间几乎等于该句的播放时长。
- sherpa-onnx 的 Kokoro 实现强制 `batch_size = 1`，并明确忽略其他 `max_num_sentences` 值；每生成一句后同步调用回调。
- sherpa-onnx 官方流式播放示例会复制回调样本到队列，立即返回，再由独立播放线程消费队列。

官方源码：

- https://github.com/k2-fsa/sherpa-onnx/blob/master/sherpa-onnx/csrc/offline-tts-kokoro-impl.h
- https://github.com/k2-fsa/sherpa-onnx/blob/master/sherpa-onnx/csrc/sherpa-onnx-offline-tts-play.cc

## 后续修复方向

本次只诊断和记录，未改播放架构。建议后续按以下顺序验证：

1. 按官方示例将“模型生成”和“AudioTrack 播放”改为生产者/消费者队列，回调只复制 PCM 并立即返回。
2. 设置可控预缓冲，再启动 `AudioTrack`。当前手机上短句生成的实时率仍大于 1，仅解耦回调可能仍在后续句子处断料。
3. 用同一组基准文本对比首音延迟、欠载数和峰值 PSS，再决定是否更换更快的多语言模型。

不建议通过删除标点规避问题：无标点虽然没有中间断口，但首音延迟反而增加到 11.6 秒，且韵律会变差。
