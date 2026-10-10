# FunctionGemma Mobile Actions model

This project uses the CPU LiteRT-LM artifact below for the FunctionGemma command demo. The model is installed outside the APK and is never committed to Git.

| Field | Value |
| --- | --- |
| Repository | `litert-community/functiongemma-270m-ft-mobile-actions` |
| Repository commit | `38942192c9b723af836d489074823ff33d4a3e7a` |
| Filename | `mobile_actions_q8_ekv1024.litertlm` |
| Size | `288964608` bytes |
| SHA-256 | `33e295cbd996b419bb1de8f3f85c5b6b01ee058a2c89bdb2173cf3e6ff4ce9d0` |
| Git object ID | `b8aa85420e2b3fe65c7a9cb080dba9bd8ec4bc9a` |
| Xet content hash | `16c70412a9b44de2cc7a749acf48ca78352372990e796f7294c88c27ddbe4e46` |
| Official source | <https://huggingface.co/litert-community/functiongemma-270m-ft-mobile-actions/blob/main/mobile_actions_q8_ekv1024.litertlm> |
| License | [Gemma Terms of Use](https://ai.google.dev/gemma/terms) |

The official repository is gated. An unauthenticated download returns `401 GatedRepo`; the user accepted the license in their own signed-in browser session. For faster local acquisition in China, the file was downloaded from the public `jegly/mobile_actions_q8_ekv1024.litertlm` copy through `hf-mirror.com`. Before use, its exact byte count, Git object ID, Xet content hash, and SHA-256 were verified against the official artifact metadata. The public-copy URL is only an acquisition convenience; the official repository and Gemma license above remain the source of record.

Local artifact path:

```text
downloads/functiongemma/mobile_actions_q8_ekv1024.litertlm
```

Verification commands:

```bash
stat -f 'bytes=%z' downloads/functiongemma/mobile_actions_q8_ekv1024.litertlm
shasum -a 256 downloads/functiongemma/mobile_actions_q8_ekv1024.litertlm
file downloads/functiongemma/mobile_actions_q8_ekv1024.litertlm
```

Expected `file` output is the generic `data`; HTML or text output is invalid.
