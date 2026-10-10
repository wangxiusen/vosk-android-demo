# FunctionGemma 离线语音指令 Demo 设计

> 日期：2026-10-10  
> 分支：`feat/functiongemma-command-demo`  
> 运行时：LiteRT-LM Android `0.18.0`

## 目标与成功标准

在现有 Java/XML Android 示例首页新增“FunctionGemma 语音指令 Demo”入口，把自然语言语音稳定转换成现有 `CommandEvent`，但只展示事件和日志，不连接真实氧气机。

首期成功标准：

- 中文和英文可切换；继续使用现有 Vosk 模型完成离线语音转文字。
- 固定词条优先走现有 `CommandCatalog`，保持快速、确定且不依赖大模型。
- 固定词条未命中时，才把转写文本交给 FunctionGemma，支持“帮我开一下机器”“开始产气半个小时”等自然说法。
- FunctionGemma 只能从白名单中选择 `POWER_ON`、`POWER_OFF`、`GAS_30_MINUTES`、`GAS_1_HOUR`、`GAS_2_HOURS`、`GAS_8_HOURS`；无调用、多调用、未知调用或非法参数一律拒绝。
- 页面展示原始转写、路由方式、模型原始调用、最终事件、初始化耗时和单次推理耗时。
- 模型不打进 APK；可从用户随后提供的云存储直链下载到应用私有目录，并校验固定 SHA-256。
- JVM 测试、Debug 构建和真机入口启动通过；真机语义正确率、延迟和内存必须单独实测，不能用官方基准替代。

## 已确认假设

- FunctionGemma 只处理文本，不替代 Vosk 语音识别。
- 首期只做中文和英文，因为项目已有这两种 Vosk 模型；扩展到五种以上语言需要为每种语言另备 ASR 模型和实测语料，但指令白名单无需改变。
- 目标新 Pad 是 64 位 ARM。LiteRT-LM `0.18.0` AAR 只包含 `arm64-v8a` 与 `x86_64` 原生库；32 位 `armeabi-v7a` 设备进入该页面时显示“不支持”，项目其他 Demo 仍可保留 32 位支持。
- 首版使用 CPU 后端。现成 270M `.litertlm` 是 CPU 模型；不能把 LiteRT-LM 的通用 NPU 参数等同于 RK3576 的 Rockchip NPU 支持。
- Google 明确建议 FunctionGemma 针对业务函数进行微调。首期先验证端侧链路和建立评测集，不把零样本或 Mobile Actions 微调模型的结果称为量产准确率。

## 技术选择

### 选择 LiteRT-LM，而不是 RKLLM 或 llama.cpp

- 采用 `com.google.ai.edge.litertlm:litertlm-android:0.18.0`，因为它有官方 Android Maven AAR、Java 可直接调用的 JVM API、FunctionGemma 工具调用接口和现成 `.litertlm` 模型。
- 首期不使用 RKLLM：RK3576 NPU 需要 Rockchip 专用转换、运行库和板卡验证，无法把 Hugging Face 权重或 `.litertlm` 直接加载到 NPU。
- 首期不使用 llama.cpp：虽然 GGUF 可移植，但会额外承担 FunctionGemma 模板、工具调用解析和 Android JNI 集成；本阶段没有收益。

初始模型采用：

- 仓库：`litert-community/functiongemma-270m-ft-mobile-actions`
- 文件：`mobile_actions_q8_ekv1024.litertlm`
- 格式：LiteRT-LM，dynamic INT8，1024 上下文
- 文件大小：约 289 MB
- 许可证：Gemma Terms，下载前必须由账号接受条款；模型不是 Apache/MIT 许可证

该模型只作为端侧链路基线。官方 S25 Ultra CPU 基准的峰值 RSS 约 510 MB、首 token 约 0.24 至 0.30 秒，不代表 RK3576 实测。现有公开问题显示 270M 模型在部分 LiteRT-LM GPU 路径可能持续输出 `<pad>`，因此首版明确锁定 CPU。

参考资料：

- [FunctionGemma 官方说明](https://ai.google.dev/gemma/docs/functiongemma)
- [LiteRT-LM Kotlin/JVM API](https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/kotlin/getting_started.md)
- [Android 可部署 Mobile Actions 模型](https://huggingface.co/litert-community/functiongemma-270m-ft-mobile-actions)

## 端到端流程

```text
休眠唤醒语法 Vosk
  -> 命中“潓美医疗”或“Hello Medical”
  -> 切换为当前语言的自由听写 Vosk，采集一条命令
  -> 规范化文本
  -> 现有 CommandCatalog 精确匹配
       -> 命中：直接输出 CommandEvent
       -> 未命中：FunctionGemma 手动工具调用
            -> 严格白名单校验
            -> 合法：输出 CommandEvent
            -> 不合法/无调用/多调用：静默拒绝并只写诊断日志
  -> 释放本次 conversation，恢复唤醒语法监听
```

语音路径之外，页面提供一个文本输入框和“解析文字”按钮。它只用于区分“Vosk 转写问题”和“FunctionGemma 指令理解问题”，不会绕过同一套白名单校验。

## 工具调用与安全边界

LiteRT-LM 使用 `ConversationConfig(automaticToolCalling = false)`。模型只能提出调用，应用不会让运行时自动执行工具。每次推理新建短生命周期 conversation，并只接受一个工具调用：

```json
{
  "name": "set_gas_duration",
  "arguments": { "duration_minutes": 30 }
}
```

允许的工具：

| 工具 | 允许参数 | 映射事件 |
|---|---|---|
| `power_on` | 无 | `POWER_ON` |
| `power_off` | 无 | `POWER_OFF` |
| `set_gas_duration` | `duration_minutes` 只能为 `30/60/120/480` | 对应四个 `GAS_*` 事件 |

拒绝条件：

- 返回零个或两个及以上工具调用。
- 工具名不在白名单。
- 参数缺失、多余、类型错误或不在允许时长中。
- 输入为空、超过 200 个 Unicode 字符或推理抛错。
- 在一次唤醒窗口中已经成功产生事件。

生成模型的概率不是可校准的业务置信度，首期不伪造 `confidence` 字段。页面只显示“精确匹配 / 模型白名单通过 / 已拒绝”。生产接入仍必须由独立设备控制层实施危险动作确认、安全联锁和状态回读。

## 组件边界

### `FunctionGemmaModelManifest` 与 `FunctionGemmaModelInstaller`

- 清单固定模型 ID、版本、文件名、大小和 SHA-256。
- 用户在页面粘贴无鉴权 HTTPS 直链后下载到 `files/functiongemma/staging/`，支持进度显示。
- 下载完成后先校验大小和 SHA-256，再原子移动为 `files/functiongemma/models/<version>/model.litertlm`。
- 校验失败保留旧版本并删除临时文件；URL 不写日志，避免泄漏带签名参数。
- 页面同时支持 Android 文件选择器导入完全相同的模型文件，方便 ADB/U 盘现场部署。

### `FunctionGemmaEngine`

- 小接口：`initialize(modelPath)`、`parse(text)`、`close()`，便于 JVM 测试使用假引擎。
- `LiteRtFunctionGemmaEngine` 在单线程 executor 上创建 CPU `Engine`，缓存目录使用 `cacheDir/functiongemma/`。
- 初始化、推理和关闭都不在主线程执行；Activity 销毁后过期回调不得更新 UI。
- 每次解析创建新的 conversation，关闭后才恢复 Vosk；Activity 离开时关闭 engine 释放约数百 MB 内存。

### `FunctionGemmaCommandValidator`

- 纯 Java、无 Android/LiteRT 依赖。
- 接收工具名与参数 Map，严格映射到现有 `CommandEvent`。
- 不做模糊纠错，不接受字符串中的 JSON，不执行任何函数。

### `HybridCommandRouter`

- 先调用现有 `CommandCatalog` 精确匹配。
- 未命中且模型已就绪时调用 `FunctionGemmaEngine`。
- 返回 `EXACT`、`MODEL_ACCEPTED` 或 `REJECTED`，携带原始文本、耗时、诊断原因和可选 `CommandEvent`。

### `FunctionGemmaCommandActivity`

- 复用现有麦克风权限、语言选择和事件日志样式。
- 显示模型缺失/下载中/校验中/初始化中/就绪/错误状态。
- 控件：语言切换、开始/停止监听、文本测试、模型 URL、下载、文件导入、删除模型、清空日志。
- Vosk 和 LiteRT-LM 生命周期由 Activity 前后台状态约束；离开页面停止录音并释放大模型。

## 模型交付流程

1. 开发电脑从 Hugging Face 下载原始 `.litertlm`，前提是账号已接受 Gemma Terms。
2. 记录准确字节数和 SHA-256，生成项目中的固定模型清单；模型本体加入 `.gitignore`，不提交 Git。
3. 在电脑上用 LiteRT-LM CLI 做一次工具调用冒烟测试。
4. 用户把同一文件上传到自己的云存储，提供可直接下载的 HTTPS URL。
5. App 使用该 URL 下载；因为清单校验的是内容哈希，换域名或临时签名不会改变可信模型版本。

云存储必须支持大文件、HTTP Range 不是首期硬要求、下载地址不能返回 HTML 登录页。首期不实现后台断点续传或自动静默升级；下载失败可手动重试。

## 测试与验收

### JVM 测试

- 三个合法工具及四个时长均映射到正确 `CommandEvent`。
- 未知工具、额外参数、缺少参数、字符串时长、非法时长、多工具调用全部拒绝。
- 精确词条命中时不调用假 FunctionGemma 引擎。
- 自然语言未精确命中时调用引擎，合法结果通过，异常和空结果拒绝。
- 模型清单拒绝错误大小和错误 SHA-256；安装失败不覆盖旧模型。
- Activity 生命周期 token 变化后，过期初始化/推理结果被丢弃。

### 构建检查

- `:app:testDebugUnitTest`、`:app:assembleDebug` 通过。
- APK 不包含 289 MB 模型文件，只包含 LiteRT-LM 运行库。
- APK 的 LiteRT-LM 原生库只出现在 `arm64-v8a` 与 `x86_64`，32 位设备显示受控提示而不是崩溃。

### 真机验收

- 新 Pad 能下载/导入并校验模型，初始化不阻塞 UI。
- 文本输入覆盖中英文精确命令、各至少 10 条自然表达、10 条无关语句和边界参数。
- 麦克风路径验证唤醒、自由听写、精确路由、模型回退、拒绝和恢复唤醒监听。
- 用 `dumpsys meminfo` 记录加载前、加载后、推理峰值、关闭页面后 PSS；记录冷启动初始化、热初始化和推理耗时。
- 事件日志只说明“Demo 事件”，不得宣称真实氧气机已执行。

## 明确不做

- 不直接控制串口、GPIO、RS232、PLC 或氧气机。
- 不自动执行 LiteRT-LM tool，不接受模型生成的任意 JSON 或代码。
- 不在首期承诺五种语言准确率、医疗合规、生产可靠性或 RK3576 NPU 加速。
- 不把模型放入 Git、APK 或公开仓库，也不绕过 Gemma 许可证下载限制。
- 不为单次使用引入通用插件框架、数据库或后台常驻服务。
