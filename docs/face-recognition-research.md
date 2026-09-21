# 工业 Android Pad 离线人脸识别方案调研

> 更新日期：2026-09-21  
> 目标：先拍照录入人员，后续通过摄像头实时判断是否与已录入人员匹配。

> 实施状态：首期 Demo 已在 `feat/face-recognition-demo` 分支完成编码和 APK 打包；目标工业 Pad 真机验收尚未完成。

## 推荐结论

首期建议使用 **CameraX + OpenCV Java AAR + YuNet + SFace INT8**。该组合可全离线、支持 Android Java，实现和后续维护都比直接接入一套庞大人脸 SDK 简单。

- Android 官方对大多数应用推荐 CameraX；它从 Android 5.0 开始提供统一的高层 API，只有需要特殊 HAL/低层控制时才应转 Camera2：[Android CameraX 选型说明](https://developer.android.com/media/camera/choose-camera-library)。
- OpenCV Java API 直接提供 `FaceDetectorYN` 和 `FaceRecognizerSF`：[OpenCV Java API](https://docs.opencv.org/4.12.0/javadoc/org/opencv/objdetect/package-summary.html)。
- 当前 YuNet 2023mar ONNX 约 227 KB；SFace INT8 约 9.44 MB。OpenCV 的完整教程展示了人脸检测、5 点对齐、特征提取和余弦匹配的完整链路：[OpenCV DNN 人脸教程](https://docs.opencv.org/4.12.0/d0/dd4/tutorial_dnn_face.html)。

## 数据流

```text
CameraX Preview + ImageAnalysis
  -> YuNet 人脸框 + 5 个关键点
  -> 姿态/清晰度/亮度/人脸尺寸质量门
  -> SFace alignCrop + 128 维特征
  -> L2 归一化
  -> 与已录入模板做余弦相似度
  -> 阈值 + Top1/Top2 差值 + 多帧稳定后输出结果
```

### 录入

1. 要求画面中只有一张人脸。
2. 连续采集约 5 帧合格人脸，覆盖轻微左右角度，不使用单帧作为唯一模板。
3. 对 5 个合格特征做平均归一化，保存一个模板向量。首期 Demo 未增加额外的异常值剔除。
4. 按需保留一张高质量对齐照片作为界面头像，但实际匹配使用特征向量，不做像素直接对比。
5. Demo 使用应用私有目录；量产版还应用 Android Keystore 管理的密钥加密照片、向量和人员元数据。

### 实时识别

- Preview 可保持流畅，分析帧丢弃旧帧，避免堆积延迟。
- 人脸检测可从 5–10 fps 起步，特征提取可降为 2–5 fps；非必要时不要每帧都跑 SFace。
- 连续 3–5 次对同一人超过阈值才确认，丢失人脸或候选人切换时重置状态。
- 1:N 人数很少时直接线性遍历即可；不要为几十人过早引入向量数据库。

OpenCV 教程中 SFace 在 LFW 上的余弦示例阈值为 0.363，但这只能作为 Demo 起点。实际阈值必须用目标平板、摄像头、光线、人群和距离的 ROC 数据确定。

## 活体与医疗设备边界

只有 YuNet + SFace 无法防止手机照片、录像回放或打印照片。首期可加随机指令（眼睛、左右转头）防低成本静态照片，但这不等于完整活体检测。

后续可评估 [Silent-Face-Anti-Spoofing](https://github.com/minivision-ai/Silent-Face-Anti-Spoofing)：仓库为 Apache-2.0，并提供移动端模型与 Android 示例。但其文档也明确提醒 RGB 静默活体会受摄像头和场景影响，必须在实机上重新标定。

对制氧机或其他医疗设备，人脸识别可用于个性化、记录归属或辅助鉴权，不应作为启停、治疗参数等危险操作的唯一授权因素。

## 许可证与选型风险

- [YuNet 目录](https://github.com/opencv/opencv_zoo/tree/main/models/face_detection_yunet) 标注 MIT License。
- [SFace 目录](https://github.com/opencv/opencv_zoo/tree/main/models/face_recognition_sface) 标注所有文件 Apache License 2.0。医疗/商用量产前仍应由法务确认模型训练数据来源、告知同意和生物特征数据合规。
- 不建议首期直接使用 InsightFace 官方预训练权重：其代码是 MIT，但官方模型文档将所提供的预训练模型限定为非商业研究：[InsightFace 模型许可说明](https://github.com/deepinsight/insightface/blob/master/python-package/docs/model_zoo.md)。

## 实施阶段

1. **硬件门禁**：在 RK3576 Pad 上确认 `CameraManager` 能枚举前置或 USB 摄像头，并连续获得正常 YUV 帧。如 camera count 为 0，必须先解决硬件、UVC 或 HAL，算法代码无法补救。
2. **Demo（已编码）**：CameraX 预览、YuNet 检测、SFace 五帧录入与 1:N、私有目录持久化、日志和性能数据。当前没有单独的 1:1 操作入口。
3. **现场标定**：至少覆盖不同人、眼镜、角度、距离、光线和设备位置，分析误接受与误拒绝。
4. **安全增强**：加入活体、数据加密、删除/重录入审计、权限分层和失败降级策略。
5. **优化**：只在 CPU 达不到目标帧率时再考虑 RKNN/NPU 转换；不在首期增加这层复杂度。

当前实现的构建、模型哈希、设备连接与未验证项见 [face-recognition-verification.md](face-recognition-verification.md)。
