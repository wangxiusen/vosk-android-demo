# FunctionGemma 离线指令真机评测

> 日期：2026-10-10  
> 分支：`feat/functiongemma-command-demo`  
> 设备：`rk3568_mtb720`，Android 11 / API 30，arm64-v8a

## 1. 评测对象与安全边界

- LiteRT-LM Android：`0.18.0`，CPU 4 线程，context 1024。
- 模型：`mobile_actions_q8_ekv1024.litertlm`，288,964,608 bytes。
- SHA-256：`33e295cbd996b419bb1de8f3f85c5b6b01ee058a2c89bdb2173cf3e6ff4ce9d0`。
- 文件通过国内 Hugging Face 镜像下载，大小和内容哈希与受 Gemma 条款约束的目标文件一致；模型不打入 APK。
- 所有结果只映射到既有 `CommandEvent` 并写 UI 日志，不连接真实制氧机。

模型前还有一层确定性路由：固定中文或英文词条直接命中，不调用 FunctionGemma。只有未命中的自然文字才进入模型；模型输出必须恰好包含一个合法工具调用，否则拒绝。

## 2. 真机发现并修复的问题

首次初始化失败的实际错误是 LiteRT-LM 缓存目录不存在。原适配器随后关闭未初始化引擎时又抛出异常，遮蔽了根因。现已分别处理：

1. 初始化前创建并验证 `cache/functiongemma` 目录。
2. 初始化失败后的关闭异常作为 suppressed error 保留，不覆盖原始异常。
3. Conversation 增加 Mobile Actions 官方示例使用的 function-calling system instruction。
4. 拒绝日志增加工具调用数量和截断后的原始模型输出，便于区分“模型未调用工具”和“白名单校验拒绝”。

上述行为均增加了 JVM 回归测试。修复后真机约 5 秒进入“模型已就绪：275.6 MiB，CPU 4 线程”。

## 3. 冒烟输入矩阵

| 语言 | 输入 | 路径 | 结果 |
|---|---|---|---|
| 中文 | `开机` | 精确词条 | 接受：`POWER_ON` |
| English | `two hours gas production` | 精确词条 | 接受：`GAS_2_HOURS` |
| English | `Please turn off the machine because I am finished` | 模型 | 接受：`POWER_OFF`，多次观察约 2.8–3.3 秒 |
| English | `Please turn on the machine for me` | 模型 | 拒绝；模型要求补充运行时长，未产生合法工具调用 |
| 中文 | `请帮我打开设备` | 模型 | 拒绝；未产生工具调用 |
| 中文 | `让设备产气三个小时` | 模型 | 拒绝；不在 30 分钟/1/2/8 小时白名单内 |
| 中文 | `今天天气怎么样` | 模型 | 拒绝；无关输入 |

真机冒烟用例覆盖以上矩阵并通过。没有使用桌面 CLI 模拟 `.litertlm` 推理；本次以 Android 目标运行时和真实 RK3568 结果作为验收证据。

### 3.1 20 条自然改写

| 语言 | 输入 | 期望 | 实际路由/事件 | 延迟 |
|---|---|---|---|---:|
| 中文 | 请帮我开机 | `POWER_ON` | 拒绝，无调用 | 6000 ms |
| 中文 | 帮我把设备打开 | `POWER_ON` | 拒绝，无调用 | 3958 ms |
| 中文 | 现在启动机器 | `POWER_ON` | 拒绝，无调用 | 3344 ms |
| 中文 | 请把机器关掉 | `POWER_OFF` | 拒绝，无调用 | 3756 ms |
| 中文 | 停止设备运行 | `POWER_OFF` | 拒绝，无调用 | 4262 ms |
| 中文 | 开始产气半小时 | `GAS_30_MINUTES` | 拒绝，无调用 | 4187 ms |
| 中文 | 请产气三十分钟 | `GAS_30_MINUTES` | 拒绝，无调用 | 3807 ms |
| 中文 | 让设备产气一小时 | `GAS_1_HOUR` | 拒绝，无调用 | 4004 ms |
| 中文 | 开始两个小时的产气 | `GAS_2_HOURS` | 拒绝，无调用 | 3673 ms |
| 中文 | 请连续产气八小时 | `GAS_8_HOURS` | 拒绝，无调用 | 4383 ms |
| English | Please turn on the machine | `POWER_ON` | 接受：`power_on{}` | 2648 ms |
| English | Start the device | `POWER_ON` | 拒绝，无调用 | 4158 ms |
| English | Power the machine on | `POWER_ON` | 拒绝，无调用 | 3909 ms |
| English | Please turn off the machine | `POWER_OFF` | 接受：`power_off{}` | 2650 ms |
| English | Stop the device | `POWER_OFF` | 拒绝，无调用 | 3500 ms |
| English | Start gas production for half an hour | `GAS_30_MINUTES` | 拒绝，无调用 | 4018 ms |
| English | Produce gas for thirty minutes | `GAS_30_MINUTES` | 接受：`set_gas_duration{duration_minutes=30}` | 3434 ms |
| English | Run gas production for one hour | `GAS_1_HOUR` | 拒绝，无调用 | 3588 ms |
| English | Start two hours of gas production | `GAS_2_HOURS` | 拒绝，无调用 | 3709 ms |
| English | Produce gas for eight hours | `GAS_8_HOURS` | 拒绝，无调用 | 3584 ms |

自然改写正确率为中文 0/10、英文 3/10、合计 3/20。这里只统计 FunctionGemma 兜底路径；固定词条仍由确定性目录直接命中。

### 3.2 无关、非法与冲突输入

| 分类 | 输入 | 实际结果 | 延迟 |
|---|---|---|---:|
| 无关 | 今天天气怎么样 | 拒绝，无调用 | 3799 ms |
| 无关 | 现在几点了 | 拒绝，无调用 | 3585 ms |
| 无关 | 讲一个笑话 | 拒绝，无调用 | 3509 ms |
| 无关 | 你好 | 拒绝，无调用 | 3656 ms |
| 无关 | 把声音调大 | 拒绝，无调用 | 3925 ms |
| 无关 | What is the weather today | 拒绝，无调用 | 3557 ms |
| 无关 | What time is it | 拒绝，无调用 | 4508 ms |
| 无关 | Tell me a joke | 拒绝，无调用 | 3495 ms |
| 无关 | Play some music | 拒绝，无调用 | 3575 ms |
| 无关 | Connect to the network | 拒绝，无调用 | 3888 ms |
| 非法时长 | 让设备产气三个小时 | 拒绝，无调用 | 4363 ms |
| 非法时长 | 产气四十五分钟 | 拒绝，无调用 | 4297 ms |
| 冲突动作 | 先开机然后关机 | 拒绝，无调用 | 5974 ms |
| 冲突动作 | Turn the machine on and then off | 拒绝，无调用 | 5265 ms |
| 冲突时长 | Start gas production for one hour and eight hours | 拒绝，无调用 | 3588 ms |

安全拒绝为 15/15。35 次 FunctionGemma 推理延迟范围为 2.65–6.00 秒，平均约 3.93 秒；整轮在同一个已初始化引擎上完成，用时 141.2 秒。

最终验证结果：155 个 JVM 测试通过；Debug、Release unsigned 和 Android 测试 APK 均构建成功；真机页面导航/重建 2 个用例、冒烟矩阵 1 个用例和宽覆盖评测 1 个用例通过。近期 logcat 未发现 `FATAL EXCEPTION` 或 `ANR in com.huimei.voice`。

## 4. 结论

端侧链路已经成立：Vosk 负责语音转文字，固定词条提供快速稳定路径，FunctionGemma 处理未命中的自然表达，最终由严格白名单拦截不确定或非法输出。页面重建、模型初始化、固定词条、模型接受和拒绝路径均完成自动测试，测试期间未观察到应用 FATAL EXCEPTION 或 ANR。

当前模型不能作为氧气机领域的量产理解模型。它是英文 Mobile Actions 数据微调产物，已验证的自然英文覆盖很窄，中文自然表达没有达到可用水平。下一阶段应使用自有工具定义构建中英文改写、否定句、冲突句、无关句和非法时长数据进行领域微调，再在工业噪声与真实 Vosk 转写错误上统计准确率、误触发率和拒绝率。

当前 Release 构建还有一个非阻断告警：LiteRT-LM 0.18.0 使用 Kotlin 2.4 元数据，而项目当前 R8 元数据解析上限为 2.2。APK 已成功生成并在真机运行，但升级构建链后仍需重新做 Release 构建与安装验证。

参考：

- [FunctionGemma 官方模型卡](https://huggingface.co/google/functiongemma-270m-it)
- [Google Mobile Actions 数据集](https://huggingface.co/datasets/google/mobile-actions)
- [Google AI Edge Gallery Mobile Actions 示例](https://github.com/google-ai-edge/gallery/blob/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/customtasks/mobileactions/MobileActionsTask.kt)

内存和 APK 证据见 [设备内存记录](2026-10-10-functiongemma-device-memory.log)。
