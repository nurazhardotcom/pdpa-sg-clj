# Changelog

All notable changes to **pdpa-sg-clj** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Fixed
- `pdpa.scan/scan` silently returned zero findings: `parse-rg-match`
  invoked the rg-JSON `lines` map as a function, dropping every match.
  Now extracts `data.lines.text`. Caught by new tests that assert real
  counts instead of result shape.
- `pdpa:ignore` line-level exclusion marker for intentional documentation
  examples (shell `# pdpa:ignore` or Markdown `<!-- pdpa:ignore -->`);
  exclusion is per-line, never per-file.

### Added
- Rule `:id` included in every finding map (machine-readable key
  alongside the human label).
- `AI_DISCLOSURE.md` (AI assistance note, same convention as
  `idira-audit-clj`).

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

[Unreleased]: https://gitlab.com/nurazhar/pdpa-sg-clj/-/compare/v0.2.0...main
[0.2.0]: https://gitlab.com/nurazhar/pdpa-sg-clj/-/releases/v0.2.0
[0.1.0]: https://gitlab.com/nurazhar/pdpa-sg-clj/-/releases/v0.1.0
