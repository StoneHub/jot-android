# Development and maintenance

This repository owns the Android app, its models/runtime integration and Android
validation. Jot for Mac is a sibling product, not a build dependency. Keep Android
changes here; use cross-links when a product decision affects both platforms.

## Check a change

1. Run `scripts/check.sh` locally. It prepares the checksum-pinned vendor archive,
   builds debug app/test APKs, runs unit tests and Android lint. Capture long logs
   under ignored `build/` and inspect the final result.
2. For native model/runtime changes, use a supported phone and run the synthetic
   instrumentation tests described in the README. Preserve the installed app's
   data and record the exact APK hash/device family. Actual voice quality and
   sustained energy use require separate physical acceptance.
3. Review changes independently when materially altering inference, privacy or
   native lifecycle. Back up completed work to the repository. Source publication
   does not authorize distributing vendor weights or signing credentials.

The **Android checks** GitHub workflow is manually triggered and requires no
phone. It validates build/unit/lint gates, not NPU execution. No recurring hosted
runner or automatic paid build is configured.

## Keep it current

[The roadmap](ROADMAP.md) defines the current work. Once published, the repository's
open roadmap issue is the durable status and next-action record.
After each evaluation or maintenance pass, update it with concrete findings and
links to fixes. Start with build failures, privacy regressions and model/runtime
compatibility; then dependency/security updates; then accepted product work.

Keep ONNX Runtime, QNN plugin, QAIRT and GenieX version changes coordinated. Verify
archive hashes and packaged native library parity. A newer dependency is not an
upgrade candidate until its model compatibility is tested. Use the built-in
synthetic sample for reproducible comparison; publish only sanitized measurements.

Current work: cleanup fidelity, runtime/weight size, real-device voice/energy and
foldable acceptance. Cross-app integration follows those findings. Capture new
work as an issue before broadening a maintenance run. A scheduled reminder, if
enabled, should report only new actionable findings and needs; recurring source
or device changes require their own scoped authorization.

## Origin

Android-only commits were extracted from `StoneHub/jot` at revision
`3503cd6a230e34dbda17269a19ac3c137d5397bb` with `git subtree split`; paths are now
at the repository root. The original implementation history is preserved. Mac
source and private development caches were excluded. The application ID remains
unchanged, so repository migration does not replace or reset the installed app.
