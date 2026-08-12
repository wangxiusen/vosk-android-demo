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
