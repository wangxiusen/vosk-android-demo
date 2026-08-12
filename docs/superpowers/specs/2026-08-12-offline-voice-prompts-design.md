# 离线事件提示音设计

## 目标

在现有潓美医疗 Vosk Android Demo 中使用 Android 系统 `MediaPlayer` 播放用户提供的中英文 MP3。提示音与标准事件绑定；播放期间暂停语音识别，避免扬声器声音被麦克风再次识别。

## 范围

- 复制六个用户提供的 MP3 到 `app/src/main/res/raw/`。
- 中文和英文分别支持唤醒、1 小时产气、8 小时产气提示音。
- 不增加第三方播放器或 TTS SDK。
- 不为尚未提供音频的 30 分钟、2 小时、开机和关机事件合成或代用声音。
- 不改变现有识别词条、标准事件或日志格式。

## 音频映射

| 语言 | 标准事件 | 源文件 | Android 资源名 |
|---|---|---|---|
| 中文 | `WAKE_UP` | `我在请说.mp3` | `zh_wake_up.mp3` |
| 中文 | `GAS_1_HOUR` | `一小时产气.mp3` | `zh_gas_1_hour.mp3` |
| 中文 | `GAS_8_HOURS` | `八小时产气.mp3` | `zh_gas_8_hours.mp3` |
| English | `WAKE_UP` | `I am here please speak.mp3` | `en_wake_up.mp3` |
| English | `GAS_1_HOUR` | `Gas production has started for one hour.mp3` | `en_gas_1_hour.mp3` |
| English | `GAS_8_HOURS` | `Gas production has started for eight hours.mp3` | `en_gas_8_hours.mp3` |

所有文件均为 MP3、24 kHz、单声道、96 kbps，时长约 1.46 至 3.26 秒。

## 组件设计

### `VoicePromptCatalog`

纯映射组件。输入 `VoiceLanguage` 和 `CommandEvent`，返回对应的 `raw` 资源 ID；未配置音频的事件返回空值。该组件不访问播放器或识别器，可由 JVM 单元测试覆盖全部映射。

### `VoicePromptPlayer`

使用 `MediaPlayer.create(Context, resourceId)` 创建并播放一次提示音。同一时刻只允许一个播放器实例：新播放请求会先释放旧实例。完成或出错时都释放播放器并通知调用方恢复识别；`stop()` 和 `close()` 必须幂等。

### `VoiceRecognitionController`

增加提示音期间的暂停与恢复接口。暂停时调用 `SpeechService.setPause(true)` 并停止倒计时任务，但不释放模型。恢复时调用 `SpeechService.setPause(false)`；如果状态机仍处于 `AWAKE`，从恢复时刻重新开始完整 8 秒窗口并恢复倒计时。

### `MainActivity`

收到 `onWakeUp` 或 `onCommand` 时先写现有事件日志，再查询提示音映射。存在提示音时暂停识别并播放；不存在提示音时保持现有行为。语言切换、停止监听和 Activity 销毁时停止当前提示音，防止旧语言音频继续播放。

## 事件时序

### 唤醒

```text
识别到 WAKE_UP
  -> 写唤醒日志
  -> 暂停 SpeechService 与倒计时
  -> 播放当前语言的“我在，请说”
  -> 播放完成
  -> 恢复 SpeechService
  -> 从 8 秒重新开始命令窗口
```

### 命令

```text
识别到 GAS_1_HOUR 或 GAS_8_HOURS
  -> 写命令日志并完成业务事件
  -> 暂停 SpeechService
  -> 播放当前语言确认音
  -> 播放完成
  -> 恢复休眠监听
```

其他没有音频的命令继续写日志并立即回到休眠监听。

## 错误与生命周期

- `MediaPlayer.create` 返回空或播放报错：写诊断日志、释放播放器并恢复识别。
- 用户播放期间切换语言：停止旧提示音，再切换模型；旧播放回调不得恢复已经替换的识别会话。
- 用户点击停止监听：停止并释放提示音，然后释放识别资源。
- Activity 销毁：先关闭播放器，再关闭识别控制器。
- 播放期间即使收到迟到的识别回调，也不得触发第二个事件。

## 验收

- 六个事件与六个 MP3 映射正确，其他事件没有音频。
- 中文唤醒、1 小时和 8 小时事件播放中文音频。
- English 唤醒、1 小时和 8 小时事件播放对应英文音频。
- 播放时识别暂停；唤醒音结束后 UI 显示完整 8 秒命令窗口。
- 播放完成、播放失败、语言切换、停止和销毁均不会遗留播放器或永久暂停识别。
- JVM 测试、Android lint 和 Debug APK 构建通过，并在已连接真机验证实际扬声器播放。
