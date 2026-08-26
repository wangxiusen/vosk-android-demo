# 离线 TTS 标点停顿真机验收

日期：2026-08-26

设备：Xiaomi 2201122C，Android 15，arm64-v8a

## 实现

第四个 Demo 新增短、标准、长三档标点停顿，分别将 sherpa-onnx `GenerationConfig.silenceScale` 设置为 `0.5`、`1.0`、`1.5`，默认使用标准档。该设置只缩放模型生成内容中的静音段，不修改语速。

## 同文对比

测试文字：`设备正在运行，请注意安全。发生异常时，请立即停机。`

| 档位 | silenceScale | 生成音频时长 | 最长低能量段 | 首 PCM 入队 | 后续队列等待 |
|---|---:|---:|---:|---:|---:|
| 短停顿 | 0.5 | 5,510 ms | 1,040 ms | 195 ms | 0 ms |
| 标准停顿 | 1.0 | 6,720 ms | 1,320 ms | 138 ms | 0 ms |
| 长停顿 | 1.5 | 8,100 ms | 1,600 ms | 126 ms | 0 ms |

三档使用相同模型、声音、语速和文字。音频总时长及最长低能量段随档位增大，说明标点停顿已经进入实际生成音频。三次生成均在约 0.13～0.20 秒提供首个 PCM，标点后的后续块等待为 0 ms，没有重新出现数秒级断口。

英文也使用同文对比：`Please check the device, and remain calm. Stop the machine, if an alarm occurs.`

| 档位 | silenceScale | 生成音频时长 | 最长低能量段 | 首 PCM 入队 | 后续队列等待 |
|---|---:|---:|---:|---:|---:|
| 短停顿 | 0.5 | 5,434 ms | 200 ms | 232 ms | 0 ms |
| 标准停顿 | 1.0 | 5,514 ms | 300 ms | 230 ms | 0 ms |
| 长停顿 | 1.5 | 5,773 ms | 680 ms | 213 ms | 0 ms |

英文的增幅小于中文，但音频总时长和最长低能量段同样随档位增加，且后续队列等待保持为 0 ms。

本轮还回归了中文第二种女声、语言切换、播放中停止和停顿控件默认值，共 2 个真机仪器测试通过。原始关键日志见 [2026-08-26-tts-punctuation-pause.log](2026-08-26-tts-punctuation-pause.log)。

## 验证命令

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_SERIAL=3da06fb
./gradlew testDebugUnitTest lintDebug assembleDebug connectedDebugAndroidTest
```
