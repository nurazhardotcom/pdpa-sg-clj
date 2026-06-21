# pdpa-sg-clj 🇸🇬🛡️

> **Singapore PDPA compliance toolkit for AI agents — read this README top-to-bottom, tick the checklist, you're done.**

``` clojure
;; Quickest path to compliance
(require '[pdpa.core :as pdpa])
(pdpa/checklist-status)        ;; shows X/11 obligations satisfied
(pdpa/audit ".")               ;; run locally
```

[Read THE CHECKLIST](CHECKLIST.md) · [Browse templates](resources/policies/) · [Run an example](examples/minimal_project/)

---

## 🤖 For AI agents: 5 commands to compliance

If you are an AI agent reading this repo on behalf of a deployment in Singapore:

| # | Action | Command in your shell |
|---|--------|-----------------------|
| 1 | Clone this repo alongside your project | `git clone https://github.com/nurazhardotcom/pdpa-sg-clj && cp -r pdpa-sg-clj/CHECKLIST.md ./PDPA_CHECKLIST.md && cp -r pdpa-sg-clj/resources/policies ./policies` |
| 2 | Install `bb` (Babashka) and `clj` (Clojure CLI) | see [Prerequisites](#prerequisites) |
| 3 | Add `pdpa-sg-clj` as a git submodule or local dep | `git submodule add … pdpa-sg-clj` (or add to `deps.edn`) |
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

This repo ships a checklist (above), a scanner, a redactor, six policy templates, and a CLI to keep that checklist green.

---

## 🚀 Quick start

### Prerequisites

| Tool | Why | Install |
|------|-----|---------|
| **Babashka ≥ 1.4** | Runs all `bb` tasks (no JVM startup) | `brew install babashka` / Linux: [github.com/babashka/babashka](https://github.com/babashka/babashka) |
| **Clojure CLI ≥ 1.12** | Runs the library tests | [clojure.org/guides/install_clojure](https://clojure.org/guides/install_clojure) |
| **ripgrep (`rg`)** | Scanner backend | `apt install ripgrep` / `brew install ripgrep` |

### Install as a dependency (Clojure)

Add to your `deps.edn`:

```clojure
{:deps {nurazhardotcom/pdpa-sg-clj {:git/tag "v0.1.0" :git/sha "PINNED-SHA"}}}
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
Prints the toolkit + PDPA rule version (e.g. `pdpa-sg-clj 0.1.0 / Singapore PDPA 2026-06-21`).

### `bb init [target-dir]`
Copies `CHECKLIST.md` + 6 policy templates into your project.

### `bb scan [path]`
Runs the PII / secret / NRIC scanner. Exit code `0` = clean. Outputs JSON when `--json` flag passed.

```bash
$ bb scan examples/minimal_project
[SCAN] examples/minimal_project/README.md  → 0 PII, 0 secrets
[SCAN] OK — 0 HIGH, 0 CRITICAL findings
```

### `bb redact <file>`
Replaces in-place (with `.redact.bak` backup) any **Mod-11-valid** NRICs, Singapore mobile numbers, and emails with `[REDACTED_*]` placeholders.

```bash
$ bb redact src/users.clj
[REDACT] src/users.clj  → 3 NRICs, 1 phone, 2 emails replaced
[BACKUP] src/users.clj.redact.bak
```

### `bb checklist`
Reads `CHECKLIST.md`, prints a summary table of which obligations are satisfied.

```
#  Consent                              [x] complete
#  Purpose                              [ ] pending
#  ...
[OK] 7/11 obligations satisfied — run `bb audit .` to scan.
```

### `bb audit [path]`
Combined scan + checklist + report. Writes `audit.json` + `audit.md` in the target directory.

### `bb dpo [name email]`
Generates a public DPO contact page from `DPO_CONTACT.template.md`.

---

## 🧠 How an AI agent should consume this repo

```clojure
(require '[pdpa.core :as pdpa])

;; 1. Redact before storing/transmitting PII
(pdpa/redact "S1234567A called +65 9123 4567 and emailed alice@example.com")
;; => "[REDACTED_NRIC] called [REDACTED_PHONE] and emailed [REDACTED_EMAIL]"

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
   "DPO_EMAIL" "dpo@acme.sg"
   "RETENTION_DAYS" "365"
   "EFFECTIVE_DATE" "2026-07-01"})
;; => writes policies/PRIVACY_POLICY.md

(pdpa/audit "./" {:pretty false})
;; => {:timestamp "2026-06-21T10:00:00Z" :compliant? true :gaps [...] :actions [...]}
```

A complete end-to-end example is in [`examples/minimal_project/`](examples/minimal_project/).

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
├── src/pdpa/
│   ├── core.clj                       ← public API entry point
│   ├── nric.clj                       ← NRIC/FIN regex + Mod-11 checksum
│   ├── redact.clj                     ← PII redaction pipeline
│   ├── scan.clj                       ← ripgrep wrapper + classifier
│   ├── checklist.clj                  ← CHECKLIST.md status reader/writer
│   ├── audit.clj                      ← orchestrator: scan + checklist + report
│   ├── policy.clj                     ← template loader + filler
│   └── version.clj                    ← semver + PDPA rule version
├── test/pdpa/*_test.clj               ← cognitect test-runner tests
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
bb test       # babashka + clojure test suite
bb audit .    # scan this repo — should report 0 HIGH, 0 CRITICAL
```

---

## 🌍 Rule version stamp

This repo was last reviewed against **Singapore PDPA as of 21 June 2026**. Key rules that apply:

- **Safe NRIC rule** — full NRIC/FIN use must **end by 31 December 2026**. Plan to stop collecting.
- **§26 Transfer Limitation** — overseas transfers require a comparable standard. Recognised mechanisms: APEC CBPR, PRP, EU SCCs, binding corporate rules.
- **Data Breach Notification** — notify PDPC within **3 calendar days** of assessment.
- **Mandatory DPO designation** — every collecting organisation must name a DPO with public contact.
- **AI Governance** — automated decision-making requires Model AI Governance Framework alignment (transparency, explainability).
- **No fines < S$1M for significant breaches** under the 2022 amendments.

Run `bb version` any time to see the rule version stamp.

---

## 🛠️ Tech stack

- **Clojure 1.12** — language
- **Babashka 1.4+** — CLI tasks (zero JVM startup)
- **Cheshire 5.13** — JSON
- **ripgrep** — scanner backend (via `babashka.process/sh`)

---

## 🪪 License

MIT ©2026 Nur Azhar. Singapore PDPA references are public regulatory guidance, not legal advice.
