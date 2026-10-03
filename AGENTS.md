# Jot Android agreements

Read [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) before changing models, build tooling,
maintenance workflows or releases. Keep substantial decisions and unfinished work
in this repository's issues. On Monroe's machine, apply his existing development
preferences in `/Users/monroe/.codex/DEVELOPMENT.md` when that file is available.

- Primary neural inference requires verified Qualcomm HTP/NPU execution. CPU audio
  preparation and decoding control are expected; fallback changes require an
  explicit product decision. Distinguish hardware execution from output quality.
- Preserve application identity and installed user/model data. Device installation
  requires authorization; never reset storage to make validation easier.
- Keep private audio, dictation, device credentials and signing keys out of Git,
  issue reports and build artifacts. Synthetic fixtures are appropriate for CI.
- Run `scripts/check.sh` for source/build changes. Model/runtime changes also need
  the synthetic native tests on a compatible device. State unavailable gates.
- Keep GitHub Actions manual-only (`workflow_dispatch`). Public app releases and
  Store distribution are separate from source publication and local installs.

## Delivery speed (solo repository)
Monroe is the only user. Merge authorized fixes and features to the default branch as soon as the checks you can run pass. Do not wait on CI runners or per-PR device validation. Monroe's end-to-end use when he updates is the acceptance test. Say plainly what you could not run, leave a short "test when you sit down" list in the PR, and fix forward on breakage before starting new work. Data, secrets, installs on his devices, destructive actions and public releases keep their own gates.
