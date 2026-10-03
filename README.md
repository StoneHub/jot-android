# Jot Android

A standalone Android project for local, hardware-accelerated dictation and text
cleanup. The current app is **Jot Model Lab**: an evaluation harness before
floating-button, keyboard or Accessibility integration.

The initial compiled models target **SM8850 / Snapdragon 8 Elite Gen 5 for
Galaxy**. Other chipsets are not yet supported by these bundles. See
[device evidence and limitations](VALIDATION.md).

## Clone and build

```sh
git clone https://github.com/StoneHub/jot-android.git
cd jot-android
python3 scripts/prepare-runtime.py
# Configure ANDROID_HOME or sdk.dir in local.properties; use Java 21 and SDK 36.
./scripts/check.sh
```

Open this directory directly in Android Studio. Build outputs, local SDK settings
and downloaded weights are ignored. This project has its own history, issues and
manual validation workflow. [Development and maintenance](docs/DEVELOPMENT.md)
explains how to keep it current; [issue #1](https://github.com/StoneHub/jot-android/issues/1)
tracks the next work. The [Mac app](https://github.com/StoneHub/jot)
is maintained separately.

## Try it

1. Open **Jot Model Lab**. The initial development install has the three models seeded.
   Other installs use **Prepare models** once over Wi-Fi.
2. Load the built-in example, or tap **Record**, grant microphone access, speak
   for up to 30 seconds and tap **Stop recording**.
3. Tap **Compare both on the same recording**. Base and Small receive identical
   audio. Optional expected text gives word error rate; three repeats show cold
   loading and subsequent warm execution.
4. Choose **Use for cleanup** on a transcript, then **Clean up with Qwen3 · NPU**.
   Review the candidate alongside the original. Text is never inserted into
   another app.
5. **Save results** explicitly exports JSON with text and timings, without audio.

Recordings live in memory, recording stops when the activity leaves the
foreground, and model files stay in app-private storage. No telemetry or automatic
uploads. Speech/cleanup work offline after setup; INTERNET is used for pinned
model downloads. The optional system recognizer is explicitly on-device and its
accelerator is unverified. It may reject recorded audio.

## Hardware contract and current limits

- Whisper Base and Small use Qualcomm's precompiled FP16 encoder/decoder graphs
  for **Snapdragon 8 Elite Gen 5 for Galaxy**, with ONNX Runtime's QNN HTP provider.
  CPU graph fallback is disabled. Log-mel preparation, token selection and cache
  handling run on the CPU; neural inference runs on HTP.
- Qwen3 0.6B uses the compiled w4a16 Genie bundle, a QAIRT-only registered plugin,
  NPU compute selection and `QnnHtp` model configuration. No alternate execution
  backend is selected on failure.
- This prototype is English-only, up to 30 seconds per sample, with 199 speech
  decode steps and up to 1,200 cleanup input characters / 256 generated tokens.
  A speech token limit is reported; cleanup output must be reviewed.
- Small uses FP16 rather than the available w8a16 bundle: ORT Java does not expose
  the UINT16 tensor type required by that quantized graph. This is a concrete
  memory/size limitation to revisit with a JNI adapter.
- Model weights occupy about **1.6 GB** unpacked. The diagnostic build also ships
  vendor runtime libraries; this is an evaluation app, not a final lightweight
  distribution.
- Timings include basic vendor profiling. PSS is app process memory; battery and
  thermal readings are whole-phone snapshots, not app-attributed energy usage.
- Successful synthetic inference proves execution, not microphone quality,
  accuracy across accents/noise, sustained battery efficiency or safe cleanup.
  Test names, numbers, negations, fragments, long thoughts and noisy speech.

## Reproduce

Use Java 21 and an Android SDK with API 36, on macOS/Linux:

```sh
python3 scripts/prepare-runtime.py
# Set sdk.dir in local.properties, or configure ANDROID_HOME.
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest
adb -s "$JOT_ANDROID_SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
```

The script verifies the official GenieX 0.8.0 archive SHA256, removes its duplicate
QNN runtime libraries and uses the Maven QAIRT 2.50.0 runtime instead. Source,
wrapper and build configuration are committed; runtime archives and model weights
are ignored. There is no paid model service or account requirement.

After setup on the target phone:

```sh
adb -s "$JOT_ANDROID_SERIAL" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s "$JOT_ANDROID_SERIAL" shell am instrument -w \
  -e class com.example.jotmodellab.HardwareSmokeTest \
  com.monroestone.jotmodellab.test/androidx.test.runner.AndroidJUnitRunner
```

The test runs only the bundled synthetic sample and synthetic cleanup text. It
writes app-private `npu-smoke.json` and ORT/QNN profiles. User audio is never
exported by this test. `reference.wav` was generated with macOS speech synthesis
and converted to mono 16 kHz PCM16; it is not a private recording.

## Pinned upstream assets

URLs and SHA256 values are in `ModelCatalog` in `ModelFiles.kt`. The downloader
verifies archives and atomically stages a flat allowlist of expected files.

- [Qualcomm Whisper Base](https://aihub.qualcomm.com/models/whisper_base)
- [Qualcomm Whisper Small](https://aihub.qualcomm.com/models/whisper_small)
- [Qualcomm Qwen3 0.6B](https://aihub.qualcomm.com/models/qwen3_0_6b)
- [ONNX Runtime Qualcomm provider](https://github.com/onnxruntime/onnxruntime-qnn)
- [Qualcomm GenieX Android example](https://github.com/qualcomm/ai-hub-apps/tree/release/geniex_chat_android)

Runtime and model licenses apply independently. This is an evaluation
prototype; store distribution, cross-device support and final size optimization
remain separate work.
