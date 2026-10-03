# Evaluation contract

Requested outcome: an installed Android test app that lets Monroe evaluate local
speech recognition and cleanup on his Fold before building cross-app insertion.

- Primary neural inference must use the phone's NPU. No hidden CPU/cloud fallback.
  CPU audio preprocessing, control flow and token handling are expected.
- Confirm the connected hardware identity rather than infer it from the nickname.
- Compare two speech candidates with identical audio; separate load, preprocessing
  and inference timings. Include a bundled synthetic sample and microphone input.
- Cleanup preserves meaning, names, numbers, negations and intentional fragments;
  show input and output for human review. Small-model quality is an experiment,
  not an assumed guarantee. Flag obvious corruption and token truncation.
- Measurements should expose practical constraints: download/storage size, cold
  and warm runs, process memory and honest whole-phone thermal/battery snapshots.
- Speech tests precede floating bubble, keyboard and Accessibility development.
  Those integrations are deferred; the lab does not modify another app's text.
- No automatic uploads; audio stays in memory. Export of text/timings is explicit.
- Install, launch, synthetic NPU execution and physical voice acceptance are
  separate evidence. Keep the source remotely recoverable under Jot's draft PR
  workflow; do not merge or publish a public app release in this task.
