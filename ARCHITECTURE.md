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
│   nric.cljc  │ │  redact.cljc │ │   scan.clj   │ │ checklist.cljc│
│  ─ mod11     │ │  ─ pipeline  │ │  ─ rg --json │ │  ─ md parser │
│  ─ regex     │ │  ─ placehldrs│ │  ─ classify  │ │  ─ mark      │
└──────────────┘ └──────────────┘ └──────┬───────┘ └──────────────┘
                                        │ compiles
                                        ▼
                                 ┌──────────────┐ ┌──────────────┐
                                 │  rules.cljc  │ │  sarif.cljc  │
                                 │  ─ rule pack │ │  ─ SARIF out │
                                 │  ─ exports   │ │  ─ 2.1.0 log │
                                 └──────────────┘ └──────────────┘

┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│  audit.clj   │ │  policy.clj  │ │  version.cljc│ │  report.cljc │
│  ─ orchestr. │ │  ─ templates │ │  ─ rule stamp│ │  ─ md / html │
└──────────────┘ └──────────────┘ └──────────────┘ └──────────────┘
```

### Portable core / native adapters

The extraction keeps the public namespaces and command surface stable while
making the in-memory rules genuinely usable from ClojureScript. Portable
namespaces accept and return ordinary Clojure data; they do not walk a
filesystem or start a process.

```
caller text / scan lines
          │
          ▼
    pdpa.detect ───────────────┐
          │                    │
          ├─ pdpa.nric          │
          ├─ pdpa.redact        │
          ├─ pdpa.rules         │
          ├─ pdpa.sarif         │
          ├─ pdpa.checklist     │
          ├─ pdpa.report        │
          └─ pdpa.audit-context │
                               │
native pdpa.scan ── rg --json ─┘
native pdpa.audit/policy ── files, resources, process, clock adapter
```

| Namespace/file | Role | Platform boundary |
|---|---|---|
| `detect.cljc` | Compiles the shared rule pack, classifies line records, and builds the stable result | No process or filesystem; incomplete rule-pack coverage is explicit in `detect/coverage-boundary` |
| `audit_context.cljc` | Builds deterministic report context and preserves the historical scanner compliance signal | Timestamp is injected by the native caller; it does not certify legal compliance |
| `policy_template.cljc` | Replaces `<<KEY>>` placeholders in an in-memory template | Resource lookup and writes remain in `policy.clj` |
| `nric.cljc`, `redact.cljc`, `rules.cljc`, `sarif.cljc`, `checklist.cljc`, `report.cljc`, `version.cljc` | Shared text/data/report primitives | JSON is behind `pdpa.json`; clock is behind `pdpa.clock`; native I/O branches are explicit |
| `json.cljc`, `clock.cljc` | Small platform adapters | Cheshire/Java on the JVM and host JSON/Date in JavaScript |
| `scan.clj` | Native ripgrep adapter | Keeps `--no-ignore` and the existing `.git/`, `node_modules/`, `target/`, `out/`, `.cpcache/` skip globs; delegates to `detect/result` |
| `audit.clj`, `policy.clj`, `init.clj`, `shipit.clj`, `core.clj` | Native orchestration/API compatibility | Filesystem, process, resource, and CLI concerns stay here |

No browser `File`/Worker adapter is included in this track. Adding one would
be an independent adapter decision; the portable core is tested through
Node and does not claim browser filesystem coverage.

### Preserved behavior and explicit boundaries

- `scan/scan` still returns the same `{:findings :counts :clean?}` shape and
  still uses the native skip globs. Detection order and one-finding-per-line
  behavior are unchanged.
- Checklist marker semantics are intentionally unchanged: a marker makes only
  a following checkbox eligible; it is not retroactive. The bundled checklist
  places its markers after the section boxes, so this limitation remains
  visible rather than being silently “fixed.”
- The native audit/report wording is intentionally unchanged. Its
  `COMPLIANT`/zero-critical signal is a scanner gate, not a complete PDPA
  assessment; `audit-context/compliance-boundary` records that limitation.
- The historical `redact/phone-re` expression is preserved as-is, including
  its `;;`-comment/literal behavior; the phone-redaction correction is a
  separate follow-up, not part of this extraction.
- The portable detector covers the configured rule pack and Mod-11 validation
  only. Filesystem traversal, binary decoding, contextual PII inference, and
  legal compliance certification are outside its contract.

### Namespaces

| File | Purpose | Standalone deps |
|---|---|---|
| `detect.cljc` | In-memory rule matching, line classification, stable result aggregation | `pdpa.nric`, `pdpa.rules` |
| `audit_context.cljc` | Deterministic report context + explicit incomplete compliance boundary | `clojure.string` |
| `policy_template.cljc` | In-memory `<<KEY>>` template rendering | `clojure.string` |
| `nric.cljc` | NRIC/FIN regex + Mod-11 check-digit algorithm | `clojure.string` only |
| `redact.cljc` | Portable text pipeline plus native file/CLI branch | `pdpa.nric`; `clojure.java.io` under `:clj` |
| `rules.cljc` | Tool-independent rule pack (PII + secrets data) + gitleaks/JSON exports | `pdpa.json` (platform adapter) |
| `sarif.cljc` | Scan result → SARIF 2.1.0 map/string | `pdpa.json`, `pdpa.version` |
| `report.cljc` | Audit context → Markdown / standalone HTML executive report | `pdpa.clock`, `pdpa.version` |
| `checklist.cljc` | Portable marker/status transformation; native CLI branch | `clojure.string` |
| `scan.clj` | Wraps `rg --json`; delegates classification to `pdpa.detect` | `babashka.process`, `cheshire`, `pdpa.detect` |
| `audit.clj` | Orchestrator: scan → checklist → injected report context | native filesystem/process adapter + portable modules |
| `policy.clj` | Loads templates and delegates in-memory filling | `pdpa.policy-template`; `clojure.java.io` |
| `core.clj` | Public API — re-exports happy-path helpers | native compatibility layer |
| `version.cljc` | Single source of rule version + PDPA stamp | none |
| `json.cljc` / `clock.cljc` | JSON and clock platform boundaries | Cheshire/Java or host JS APIs |
| `bb/init.bb` (logical) | Copies CHECKLIST + templates into target dir | `babashka.fs`, `babashka.cli` |

### Key algorithms

#### `pdpa.nric/valid?` — Mod-11 for Singapore NRIC/FIN

The published Singapore NRIC check-digit algorithm (cross-checked
against independent validators; see CHANGELOG):

```
Weights [2 7 6 5 4 3 2] over the 7 digits; +4 offset for T/G only (S/F +0):
  idx = (weighted sum + offset) mod 11
  S/T check-char = "JZIHGFEDCBA"[idx]
  F/G check-char = "XWUTRQPNMLK"[idx]
M prefix (2022+): same 7-digit weights, +3 offset, own table
"XWUTRQPNJLK" (J at idx 8) — corroborated by independent validators.
Valid iff check-char matches the last character (upper-cased).
```

Implemented fully in `src/pdpa/nric.cljc`.

#### `pdpa.redact/redact-text` — pipeline

1. Find all NRIC-shaped matches via `\b[STFG]\d{7}[A-Z]\b` **and** `M\d{7}[A-Z]\b` (FINs)
2. Filter with `chksum-valid?` — **prevents false positives on hex strings**
3. Find the historical SG phone expression (its `;;` comment/literal
   behavior is preserved; see the follow-up contract)
4. Find emails (RFC 5322 simplified)
5. Replace with `[REDACTED_NRIC]`, `[REDACTED_PHONE]`, `[REDACTED_EMAIL]`

#### `pdpa.detect/classify` — severity mapping

| Pattern | Severity | Why |
|---|---|---|
| NRIC with valid Mod-11 | **CRITICAL** | Live personal data leak |
| SG mobile with country code | **CRITICAL** | Live personal data leak |
| `*_KEY=…` with hex ≥ 32 chars | **HIGH** | Likely credential |
| `-----BEGIN … KEY-----` | **HIGH** | Private key |
| `[A-Z_]+SECRET_KEY` literal | **MEDIUM** | Pattern risk, may be placeholder |
| Email address | **LOW** | May be contact, may be test |
| `password=` with non-empty value | **MEDIUM** | Risk even if test |

#### `pdpa.checklist/auto-tick`

For each obligation, the namespace defines a verifier against the supplied
scan counts/evidence. The implementation is intentionally unchanged during
this extraction:

```
(verify :purpose (fn [ctx] (zero? (get-in ctx [:scan :counts :medium]))))
(verify :protection (fn [ctx]
                      (and (zero? (get-in ctx [:scan :counts :critical]))
                           (zero? (get-in ctx [:scan :counts :high]))
                           (some #(= "SECURITY_HARDENING" %) (:evidence ctx)))))
```

The CHECKLIST.md file uses HTML-comment markers like
`<!-- agent:verify-protection -->`. A marker arms only the next matching
checkbox after it; it does not tick earlier boxes. This is a characterized
preserved limitation, not a corrected behavior in the portability track.

## Date 21 June 2026 — what we encode

- `bb version` banner: `pdpa-sg-clj 0.1.0 / Singapore PDPA 2026-06-21`
- The "Safe NRIC deadline" is wired into the scan output as a **warning**, not an auto-fail (the code still allows NRICs as input because some legacy systems need them, but the warning is loud).
- The Data Breach rule uses the **3 calendar days** value.
- §26 cert list is in `resources/policies/DATA_TRANSFER_AGREEMENT.template.md` (not in code, so we don't have to keep a clojure list in sync with PDPC's evolving cert set).

## Testing strategy

| Test file | Cases |
|---|---|
| `nric_test.cljc` | valid S-series redacts, valid M-series FIN redacts, structural match with bad checksum does NOT redact (false-positive guard), valid NRIC survives round-trip |
| `redact_test.cljc` | NRIC/email redaction, historical phone-pattern characterization, multi-PII in same string, idempotency |
| `rules_test.cljc`, `sarif_test.cljc`, `report_test.cljc` | portable rule compilation, JSON/SARIF adapters, deterministic report output |
| `checklist_test.cljc` | portable status/marker transformation; marker-placement limitation is characterized |
| `portable_test.cljc` + `portable_fixtures.cljc` | shared fictional parity cases, incomplete detector/compliance boundaries, injected timestamps, template rendering |
| `scan_test.clj` | native rg process/filesystem behavior, skip globs, path exclusions, and exit-shape smoke coverage |
| `clojure -M:test:cljs` | the portable `.cljc` namespaces under Node, without native scan/filesystem tests |

## Why Babylon splits (nric/redact/scan/checklist/policy/audit/core/version)?

- **Testability** — each namespace has a small, well-defined interface
- **Single responsibility** — the scanner doesn't know about templates; the redactor doesn't know about the scanner
- **REPL friendliness** — you can `(require '[pdpa.nric :as nric])` standalone to check a regex with no startup cost
- **Composability for AI agents** — A code-generation agent can pick the right namespace based on the task ("redact" → `pdpa.redact`, "audit" → `pdpa.audit`)

## Future work

- [x] `gitleaks` interop — done as one-way export (`bb export-rules --format gitleaks`);
  a live `--backend gitleaks` merge remains optional
- [ ] Correct checklist marker placement/semantics in a separate behavior
  change; the current placement limitation is intentionally preserved here
- [ ] Replace native scanner-gate compliance wording with a separately reviewed
  legal/compliance vocabulary; the current wording is intentionally preserved
- [ ] Add a browser `File`/Worker adapter only with independent, offline tests;
  no Reagent dependency is part of this track
- [ ] Differential privacy layer for analytics (Obligation 6)
- [x] CI workflow template (GitHub Actions YAML) for `bb audit` on every push
  — done: `.github/workflows/ci.yml` (test job + SARIF scan job)
- [x] Pre-commit hook (`.git/hooks/pre-commit`) that blocks commits containing raw NRICs
  — done: `scripts/git-hooks/pre-commit` + `bb install-hook`
- [ ] Native PDF emitter (today: HTML report + pandoc/headless-chrome conversion)
- [ ] Publish `pdpa-vscode` extension to the VS Code Marketplace (scaffold ready)
