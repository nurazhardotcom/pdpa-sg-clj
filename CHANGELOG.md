# Changelog

All notable changes to **pdpa-sg-clj** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Portable extraction boundary: `detect.cljc`, `audit_context.cljc`, and
  `policy_template.cljc`, plus small `json.cljc` / `clock.cljc` adapters.
- Shared fictional parity fixtures in `test/pdpa/portable_fixtures.cljc` and
  Node ClojureScript coverage via `clojure -M:test:cljs` (also exposed as
  `bb test:cljs`).
- Explicit incomplete-coverage contracts for the in-memory detector and the
  historical scanner compliance signal.
- CI `example-audit` job (`.github/workflows/ci.yml`) exercising the
  documented onboarding path end to end: `bb scan examples/minimal_project`
  plus `bb audit examples/minimal_project` must both exit 0.
- CI `lint` job: `clj-kondo --lint src test scripts --fail-level warning`
  (pinned v2026.08.04 release, SHA-256 verified).

### Changed
- `nric`, `redact`, `rules`, `sarif`, `checklist`, `report`, and `version` are
  now portable `.cljc` namespaces. JVM/JS parsing differences are isolated;
  native I/O and process behavior remain in the CLJ adapters.
- Native `pdpa.scan` now delegates classification/result aggregation to
  `pdpa.detect/result` while retaining the exact native skip globs and result
  shape.
- Report timestamps are injected into the audit context by the native
  orchestrator, keeping report fixtures deterministic.
- Portable tests are `.cljc`; `scan_test.clj` and other filesystem/process
  coverage remain native. CI now runs the Node suite as well as Babashka and
  JVM tests.

### Preserved limitations / follow-up
- Checklist marker placement/semantics are characterized but not changed. A
  marker only arms a following checkbox; the bundled file's current placement
  remains as-is.
- Native compliance wording remains unchanged. Its scanner-based
  `COMPLIANT`/zero-critical signal is not a complete PDPA or legal
  determination; wording changes require a separate reviewed behavior track.
- The historical `redact/phone-re` `;;` comment/literal behavior is retained
  and characterized by the shared fixture; correcting phone redaction is a
  separate behavior change.

### Fixed
- `clj-kondo` clean (was 13 warnings): removed dead private fns
  (`audit/opt-val`, `scan/rg-line-seq-bb`), dropped unused requires,
  added missing namespace requires in tests.
- NRIC/FIN checksum aligned to the published Mod-11 algorithm: the +4
  offset now applies to T/G only (was: all prefixes), and the F/G/M
  table tail is corrected to `...MLK` (was: `...KLM`). Canonical
  community vectors now validate; previous outputs correctly fail —
  see `nric_test.cljc`. M prefix (2022+ FINs): same 7-digit weights,
  +3 offset, own table with J at idx 8 (was: value-3 prepend, +4
  offset, shared table) — corroborated by independent validators.

### Security
- CI actions pinned to commit SHAs; `SECURITY.md` and Dependabot added.

## [0.3.0] - 2026-09-06

### Fixed
- `pdpa.scan` finding `:path` is now a plain string (was the raw rg-JSON
  `{"text": ...}` map); legacy map shapes are still tolerated by exporters.
- Pre-commit hook called a non-existent `pdpa.nric/checksum-valid?`;
  now calls `pdpa.nric/valid?` (previously every structural NRIC match
  fail-closed the commit).
- `pdpa.version/toolkit-version` synced to `0.2.0` (matched `VERSION`,
  README, and CHANGELOG all along); README's `bb version` reference
  corrected to `bb about`.
- `bb audit --json` / `--format` / `--out` were silently ignored because
  `babashka.cli/parse-opts` returns a flat options map; now honoured.
- Scanner never skipped vendored trees: `rg --json` was invoked with
  `--no-ignore` and no excludes, so one `npm install` made it slurp
  `node_modules` into memory until the kernel OOM-killed the run.
  `scan` now always excludes `.git/`, `node_modules/`, `target/`,
  `out/`, `.cpcache/` (new `skip-globs`, regression-tested).

### Added
- SARIF 2.1.0 export (`pdpa.sarif`, `bb scan --format sarif`,
  `bb audit --format sarif --out audit.sarif`) for auditors and GitHub
  code scanning (CI uploads via `upload-sarif`).
- Executive Markdown/HTML auditor reports (`pdpa.report`,
  `bb audit --format html|md`), with documented PDF conversion for
  ISO 27001 filings.
- Tool-independent rule pack (`pdpa.rules`, 20 PII + secret rules):
  JWT, AWS secret/session keys, CyberArk Conjur, Slack, OpenAI,
  Anthropic, GCP, Azure, extended GitHub tokens. Exportable via
  `bb export-rules --format gitleaks|json`.
- Real-time editor feedback: VS Code task + problemMatcher
  (`editors/vscode/`), Neovim `:PdpaScan` quickfix (`editors/nvim/`),
  and a full VS Code extension scaffold (`editors/pdpa-vscode/`).
- GitHub Actions CI (`.github/workflows/ci.yml`: test job + SARIF scan
  job) replacing `.gitlab-ci.yml`; README/CHANGELOG links moved from
  private GitLab to GitHub.
- `bb scan --format quickfix` (`path:line: [SEV] label`) for editors.

## [0.2.0] - 2026-07-16

### Added
- `VERSION` file as single source of truth for semver
- `CHANGELOG.md` documenting release history
- Production-release badge in `README.md` linking to GitLab Releases page
- Production-release stamp line crediting Nur Azhar

## [0.1.0] - 2026-06-21

### Added
- Initial public release of pdpa-sg-clj
- Singapore PDPA 11-obligation checklist, scanner, redactor, audit CLI
- 6 policy templates in `resources/policies/`
- 4 cognitect test-runner test namespaces under `test/pdpa/`
- GitLab CI

[Unreleased]: https://github.com/nurazhardotcom/pdpa-sg-clj/compare/v0.3.0...main
[0.3.0]: https://github.com/nurazhardotcom/pdpa-sg-clj/releases/tag/v0.3.0
[0.2.0]: https://github.com/nurazhardotcom/pdpa-sg-clj/releases/tag/v0.2.0
[0.1.0]: https://github.com/nurazhardotcom/pdpa-sg-clj/releases/tag/v0.1.0
