# Changelog

All notable changes to **pdpa-sg-clj** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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
  ISO 27001 / MAS TRM filings.
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
- `CHANGELOG.md` documenting release history for hiring managers and recruiters
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
