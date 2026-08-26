# AISHELL3 中文女声音色扩充与真机验收

日期：2026-08-26

设备：Xiaomi 2201122C，Android 15，arm64-v8a

## 选择依据

工程内 `speakers.txt` 包含 174 个模型 speaker。将其顺序与 AISHELL-3 的 `spk-info.txt` 性别、口音属性对应后，可确认其中 138 个为女声：北方 102 个、南方 34 个、其他口音 2 个。

为了让工业设备上的下拉选择保持易用，本次没有展示全部 138 个女声，而是保留原有两个 speaker，并扩充到北方、南方各 5 种：

| UI 音色 | speaker ID | AISHELL3 speaker | 属性 |
|---|---:|---|---|
| 中文女声 01 | 4 | SSB0016 | female / north |
| 中文女声 02 | 122 | SSB1125 | female / south |
| 中文女声 03 | 0 | SSB0005 | female / north |
| 中文女声 04 | 1 | SSB0009 | female / south |
| 中文女声 05 | 16 | SSB0145 | female / north |
| 中文女声 06 | 19 | SSB0197 | female / south |
| 中文女声 07 | 57 | SSB0534 | female / north |
| 中文女声 08 | 104 | SSB0915 | female / south |
| 中文女声 09 | 113 | SSB1055 | female / north |
| 中文女声 10 | 147 | SSB1575 | female / south |

数据集来源为 [OpenSLR 93](https://openslr.org/93/)，speaker 属性文件核对自 [AISHELL-3 spk-info.txt 镜像](https://huggingface.co/datasets/shenyunhang/AISHELL-3/blob/main/spk-info.txt)。

## 真机结果

自动化测试在同一次中文模型加载后依次选择 10 个 UI 音色并完成实际合成和播放。日志记录了全部 10 个 speaker ID 的 `request` 与 `generate_end`，没有合成异常或 Android 崩溃。新增 8 个音色的短句生成耗时为 138～189 ms，生成音频时长为 3,040～4,352 ms。

该扩充只增加 Java 音色映射，不增加模型文件，也不会因为音色数量增加而同时常驻更多 TTS 模型。关键日志见 [2026-08-26-aishell3-female-voices.log](2026-08-26-aishell3-female-voices.log)。
