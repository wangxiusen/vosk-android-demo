# 离线声纹 Demo 对比与验证记录

> 更新日期：2026-09-21  
> 分支：`feat/dual-voiceprint-demo`  
> 结论属性：Demo 对比，不是医疗设备生产级身份认证

## 1. 实现范围

应用首页新增两个相互独立的入口：

- `Sherpa 离线声纹 Demo`：使用 sherpa-onnx CAM++ 提取声纹向量。
- `Vosk 离线声纹 Demo`：使用 Vosk `SpeakerModel` x-vector 提取声纹向量。

两个页面不复制官方 Demo 代码，只调用 SDK 的公开 API。它们共用同一套自行实现的 Java/XML 录音、WAV、音频质量、持久化、余弦相似度、页面和生命周期逻辑，因此对比时的录音条件一致。

流程：

1. 输入人员姓名，每次清晰朗读页面的固定短语。
2. 录制 3 条、5 秒、16 kHz 单声道 PCM16 样本。
3. 先拒绝太短、过小或削波音频，再提取并平均归一化声纹向量。
4. 可对所选人员做 1:1 验证，或对已录入人员做 1:N 识别。
5. 录音和声纹数据都不会上传；声纹资料仅保存在 `files/voiceprint/` 应用私有目录。

## 2. 算法与包体对比

| 项目 | Sherpa CAM++ | Vosk x-vector |
|---|---:|---:|
| 模型文件大小 | 28,281,138 bytes | 14,996,015 bytes（4 个文件合计） |
| 向量维度 | 192 | 128 |
| Demo 初始余弦阈值 | 0.60 | 0.50 |
| 真机冷加载 | 568 ms | 563 ms |
| 页面即时 PSS | 152,379 KB | 196,215 KB |
| `dumpsys meminfo` 稳定时 PSS | 134,030 KB | 135,231 KB |
| 已观察的单次推理 | 160 ms、166 ms | 未获得可记录样本 |
| 已观察的通过分数 | 0.861、0.714 | 未获得可记录样本 |

注意：

- 真机数据来自 Huawei HMA-AL00，Android 10 / API 29，arm64-v8a。PSS 受进程热状态、垃圾回收和共享页分摊影响，应视为本次采样，不是固定上限。
- 两个引擎的分数分布不同，不能直接比较分数高低；阈值必须分别标定。
- 上表阈值只是为了让 Demo 可运行的初始值，未经目标人群、噪声、麦克风和距离校准。
- Sherpa 推理与分数是用户操作页面时的客观 UI 记录，但本次没有控制说话人、真假匹配和环境，因此只可用于耗时观察，不能当作准确率或阈值证明。

## 3. 2026-09-21 已完成验证

| 项目 | 结果 |
|---|---|
| JVM 单元测试 | 83 个，0 失败，0 跳过 |
| 真机界面/生命周期测试 | 3 个，全部通过 |
| Sherpa 模型初始化 | 真机显示“状态：就绪”，192 维 |
| Vosk 模型初始化 | 真机显示“状态：就绪”，128 维 |
| 后台/恢复/重建 | 自动测试通过，无过期回调崩溃 |
| 崩溃和原生库异常 | 当前进程日志未发现 FATAL 或模型初始化错误 |
| APK | `app/build/outputs/apk/debug/app-debug.apk`，290,562,698 bytes（约 277 MiB） |
| 包内资源 | 5 个声纹模型条目各一份，无 zip/tar/临时下载文件；原 Vosk 中英文、TTS、Lottie 和 6 个 MP3 仍在 |

未完成、不冒充通过的项目：

- 本次无受控参与者协议，因此未将现场 UI 结果写成三次录入、1:1 或 1:N 准确性验收。使用相同录音注入会产生虚假乐观结果，所以没有这样做。
- 三人真假接受率对比尚未进行。
- RK3576 工业 Pad 必须先确认 Android 能枚举麦克风且采集到非静音 PCM，目前未将算法测试标记为通过。

## 4. 复现命令

```bash
./gradlew -Dorg.gradle.java.home="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  clean :app:testDebugUnitTest :app:assembleDebug

adb -s <serial> install -r -t --no-streaming \
  app/build/outputs/apk/debug/app-debug.apk

adb -s <serial> shell am start -n com.huimei.voice/.MainActivity
adb -s <serial> shell dumpsys meminfo com.huimei.voice
```

华为系统可能弹出两次未知来源风险确认。安装命令只有最终返回 `Success` 才算安装完成。

## 5. 量产前必须补做

1. 在目标工业 Pad 上验证麦克风、增益、回声、风扇/制氧机噪声和收音距离。
2. 至少 3 人，每人 3 条录入、10 次本人测试，并完成不少于 20 次他人测试。
3. 分别对 Sherpa 和 Vosk 扫描阈值，报告误接受与误拒绝，不共用一个阈值。
4. 补充录音回放攻击对抗，或将声纹只用作非安全性的个性化功能。
5. 如用于操作医疗设备，不得以声纹单因素解锁危险操作，必须保留硬件联锁、权限和二次确认。
