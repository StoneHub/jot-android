# Android evaluation and maintenance roadmap

Owner: this repository's maintainer, with user testing for physical acceptance.
Current status: the standalone project builds; Base, Small and cleanup NPU
execution were verified on a compatible device. Cleanup fidelity remains weak.
Source identity and APK hash were preserved by the repository extraction.

## 1. Improve cleanup fidelity

Problem: the current small model may retain fillers, change capitalization or
remove meaning; a previous prompt dropped a negation.

Next action: build a synthetic corpus covering fillers, names, numbers, negations,
URLs and fragments. Compare prompt/runtime behavior and any stronger model only
after verifying a compatible compiled NPU bundle and its licensing.

Acceptance: original/candidate comparison remains visible; limits/corruption are
flagged; fragment behavior is preserved; quality and latency are measured without
hidden CPU or network inference. Native lifetime tests continue to pass.

## 2. Reduce size while retaining real hardware inference

Problem: the debug APK is about 145 MB and initial model weights about 1.6 GB.
The available quantized speech graph needs a UINT16 tensor interface unavailable
in the current Java binding.

Next action: audit unused runtime libraries, then evaluate a narrow JNI interface
for quantized speech. Record storage, load time, inference and memory changes.

Acceptance: smaller artifacts/models with equivalent NPU proof and measured
accuracy; unchanged installed application identity and preserved user/model data.

## 3. Finish real-device acceptance

Next action: test microphone permissions and capture, folded/unfolded layouts,
names/noise/long utterances, airplane-mode inference after setup, and repeated
sessions for sustained warmth/battery behavior.

Acceptance: exact installed APK, supported device family and observed results are
recorded separately from build/CI evidence. Synthetic tests never replace voice
quality or energy acceptance. Reports use sanitized samples and measurements.

## 4. Decide the cross-app product slice

Next action: use evaluation results to choose speech/cleanup defaults and the
smallest useful floating-button or keyboard flow using established Android APIs.

Acceptance: integration preserves existing drafts, cursor/selection semantics and
sensitive-field boundaries; text insertion follows an explicit user action.
Implement this after model evaluation, not as part of routine maintenance.

## Maintenance checklist

At each maintenance pass: verify the default branch/build, review coordinated
vendor-runtime changes and security notices, inspect the open roadmap, and update
its concrete next action. Keep hosted checks manual-only. Update [the roadmap issue](https://github.com/StoneHub/jot-android/issues/1)
as the durable remote status record. No scheduled task is enabled without the maintainer's choice.
