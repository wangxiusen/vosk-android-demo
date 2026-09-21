# Third-Party Notices

## Vosk Android

- Project: `alphacep/vosk-api`
- Android artifact: `com.alphacephei:vosk-android:0.3.75`
- Source: https://github.com/alphacep/vosk-api
- License: Apache License 2.0

## Java Native Access (JNA)

- Artifact: `net.java.dev.jna:jna:5.18.1@aar`
- Source: https://github.com/java-native-access/jna
- License: Apache License 2.0 or LGPL 2.1+

## Chinese model

- Model: `vosk-model-small-cn-0.22`
- Source: https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip
- Downloaded SHA-256: `3af8b0e7e0f835ae9d414ce5df580237a3cfb08d586c9fbbb0f7ff29ad5b14ba`
- License listed by the Vosk model catalog: Apache License 2.0

## English model

- Model: `vosk-model-small-en-us-0.15`
- Source: https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip
- Downloaded SHA-256: `30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498`
- License listed by the Vosk model catalog: Apache License 2.0

The English model files inherited from the upstream Android demo were compared with the official archive; the runtime model files match. The upstream demo carries a shorter README, and this project adds a deterministic `uuid` cache marker.

## sherpa-onnx Android

- Project: `k2-fsa/sherpa-onnx`
- Android AAR: `sherpa-onnx-1.13.6.aar`
- Source: https://github.com/k2-fsa/sherpa-onnx/releases/tag/v1.13.6
- Downloaded SHA-256: `0012d9a28f15bd6fb966b62b70a75da3990512fdccce28b83098248ce4be1698`
- License: Apache License 2.0

## 3D-Speaker CAM++ speaker-embedding model

- Model: `speech_campplus_sv_zh-cn_16k-common` converted to ONNX and distributed by sherpa-onnx
- Bundled file: `3dspeaker_speech_campplus_sv_zh-cn_16k-common.onnx`
- Source model: https://www.modelscope.cn/models/iic/speech_campplus_sv_zh-cn_16k-common
- ONNX download: https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/3dspeaker_speech_campplus_sv_zh-cn_16k-common.onnx
- Bundled size: `28281138` bytes
- Downloaded SHA-256: `f682b514c05d947ee3fa91cd6ec6c5c7543479a128373fa29b1faedccd21fd11`
- License listed by the source model page: Apache License 2.0

## Vosk speaker-identification model

- Model: `vosk-model-spk-0.4`
- Source: https://alphacephei.com/vosk/models/vosk-model-spk-0.4.zip
- Downloaded archive size: `13869103` bytes
- Downloaded archive SHA-256: `a74d8f51144484813e16af689bb0f916b7a111e2347f467c4933c1166097b5a7`
- License listed by the Vosk model catalog: Apache License 2.0
- Bundled files:
  - `mfcc.conf`: `94` bytes, SHA-256 `86f4df625b950b81b68864fdefabf26aa65079f84ea20f40e7f9e7ccbb9c456c`
  - `mean.vec`: `1199` bytes, SHA-256 `3bc5dfa9479c3aaacf4f45c155e9bd4772785449112566fac23578c09e93bcd7`
  - `transform.mat`: `65551` bytes, SHA-256 `fab14481fb27c41df0f1825afe18530e4f6d128dbb2c239ee86d8d7096e22c33`
  - `final.ext.raw`: `14929171` bytes, SHA-256 `d30354474ac719b3cfce58186463302b988cf22ce3afee7237a2da862c4a91d1`

## Chinese AISHELL3 VITS TTS model

- Model: `vits-icefall-zh-aishell3`
- Source: https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-icefall-zh-aishell3.tar.bz2
- Downloaded archive SHA-256: `ab468db3a3308cdd861495e0db2f25d79418a0c00639f74944c7cdf5dd8c6ec1`
- Bundled `model.onnx` SHA-256: `5511d651b7840c0a93a6bbfd4afd070a2c7f39ca1ec3ff2ecd73191519bbb852`
- Dataset: AISHELL-3 / OpenSLR 93
- Dataset license: Apache License 2.0

## English Piper LJSpeech VITS TTS model

- Model: `vits-piper-en_US-ljspeech-medium`
- Source: https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-ljspeech-medium.tar.bz2
- Piper voice source: https://huggingface.co/rhasspy/piper-voices/tree/main/en/en_US/ljspeech/medium
- Downloaded archive SHA-256: `3dfb4b759d8be032a4903a9538d128b0fda2a06ab1de6cbc2d93a97e2dd83dba`
- Bundled `en_US-ljspeech-medium.onnx` SHA-256: `8ceba58a4b540d4e7e7e24ad079cf0d92762a4fd334e059f12940787c6c37b3d`
- Voice repository license: MIT
- Dataset: LJSpeech, public domain

## AndroidX CameraX

- Artifacts: `androidx.camera:camera-camera2:1.4.2`, `camera-lifecycle:1.4.2`, `camera-view:1.4.2`
- Source: https://github.com/androidx/androidx/tree/androidx-main/camera
- License: Apache License 2.0

CameraX 1.4.2 is intentionally pinned so this Demo can retain its existing Android API 21 minimum.

## OpenCV Android

- Artifact: `org.opencv:opencv:4.13.0`
- Source: https://github.com/opencv/opencv
- License: Apache License 2.0

## YuNet face detector

- Model: `face_detection_yunet_2023mar.onnx`
- Source: https://github.com/opencv/opencv_zoo/tree/main/models/face_detection_yunet
- Bundled size: `232589` bytes
- Bundled SHA-256: `8f2383e4dd3cfbb4553ea8718107fc0423210dc964f9f4280604804ed2552fa4`
- License listed in the model directory: MIT

## SFace face recognizer

- Model: `face_recognition_sface_2021dec_int8.onnx`
- Source: https://github.com/opencv/opencv_zoo/tree/main/models/face_recognition_sface
- Bundled size: `9896933` bytes
- Bundled SHA-256: `2b0e941e6f16cc048c20aee0c8e31f569118f65d702914540f7bfdc14048d78a`
- License listed in the model directory: Apache License 2.0

The repository license does not replace a product-specific legal review of the SFace training-data provenance or the collection, consent, retention and deletion rules for biometric data. Complete that review before medical or commercial production use.
