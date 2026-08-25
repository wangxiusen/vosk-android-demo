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
