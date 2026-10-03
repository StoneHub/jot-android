# Device validation — 2026-10-03

Connected phone: `SM-F976U1` / `SM8850`, Android 17 / API 37.

Installed debug APK SHA256 matches the locally checked APK:

`439df1ff9df7a8954ca5dfef714dddc975851ab8458acb8ea7e6a03882d069ee`

APK: 144,994,244 bytes. Model weights: approximately 1.6 GB unpacked.

Validation:

- Android assembleDebug + assembleDebugAndroidTest: pass.
- Unit tests: 4 pass. Android lint: pass with dependency/update and catalog warnings.
- Device instrumentation: 2 pass (all three NPU models; leaving during native cleanup).
- Jot portable checks: 78 Python tests, feedback removal and 27 synthetic suggestion fixtures pass.
- Independent Standards and Spec reviews: two lifecycle/truncation findings fixed; follow-up review passes.
- Mac app sources and build configuration were not changed; no Mac app install or merge.

Synthetic 6.6-second speech fixture (cold sessions, basic profiling enabled):

| Model | Load | Audio prep | Inference |
|---|---:|---:|---:|
| base | 427 ms | 137 ms | 619 ms |
| small | 850 ms | 38 ms | 1044 ms |
| qwen3-0.6b | 2877 ms | — | 421 ms |

Both speech models returned:

> Please send the blue folder tomorrow morning. The meeting starts at 9.30. Do not change the number 42.

Speech encoder/decoder completed with QNN HTP and CPU graph fallback disabled. Cleanup completed using the QAIRT NPU plugin and compiled QnnHtp model. CPU handles audio preparation and decoding control; this is not a claim of zero CPU use.

Cleanup from `Um please send the blue folder tomorrow morning. Do not change 42.` returned:

> um please send the blue folder tomorrow morning. do not change 42.

The candidate preserved details in this test, but left the filler and lowercased sentences. An earlier prompt dropped the negation and was flagged. Cleanup quality is an open evaluation question; no text is inserted automatically.

The native exit test clears the ViewModel during model initialization/generation and waits for bounded completion before disposal.

Still requires Monroe: real microphone permission/input, names/noise/accent accuracy, fragment/number/negation cleanup evaluation, folded and unfolded visual acceptance, sustained warmth/battery use, and airplane-mode acceptance after setup. The phone was locked at the last visual check; installed and native inference proof does not establish visible UI acceptance.

Deferred: floating bubble/keyboard integration, Accessibility insertion, wider device coverage, quantized speech JNI support, APK/runtime trimming and larger NPU cleanup candidates. No public/store release.
