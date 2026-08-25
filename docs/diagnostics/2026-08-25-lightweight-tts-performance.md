# 轻量离线 TTS 真机验收

日期：2026-08-25

设备：Xiaomi 2201122C，SM8450，Android 15，arm64-v8a

引擎：sherpa-onnx 1.13.6

模型：中文 `vits-icefall-zh-aishell3`，英文 `vits-piper-en_US-ljspeech-medium`

## 结论

- 中文两种女声、英文女声、中英文模型切换和播放中停止均通过自动化真机测试。
- 标点分句后的后续 PCM 块 `queue_wait_ms` 为 0～1 ms，不再出现 Kokoro 方案中 3.3～5.9 秒的句间断口。
- 常用文本的首个 PCM 入队为 151～166 ms，开始 `AudioTrack` 播放为 264～316 ms。
- 每次播放结束时 `AudioTrack.getUnderrunCount()` 为 1；该计数在启动阶段出现，后续队列等待只有 0～1 ms，没有播放中再次断粮的日志证据。

## 实测数据

| 用例 | 模型加载 | 首 PCM 入队 | 开始播放 | 生成 RTF | 后续队列等待 | PSS 样本 |
|---|---:|---:|---:|---:|---:|---:|
| 中文北方女声，多标点 | 1,417 ms | 151 ms | 316 ms | 0.057 | 0 ms | 237,843 KB（播放中） |
| 中文南方女声 | 复用中文模型 | 160 ms | 268 ms | 0.058 | 1 ms | 200,308 KB（播放中） |
| 英文 LJSpeech，多标点 | 2,403 ms | 166 ms | 264 ms | 0.082 | 0 ms | 272,561 KB（播放中） |

中文模型就绪时 PSS 为 219,745 KB，英文模型就绪时为 260,005 KB。PSS 会受 GC、页面和测量时刻影响，这些数字是本次测试样本，不是固定上限。

## 与旧方案的边界

旧 Kokoro 记录来自 Huawei HMA-AL00，本次来自 Xiaomi 2201122C，所以不把 PSS 和首音数字当成同机严格性能对照。可直接确认的架构改进是：旧回调阻塞播放整句，新回调复制 PCM 后立即返回，由独立播放线程消费有界队列；真机日志也证明后续队列等待仅 0～1 ms。

Debug APK 由约 478 MB 降为约 237 MB，原因是不再打包 206 MB 的 Kokoro 多语言模型，而是打包约 31 MB 的中文资产和 79 MB 的英文资产。

## 验证方法

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_SERIAL=3da06fb
./gradlew clean :app:connectedDebugAndroidTest
```

仪器测试直接启动第四页，依次播放中文北方女声、中文南方女声、英文女声，再在长英文播放开始后执行停止。原始日志见 [2026-08-25-lightweight-tts-performance.log](2026-08-25-lightweight-tts-performance.log)。

## 许可选择

中文最终选择 AISHELL3（Apache-2.0）。调研期曾下载 Baker 模型，但其数据集说明限定非商业使用，不符合工业产品边界，因此未纳入工程。英文选择 Piper LJSpeech，声音仓库为 MIT，LJSpeech 数据集为 public domain。详细来源和哈希见项目根目录 `THIRD_PARTY_NOTICES.md`。
