# pdpa-sg-clj — Architecture

## Goals

1. **AI-agent consumable** — An LLM reading the README + CHECKLIST in one pass should be able to drive the toolkit to compliance without additional documentation.
2. **One-shot compliance** — `bb audit <project>` reports whether the project is compliant and exactly what is missing.
3. **Zero external dependencies for the CLI** — only `rg` + bash are required for the BB tasks; Clojure deps only for the library API.

## Layered design

```
┌─────────────────────────────────────────────────────────────┐
│  CLI surface (bb.edn tasks)                                  │
│  about, init, scan, redact, checklist, audit, export-rules, │
│  dpo, test, shipit, blog-scan, install-hook                 │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│  Public API (src/pdpa/core.clj)                              │
│  redact, scan, checklist-status, audit, fill-policy,        │
│  to-sarif, audit-report, rule-pack, version                 │
└──────────────────────┬──────────────────────────────────────┘
                       │
        ┌──────────────┼──────────────┬──────────────────┐
        ▼              ▼              ▼                  ▼
┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│   nric.clj   │ │  redact.clj  │ │   scan.clj   │ │ checklist.clj│
│  ─ mod11     │ │  ─ pipeline  │ │  ─ rg --json │ │  ─ md parser │
│  ─ regex     │ │  ─ placehldrs│ │  ─ classify  │ │  ─ mark      │
└──────────────┘ └──────────────┘ └──────┬───────┘ └──────────────┘
                                        │ compiles
                                        ▼
                                 ┌──────────────┐ ┌──────────────┐
                                 │  rules.clj   │ │  sarif.clj   │
                                 │  ─ rule pack │ │  ─ SARIF out │
                                 │  ─ exports   │ │  ─ 2.1.0 log │
                                 └──────────────┘ └──────────────┘

┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│  audit.clj   │ │  policy.clj  │ │  version.clj │ │  report.clj  │
│  ─ orchestr. │ │  ─ templates │ │  ─ rule stamp│ │  ─ md / html │
└──────────────┘ └──────────────┘ └──────────────┘ └──────────────┘
```

### Namespaces

| File | Purpose | Standalone deps |
|---|---|---|
| `nric.clj` | NRIC/FIN regex + Mod-11 check-digit algorithm | `clojure.string` only |
| `redact.clj` | Pipeline: text → (NRIC/phone/email/name) → `[REDACTED_*]` | `pdpa.nric` |
| `scan.clj` | Wraps `rg --json`, classifies via compiled `pdpa.rules` pack | `babashka.process`, `cheshire`, `pdpa.rules` |
| `rules.clj` | Tool-independent rule pack (PII + secrets data) + gitleaks/JSON exports | `clojure.string`, `cheshire` (export only) |
| `sarif.clj` | Scan result → SARIF 2.1.0 log | `cheshire`, `pdpa.version` |
| `report.clj` | Audit context → Markdown / standalone HTML executive report | `clojure.string`, `pdpa.version` |
| `checklist.clj` | Parses + writes `CHECKLIST.md`; applies auto-ticks | `clojure.string` only |
| `audit.clj` | Orchestrator: scan → checklist → report | all of above |
| `policy.clj` | Loads templates, fills `<<ORG_NAME>>` markup | only `clojure.string` |
| `core.clj` | Public API — re-exports happy-path helpers | everything |
| `version.clj` | Single source of rule version + PDPA stamp | none |
| `bb/init.bb` (logical) | Copies CHECKLIST + templates into target dir | `babashka.fs`, `babashka.cli` |

### Key algorithms

#### `pdpa.nric/checksum-valid?` — Mod-11 for Singapore NRIC/FIN

The official Singapore NRIC check-digit algorithm:

```
For NRIC S/T prefixes (citizens/PRs):
  weights = [2 7 6 5 4 3 2]
  sum = Σ (digit_i × weight_i) for i = 0..6
  remainder = sum mod 11
  check-char = "JZIHGFEDCBA" [remainder]
  Valid iff check-char matches last character (after upper-casing, applying prefix-specific logic)

For FIN M/F/G prefixes (foreigners):
  weights = [2 7 6 5 4 3 2 1]  (extra weight for prefix)
  sum = Σ ...
```

Implemented fully in `src/pdpa/nric.clj`.

#### `pdpa.redact/redact-text` — pipeline

1. Find all NRIC-shaped matches via `\b[STFG]\d{7}[A-Z]\b` **and** `M\d{7}[A-Z]\b` (FINs)
2. Filter with `chksum-valid?` — **prevents false positives on hex strings**
3. Find SG mobile numbers `\b(?:[89]\d{7}|9\d{3}\s?\d{4})\b`
4. Find emails (RFC 5322 simplified)
5. Replace with `[REDACTED_NRIC]`, `[REDACTED_PHONE]`, `[REDACTED_EMAIL]`

#### `pdpa.scan/classify` — severity mapping

| Pattern | Severity | Why |
|---|---|---|
| NRIC with valid Mod-11 | **CRITICAL** | Live personal data leak |
| SG mobile with country code | **CRITICAL** | Live personal data leak |
| `*_KEY=…` with hex ≥ 32 chars | **HIGH** | Likely credential |
| `-----BEGIN … KEY-----` | **HIGH** | Private key |
| `[A-Z_]+SECRET_KEY` literal | **MEDIUM** | Pattern risk, may be placeholder |
| Email address | **LOW** | May be contact, may be test |
| `password=` with non-empty value | **MEDIUM** | Risk even if test |

#### `pdpa.checklist/auto-tick?`

For each obligation, defines a `verify-fn` returning `true` when auto-tick is justified:

```
(verify :protection (fn [ctx] (zero? (get-in ctx [:scan :high]))))
(verify :purpose    (fn [ctx] (zero? (get-in ctx [:scan :medium]))))
(verify :breach     (fn [ctx] (every? (set (:checklist ctx))
                                       [:plan-published :drill-completed])))
```

The CHECKLIST.md file uses hidden HTML-comment markers like `<!-- agent:verify-protection -->` to map obligations to verifier functions.

## Date 21 June 2026 — what we encode

- `bb version` banner: `pdpa-sg-clj 0.1.0 / Singapore PDPA 2026-06-21`
- The "Safe NRIC deadline" is wired into the scan output as a **warning**, not an auto-fail (the code still allows NRICs as input because some legacy systems need them, but the warning is loud).
- The Data Breach rule uses the **3 calendar days** value.
- §26 cert list is in `resources/policies/DATA_TRANSFER_AGREEMENT.template.md` (not in code, so we don't have to keep a clojure list in sync with PDPC's evolving cert set).

## Testing strategy

| Test file | Cases |
|---|---|
| `nric_test.clj` | valid S-series redacts, valid M-series FIN redacts, structural match with bad checksum does NOT redact (false-positive guard), valid NRIC survives round-trip |
| `redact_test.clj` | NRIC redaction, phone redaction, SG mobile vs landline, email redaction, multi-PII in same string, idempotency |
| `scan_test.clj` | rg `--json` output parses, severity classification, exit code 0 = clean flag |
| `checklist_test.clj` | parses CHECKLIST.md, applies auto-ticks correctly, leaves manual boxes alone |

## Why Babylon splits (nric/redact/scan/checklist/policy/audit/core/version)?

- **Testability** — each namespace has a small, well-defined interface
- **Single responsibility** — the scanner doesn't know about templates; the redactor doesn't know about the scanner
- **REPL friendliness** — you can `(require '[pdpa.nric :as nric])` standalone to check a regex with no startup cost
- **Composability for AI agents** — A code-generation agent can pick the right namespace based on the task ("redact" → `pdpa.redact`, "audit" → `pdpa.audit`)

## Future work

- [x] `gitleaks` interop — done as one-way export (`bb export-rules --format gitleaks`);
  a live `--backend gitleaks` merge remains optional
- [ ] Differential privacy layer for analytics (Obligation 6)
- [x] CI workflow template (GitHub Actions YAML) for `bb audit` on every push
  — done: `.github/workflows/ci.yml` (test job + SARIF scan job)
- [x] Pre-commit hook (`.git/hooks/pre-commit`) that blocks commits containing raw NRICs
  — done: `scripts/git-hooks/pre-commit` + `bb install-hook`
- [ ] Native PDF emitter (today: HTML report + pandoc/headless-chrome conversion)
- [ ] Publish `pdpa-vscode` extension to the VS Code Marketplace (scaffold ready)
