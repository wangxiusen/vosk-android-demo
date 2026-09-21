# 离线人脸识别 Demo 设计

> 日期：2026-09-21  
> 分支：`feat/face-recognition-demo`  
> 依据：`docs/face-recognition-research.md`

## 目标与成功标准

在现有 Java/XML Android 示例首页新增“离线人脸识别 Demo”入口。用户输入姓名后，通过前置摄像头连续采集 5 个合格人脸样本完成录入；之后摄像头实时判断当前人脸是否属于已录入人员，并在页面展示姓名、相似度、推理耗时、状态和事件日志。

首期成功标准：

- 完全离线，不上传照片或特征。
- 支持多人录入、实时 1:N 识别、删除单人和清空全部资料。
- 只在单张、清晰、亮度和尺寸合格的人脸上录入或识别。
- 连续 3 帧同一候选人超过阈值后才确认，单帧结果不触发最终身份事件。
- 页面离开后台时停止摄像头分析，返回时安全恢复；所有 `ImageProxy` 都必须关闭。
- 构建、单元测试和真机页面启动通过；真实匹配准确率必须单独标注为现场观测，不能用同一张照片回灌冒充验收。

## 技术选择

- CameraX `1.4.2`：保留项目 `minSdk 21`，使用 `PreviewView`、`Preview` 和 `ImageAnalysis`。
- OpenCV Android AAR `4.13.0`：使用 Maven Central 官方 `org.opencv:opencv`。
- YuNet `face_detection_yunet_2023mar.onnx`：轻量人脸框和 5 点关键点检测。
- SFace INT8 `face_recognition_sface_2021dec_int8.onnx`：128 维特征提取。
- CPU 后端：首期不引入 RKNN/NPU 转换，先获得可移植基准。

CameraX 分析采用 RGBA 输出与 `STRATEGY_KEEP_ONLY_LATEST`，避免分析速度不足时堆积旧帧。OpenCV 通过 `OpenCVLoader.initLocal()` 初始化，模型从 APK assets 首次复制到应用私有目录后加载。

## 组件边界

### 领域与持久化

- `FaceProfile`：不可变人员资料，包含 ID、显示名、创建时间和样本数。
- `FaceMatcher`：L2 归一化、余弦相似度和 Top-1 匹配，不依赖 Android/OpenCV。
- `FaceRecognitionStabilizer`：要求同一候选人连续 3 帧通过阈值；未知、无人脸或候选变化立即重置。
- `FaceRepository`：保存 `profiles.json`、每人一份 `embedding.f32` 和 `portrait.jpg`；使用 canonical path 阻止目录穿越，元数据采用临时文件替换。

### 模型与帧分析

- `FaceModelInstaller`：按固定清单复制并校验两个 ONNX assets。
- `FaceFrame` / `FaceEngine`：用不可变 RGBA 帧和小接口隔离 CameraX、控制器与 OpenCV，控制器可以用假引擎做 JVM 测试。
- `OpenCvFaceEngine`：封装 YuNet/SFace，输入已校正方向的 RGBA `FaceFrame`，输出人脸数量、质量、128 维归一化特征和可保存的对齐头像。
- `FaceFrameQuality`：检查检测置信度、人脸相对尺寸、亮度和 Laplacian 清晰度；具体阈值作为 Demo 起点写入常量并展示在文档中。
- `RgbaFrameConverter`：从 CameraX RGBA plane 复制并按 `rotationDegrees` 旋转为紧凑的不可变 `FaceFrame`；不持有 `ImageProxy`。

### 编排与界面

- `FaceRecognitionController`：单线程串行化模型初始化、录入和识别；录入期间收集 5 个合格样本并平均归一化；识别期间每隔至少 150 ms 处理一帧。
- `FaceRecognitionActivity`：前置摄像头优先、无前置时回退后置；显示预览、姓名输入、人员选择、录入/删除/清空按钮、状态、结果、性能与日志。
- 摄像头权限被拒绝、设备无摄像头、OpenCV/模型加载失败、存储损坏均显示可读错误，不崩溃。

## 数据流

```text
CameraX RGBA frame
  -> rotation correction
  -> YuNet detect exactly one face
  -> quality gate
  -> SFace alignCrop + feature
  -> L2 normalize
  -> enrollment: collect 5 -> average -> persist
  -> recognition: compare all profiles -> threshold 0.363 -> 3-frame stabilizer
  -> UI result + event log
```

阈值 `0.363` 只来自 OpenCV 示例，是 Demo 初值，不是量产结论。目标工业 Pad 必须用真人、不同光线/距离、眼镜和制氧机现场背景重新标定误接受与误拒绝。

## 隐私、安全与明确不做

- 数据仅保存在 `files/face-recognition/`，卸载应用即删除；页面支持主动删除。
- 首期不实现云端同步、向量数据库、用户账号、RKNN、多人同时跟踪或生产级活体。
- 首期不把随机眨眼/转头描述为可靠活体；照片和视频回放攻击仍可能通过。
- 人脸结果不能作为氧气机启停或治疗参数修改的唯一授权条件。
- SFace 模型虽在 OpenCV Zoo 标注 Apache-2.0，量产前仍需法务确认训练数据和生物特征合规。

## 验证

- JVM 单元测试覆盖归一化、匹配、阈值边界、稳定器、仓库损坏/越界路径和模型清单。
- Android instrumentation 覆盖首页入口、Activity 启动、权限缺失提示与生命周期重建。
- 构建检查 APK 中两个模型各一份，OpenCV 原生库覆盖项目声明的 ABI。
- 真机记录摄像头枚举、模型加载耗时、单帧推理耗时、PSS、录入和真实同人/异人结果；没有真人协议时只报告观测，不报告准确率。
