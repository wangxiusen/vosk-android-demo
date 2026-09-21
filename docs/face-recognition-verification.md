# 离线人脸识别 Demo 验证记录

> 验证日期：2026-09-21  
> 分支：`feat/face-recognition-demo`  
> 应用包名：`com.huimei.voice`

## 已确认事实

- 执行了干净构建：`clean :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest`，结果为 `BUILD SUCCESSFUL`。
- JVM 单元测试共 116 个，0 失败，0 错误。范围包含向量数学、三帧稳定确认、存储损坏/路径防护、质量门、控制器生命周期和 RGBA 旋转。
- Debug APK 已生成，348,119,217 bytes，SHA-256 为 `7b843d4b849b84a39e4ec4ad26b07f8a2dcbb2f9e9510217ba6c45395fad9ab1`。
- 测试 APK 已生成，387,421 bytes，SHA-256 为 `d995aefceca9024e51f618d29ab099d15e38cb848f5ccd0a25fe006ff3ef1ef7`。
- APK 包含 `armeabi-v7a`、`arm64-v8a`、`x86_64` 三个 ABI，每个 ABI 均包含 OpenCV 原生库。
- YuNet 和 SFace ONNX 在 APK 中各出现一次，解包后的 SHA-256 分别为：
  - YuNet：`8f2383e4dd3cfbb4553ea8718107fc0423210dc964f9f4280604804ed2552fa4`
  - SFace INT8：`2b0e941e6f16cc048c20aee0c8e31f569118f65d702914540f7bfdc14048d78a`
- CameraX 分析使用 `STRATEGY_KEEP_ONLY_LATEST` 和单线程分析器，每条分析路径都在 `finally` 中关闭 `ImageProxy`。
- 人脸数据仅写入应用私有目录 `files/face-recognition/`；日志不输出照片字节或特征向量。

`lintDebug` 另行执行后未通过，唯一错误是人脸分支创建前已存在的 `voiceprint/VoiceprintAudioRecorder.java:34` `MissingPermission`；本次新增人脸 Java 代码没有 Lint 错误。人脸页面有与工程其他页面一致的 `ButtonStyle` 建议性告警，不影响构建。为避免在本分支改动已完成的声纹模块，该旧错误保留为独立待办。

APK 位置：

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
```

## 本次无法确认的真机项

2026-09-21 16:13 执行 `adb devices -l` 时只出现 `emulator-5554 offline`；执行无损重连后设备列表为空。因此没有安装 APK，也没有执行真机 instrumentation。下列项目均标记为“未验证”，不从构建成功推断真机通过：

| 真机项目 | 状态 |
|---|---|
| 工业 Pad 能枚举前置或后置摄像头 | 未验证 |
| CameraX 预览方向、镜像和画面稳定性 | 未验证 |
| YuNet/SFace 模型在目标 CPU/ABI 上成功加载 | 未验证 |
| 真人 5 帧录入与持久化 | 未验证 |
| 同人、不同人、照片回放下的识别表现 | 未验证 |
| 模型冷加载时间、单帧推理时间、PSS 和长时间温升 | 未验证 |

## 下次真机验收命令

先用设备序列号锁定目标，避免误装到其他平板：

```bash
adb devices -l
adb -s <serial> install -r -t --no-streaming app/build/outputs/apk/debug/app-debug.apk
adb -s <serial> install -r -t --no-streaming app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s <serial> shell am instrument -w com.huimei.voice.test/androidx.test.runner.AndroidJUnitRunner
adb -s <serial> shell am start -n com.huimei.voice/.FaceRecognitionActivity
adb -s <serial> shell dumpsys activity activities
adb -s <serial> shell pidof com.huimei.voice
adb -s <serial> shell dumpsys meminfo com.huimei.voice
```

安装与测试只有在终端分别出现 `Success` 和 instrumentation 终端 `OK` 时才记为通过。页面启动后还需人工完成至少一位人员录入，再分别用本人与另一人做实时比对，记录页面显示的模型加载、推理和 PSS。

## 安全与量产边界

- 阈值 `0.363` 未用目标平板、人群和现场光线做 ROC 标定。
- 当前没有活体检测，不能阻止手机照片、视频或打印照片回放。
- 人脸识别不应成为制氧机启停、治疗参数或其他危险动作的唯一授权因素。
- SFace 模型仓库许可信息不替代训练数据来源、生物特征告知同意、保留和删除规则的量产法务审查。
