# pdpa-sg-clj 🇸🇬🛡️

> **Status:** Active — maintained. See [AI_DISCLOSURE.md](AI_DISCLOSURE.md).

[![GitHub tag](https://img.shields.io/github/v/tag/nurazhardotcom/pdpa-sg-clj)](https://github.com/nurazhardotcom/pdpa-sg-clj/releases)
[![CI](https://github.com/nurazhardotcom/pdpa-sg-clj/actions/workflows/ci.yml/badge.svg)](https://github.com/nurazhardotcom/pdpa-sg-clj/actions)
[![license: MIT](https://img.shields.io/badge/license-MIT-blue)](./LICENSE)

`pdpa-sg-clj v0.3.0` · Last verified 2026-09-06 · MIT ©2026 Nur Azhar

> **Singapore PDPA compliance toolkit for AI agents — read this README top-to-bottom, tick the checklist, you're done.**

``` clojure
;; Quickest path to compliance
(require '[pdpa.core :as pdpa])
(pdpa/checklist-status)        ;; shows X/11 obligations satisfied
(pdpa/audit ".")               ;; run locally
```

[Read THE CHECKLIST](CHECKLIST.md) · [Browse templates](resources/policies/) · [Run an example](examples/minimal_project/)

---

## 📖 In plain English (no tech background needed)

**What is this?** A free toolkit that helps any organisation in Singapore
follow the **PDPA** — Singapore's law on collecting and protecting
customers' personal data (names, NRICs, phone numbers, emails).

**Why should you care?** Breaking the rules can mean public orders to fix
things plus fines of **over S$1 million** for serious breaches. If customer
data leaks, you must notify the authorities **within 3 days**. This toolkit
helps you find risks *before* that happens.

**What do you get?**
- A **checklist** of all 11 legal duties, in plain steps ([CHECKLIST.md](CHECKLIST.md))
- **Ready-to-fill policy documents** (privacy policy, consent form, breach plan…)
- A **scanner** your developer runs: it finds leaked personal data and
  passwords in your systems and produces a **report card** for your auditor

**If you are not technical, do this:**
1. Open [CHECKLIST.md](CHECKLIST.md) and read it top to bottom (~20 minutes).
2. Copy the [policy templates](resources/policies/) and fill in your
   company name, contact email, and how long you keep data.
3. Ask your developer (or IT vendor) to run the scan and hand you the
   `audit.html` report — that is the file your auditor files with
   ISO 27001 paperwork.

## ❓ FAQ

| Question | Answer |
|---|---|
| Is this legal advice? | **No.** It is practical guidance plus tooling. For legal certainty, consult a lawyer. |
| Do I need programmers to use it? | For the checklist and documents, no. For the automatic scan, yes — one developer, ~30 minutes. |
| What do I give my auditor? | The `audit.html` report (convert to PDF in one step — see [Export formats](#export-formats-for-auditors)). |
| What does it cost? | Nothing. MIT-licensed, free for commercial use. |
| What is … (jargon buster) | **PII** = personal data like names/NRICs · **NRIC/FIN** = Singapore ID numbers · **DPO** = the Data Protection Officer you must appoint · **Redaction** = masking data (`S1234567A` → `[REDACTED]`) · **SARIF** = a standard result file auditors' tools can read · **CI/CD** = a robot that re-checks your code on every change |

---

## 🤖 For AI agents: 5 commands to compliance

If you are an AI agent reading this repo on behalf of a deployment in Singapore:

| # | Action | Command in your shell |
|---|--------|-----------------------|
| 1 | Clone this repo alongside your project | `git clone https://github.com/nurazhardotcom/pdpa-sg-clj && cp -r pdpa-sg-clj/CHECKLIST.md ./PDPA_CHECKLIST.md && cp -r pdpa-sg-clj/resources/policies ./policies` |
| 2 | Install `bb` (Babashka) and `clj` (Clojure CLI) | see [Prerequisites](#prerequisites) |
| 3 | Add `pdpa-sg-clj` as a git submodule or local dep | `git submodule add https://github.com/nurazhardotcom/pdpa-sg-clj.git pdpa-sg-clj` (or add to `deps.edn`) |
| 4 | Run the local audit | `cd pdpa-sg-clj && bb audit ../your-project` |
| 5 | Fill in templates and tick the checklist | open `PDPA_CHECKLIST.md`, replace `<<ORG_NAME>>` placeholders in `policies/PRIVACY_POLICY.md`, mark each `[ ]` → `[x]` |

**Total time: ~30 minutes** for a small-to-medium project.

---

## 📋 The 11 Singapore PDPA obligations

Singapore's **Personal Data Protection Act 2012** (most recently revised through 2026 amendments) puts **11 obligations** on every organisation that collects, uses, or discloses personal data within Singapore:

| # | Obligation | One-line definition |
|---|---|---|
| **1** | Consent | Get clear consent before collection |
| **2** | Purpose Limitation | Use data only for the stated purpose |
| **3** | Notification | Tell individuals what you do with their data |
| **4** | Accuracy | Keep data accurate and complete |
| **5** | Protection | Implement reasonable security |
| **6** | Retention Limitation | Delete data when no longer needed |
| **7** | Transfer Limitation (§26) | Overseas transfers must be to a comparable-standard jurisdiction |
| **8** | Access & Correction | Honour data-subject requests (DSRs) |
| **9** | Withdrawal of Consent | Support consent withdrawal as easily as consent was given |
| **10** | Data Breach Notification | Notify PDPC within **3 calendar days** if ≥500 affected OR significant harm |
| **11** | Accountability / DPO | Designate a Data Protection Officer and publish contact |

Plus the **Safe NRIC rule** — full NRIC/FIN collection, use or disclosure **must cease by 31 December 2026** unless you have explicit PDPC approval. After this date, the practical answer is: **do not collect NRICs at all** unless legally required.

This repo ships a checklist (above), a scanner, a redactor, eight policy templates, and a CLI to keep that checklist green.

---

## 🚀 Quick start

### Prerequisites

| Tool | Why | Install |
|------|-----|---------|
| **Babashka ≥ 1.4** | Runs all `bb` tasks (no JVM startup) | `brew install babashka` / Linux: [babashka.org](https://babashka.org) |
| **Clojure CLI ≥ 1.12** | Runs the library/native tests | [clojure.org/guides/install_clojure](https://clojure.org/guides/install_clojure) |
| **Node.js ≥ 18** | Runs the portable ClojureScript test bundle | [nodejs.org](https://nodejs.org/) |
| **ripgrep (`rg`)** | Scanner backend | `apt install ripgrep` / `brew install ripgrep` |

### Install as a dependency (Clojure)

Add to your `deps.edn`:

```clojure
{:deps {pdpa/pdpa-sg-clj {:git/url "https://github.com/nurazhardotcom/pdpa-sg-clj.git"
                           :git/tag "v0.3.0"}}}
```

Or as a local checkout:

```clojure
{:deps {org.clojure/clojure {:mvn/version "1.12.0"}
        pdpa/pdpa-sg-clj    {:local/root "../pdpa-sg-clj"}}}
```

### Install as a CLI

```bash
git clone https://github.com/nurazhardotcom/pdpa-sg-clj
cd pdpa-sg-clj
bb about         # prints version banner
bb init ./your-project/
```

> To use the tasks outside this repo, copy `bb.edn` into your project and adjust the `(:require …)` lines, or include this repo as a git submodule + use `clj -X:test`.

---

## 🔧 CLI usage

All commands operate from this directory (or set `BB_PROJECT_DIR`).

### `bb about`
Prints the toolkit + PDPA rule version (e.g. `pdpa-sg-clj 0.3.0 / Singapore PDPA 2026-06-21`).

### `bb init [target-dir]`
Copies `CHECKLIST.md` + 8 policy templates into your project.

### `bb scan [path] [--format text|json|sarif|quickfix] [--out FILE]`
Runs the PII / secret / NRIC scanner. Exit code `0` = clean.

```bash
$ bb scan examples/minimal_project
[SCAN] examples/minimal_project/README.md  → 0 PII, 0 secrets
[SCAN] OK — 0 HIGH, 0 CRITICAL findings
```

Machine-readable output for gates and editors:

```bash
bb scan . --format json                  # full result map on stdout
bb scan . --format sarif --out audit.sarif   # SARIF 2.1.0 for GitHub code scanning
bb scan . --format quickfix              # path:line: [SEV] label for Vim/VS Code
```

### Secret detection (DevSecOps)

Beyond PDPA PII (NRIC/FIN, `+65` phones, emails), the scanner ships a
20-rule pack covering AWS keys + session tokens, JWTs, CyberArk Conjur
keys, Slack / OpenAI / Anthropic / GCP / Azure credentials, Stripe,
GitHub tokens, PEM keys, and hardcoded passwords/secrets. Rules live as
tool-independent data in `src/pdpa/rules.cljc` — the same pack drives the
built-in scanner, SARIF export, and external tools:

```bash
bb export-rules --format gitleaks --out gitleaks.toml  # run the pack inside gitleaks
bb export-rules --format json                          # portable rules.json
```

### `bb redact <file>`
Runs the in-place redactor (with `.redact.bak` backup) over its configured PII patterns, including **Mod-11-valid** NRICs and emails. The historical phone-expression limitation is called out below.

```bash
$ bb redact src/users.clj
[REDACT] src/users.clj  → 3 NRICs, 0 phones, 2 emails replaced
[BACKUP] src/users.clj.redact.bak
```

> **Preserved redaction limitation:** the historical phone expression uses
> `;;` text where a regex comment was intended, so ordinary phone fixtures are
> not currently replaced. This behavior is deliberately unchanged by the
> portability extraction; correcting it is a separate follow-up.

### `bb checklist`
Reads `CHECKLIST.md`, prints a summary table of which obligations are satisfied.

```
#  Consent                              [x] complete
#  Purpose                              [ ] pending
#  ...
[OK] 7/11 obligations satisfied — run `bb audit .` to scan.
```

### `bb audit [path] [--format text|json|sarif|html|md] [--out FILE]`
Combined scan + checklist + report. Prints to stdout unless `--out` is given.

```bash
bb audit . --format json --out audit.json    # CI/CD gate input
bb audit . --format sarif --out audit.sarif  # GitHub code scanning / VS Code SARIF Viewer
bb audit . --format html --out audit.html    # executive report for auditors
bb audit . --format md --out audit.md        # Markdown report
```

### Export formats (for auditors)

JSON gates CI; humans file paper. The executive HTML report carries the
verdict, finding counts, published-evidence table, and rule stamp —
convert it to PDF for ISO 27001 filings:

```bash
bb audit . --format html --out audit.html
pandoc audit.md -o audit.pdf                                   # via Markdown, or
google-chrome --headless --print-to-pdf=audit.pdf audit.html   # straight from HTML
```

SARIF uploads straight into GitHub code scanning (wired in
`.github/workflows/ci.yml` via `upload-sarif`).

### `bb dpo [name email]`
Generates a public DPO contact page from `DPO_CONTACT.template.md`.

---

## 🧠 How an AI agent should consume this repo

```clojure
(require '[pdpa.core :as pdpa])

;; 1. Redact before storing/transmitting PII
(pdpa/redact "S0100000D emailed alice@acme.test") ; pdpa:ignore — fictional example
;; => "[REDACTED_NRIC] emailed [REDACTED_EMAIL]"

;; 2. Generate the checklist status programmatically
(pdpa/checklist-status)
;; => {:consent {:status "PENDING" :evidence []}
;;     :purpose {:status "PENDING" :evidence []}
;;     :notification {:status "PENDING" :evidence []}
;;     ...}

;; 3. Run the scanner
(pdpa/scan "./" {:json true})
;; => {:findings [...] :severity-counts {:critical 0 :high 0 :medium 3 :low 7}}

;; 4. Fill in a policy template with org-specific values
(pdpa/fill-policy "PRIVACY_POLICY.template.md"
  {"ORG_NAME" "Acme Pte Ltd"
   "DPO_EMAIL" "dpo@acme.sg"  ; pdpa:ignore — fictional placeholder
   "RETENTION_DAYS" "365"
   "EFFECTIVE_DATE" "2026-07-01"})
;; => writes policies/PRIVACY_POLICY.md

(pdpa/audit "./" {:pretty false})
;; => {:timestamp "2026-06-21T10:00:00Z" :compliant? true :gaps [...] :actions [...]}
```

### Portable API (no filesystem required)

Clojure and ClojureScript callers can use the same in-memory primitives:

```clojure
(require '[pdpa.detect :as detect]
         '[pdpa.audit-context :as context])

(def scan-result
  (detect/result [{:path "input.txt"
                   :text "User S0100000D"  ; pdpa:ignore — fictional example
                   :line 1}]
                 {}))

(def report-context
  (context/build {:path "input.txt"
                  :scan-result scan-result
                  :evidence []
                  :compliant? (:clean? scan-result)
                  :timestamp "2026-09-25T00:00:00Z"}))
```

`pdpa.detect` covers the configured rule pack only. A clean result is a
scanner signal, not a complete legal compliance determination. Native
filesystem/process commands remain in `pdpa.scan`, `pdpa.audit`, and the
Babashka tasks.

A complete end-to-end example is in [`examples/minimal_project/`](examples/minimal_project/).

---

## ⚡ Real-time feedback (editors)

Pre-commit scanning (`bb install-hook`) is the last line of defence —
these give warnings *before* `git commit`:

- **VS Code, zero code:** copy `editors/vscode/tasks.json` into your
  project's `.vscode/`, run the `pdpa: scan workspace` task
  (Problems panel), and open `audit.sarif` with the
  [SARIF Viewer](https://marketplace.visualstudio.com/items?itemName=MS-SarifVSCode.sarif-viewer)
  (see `editors/vscode/extensions.json`).
- **VS Code extension:** `editors/pdpa-vscode/` is a full extension
  (`bb scan --format sarif` → inline diagnostics, rescan on save).
  Publish with `vsce publish` (publisher: `nurazhardotcom`).
- **Neovim:** `editors/nvim/pdpa.lua` provides `:PdpaScan [path]`,
  pushing findings to the quickfix list.

---

## 📚 What's in this repo

```
pdpa-sg-clj/
├── README.md                          ← you are here
├── CHECKLIST.md                       ← the master 11-obligation checklist
├── ARCHITECTURE.md                    ← design notes for contributors
├── LICENSE                            ← MIT
├── deps.edn                           ← Clojure deps
├── bb.edn                             ← Babashka tasks (CLI surface)
├── .github/workflows/ci.yml            ← GitHub Actions CI (test + SARIF scan)
├── editors/
│   ├── vscode/tasks.json                ← VS Code task + problemMatcher
│   ├── nvim/pdpa.lua                    ← Neovim :PdpaScan quickfix
│   └── pdpa-vscode/                     ← full VS Code extension source
├── src/pdpa/
│   ├── core.clj                       ← native public API entry point
│   ├── detect.cljc                    ← portable in-memory classifier/result
│   ├── audit_context.cljc              ← portable report context/boundary
│   ├── policy_template.cljc            ← portable template filler
│   ├── json.cljc / clock.cljc          ← platform adapters
│   ├── nric.cljc                       ← NRIC/FIN regex + Mod-11 checksum
│   ├── redact.cljc                     ← portable text pipeline + native I/O branch
│   ├── rules.cljc                      ← tool-independent rule pack + exports
│   ├── scan.clj                        ← native ripgrep adapter → detect
│   ├── sarif.cljc                      ← SARIF 2.1.0 export
│   ├── report.cljc                     ← Markdown/HTML auditor reports
│   ├── checklist.cljc                  ← portable status/marker transform
│   ├── audit.clj                       ← native orchestrator
│   ├── policy.clj                      ← native resource/file template adapter
│   └── version.cljc                    ← semver + PDPA rule version
├── test/pdpa/*_test.cljc               ← portable tests; scan_test.clj stays native
├── test/pdpa/test_runner.cljs            ← Node ClojureScript test entry point
├── test/pdpa/portable_fixtures.cljc    ← shared fictional parity fixtures
├── resources/policies/
│   ├── PRIVACY_POLICY.template.md
│   ├── DPO_CONTACT.template.md
│   ├── CONSENT_FORM.template.md
│   ├── DATA_BREACH_RESPONSE.template.md   ← 3-day PDPC notification
│   ├── DATA_TRANSFER_AGREEMENT.template.md ← §26 overseas-standard
│   └── DPIA.template.md                   ← Data Protection Impact Assessment
└── examples/minimal_project/
```

---

## ✅ Verify the toolkit works on itself

```bash
cd pdpa-sg-clj
bb test           # Babashka/native + portable JVM tests
clojure -M:test   # JVM test runner
bb test:cljs      # same portable suite compiled and run on Node
clj-kondo --lint src test scripts --fail-level warning
bb scan src/pdpa  # bounded native scan; vendor/build skip globs stay enabled
```

`bb audit .` remains the native end-to-end command when a full local audit is
wanted. It is intentionally separate from the portable test boundary; the
portable suite does not claim filesystem or legal-compliance coverage.

---

## 🌍 Rule version stamp

This repo was last reviewed against **Singapore PDPA as of 21 June 2026**. Key rules that apply:

- **Safe NRIC rule** — full NRIC/FIN use must **end by 31 December 2026**. Plan to stop collecting.
- **§26 Transfer Limitation** — overseas transfers require a comparable standard. Recognised mechanisms: APEC CBPR, PRP, EU SCCs, binding corporate rules.
- **Data Breach Notification** — notify PDPC within **3 calendar days** of assessment.
- **Mandatory DPO designation** — every collecting organisation must name a DPO with public contact.
- **AI Governance** — automated decision-making requires Model AI Governance Framework alignment (transparency, explainability).
- **No fines < S$1M for significant breaches** under the 2022 amendments.

Run `bb about` any time to see the rule version stamp.

---

## 🛠️ Tech stack

- **Clojure 1.12** — native JVM/CLI language
- **ClojureScript 1.12** — portable in-memory core, compiled/tested on Node
- **Babashka 1.4+** — CLI tasks (zero JVM startup)
- **Cheshire 5.13 / host JSON** — platform JSON adapter
- **ripgrep** — native scanner backend (via `babashka.process/sh`)

### Portable core and native boundary

The rule/data/report primitives are now `.cljc` and are exercised by the
same fictional fixtures on the JVM and Node:

- `pdpa.detect` matches caller-supplied line records and builds the stable
  `{:findings :counts :clean?}` result.
- `pdpa.audit-context` accepts an injected report timestamp and exposes an
  explicit incomplete-compliance boundary.
- `pdpa.policy-template` fills an in-memory template; resource and file I/O
  remain in the native `pdpa.policy` adapter.
- `pdpa.json` and `pdpa.clock` isolate platform JSON and time behavior.
- `pdpa.scan` remains the native `rg --json` adapter and delegates detection
  to `pdpa.detect/result`; its existing skip globs are unchanged.

ClojureScript does not receive filesystem traversal, process invocation, or
resource loading in this track. No browser `File`/Worker adapter or Reagent
dependency is claimed. The detector and compliance signal are intentionally
incomplete: see `detect/coverage-boundary` and
`audit-context/compliance-boundary` in the source and `ARCHITECTURE.md`.

---

## 🪪 License

MIT ©2026 Nur Azhar · contact via https://nurazhar.com. Singapore PDPA references are public regulatory guidance, not legal advice.
