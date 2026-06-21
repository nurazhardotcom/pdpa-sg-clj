# Singapore PDPA Compliance Checklist

> **The single source of truth.** Tick each box as you complete it. Boxes marked with `<!-- agent:verify-* -->` are auto-ticked by `bb checklist` when its scanner passes.

**Last rule update:** 21 June 2026 — runs against Singapore PDPA (revised through 2026 amendments).
**Toolkit version:** pdpa-sg-clj (run `bb version` for stamp)

---

## Summary

```
[X] auto-tick when scanner passes
[ ] manual action required
```

| # | Obligation | Status | Auto? |
|---|---|---|---|
| 1 | Consent | [ ] | manual |
| 2 | Purpose Limitation | [ ] | manual + scan |
| 3 | Notification | [ ] | manual |
| 4 | Accuracy | [ ] | manual |
| 5 | Protection | [ ] | partial + scan |
| 6 | Retention Limitation | [ ] | manual |
| 7 | Transfer Limitation (§26) | [ ] | manual |
| 8 | Access & Correction (DSR) | [ ] | manual |
| 9 | Withdrawal of Consent | [ ] | manual |
| 10 | Data Breach Notification | [ ] | manual |
| 11 | Accountability / DPO | [ ] | manual |

---

## 1. Consent Obligation

> No collection / use / disclosure of personal data without the individual's consent — or another lawful basis.

- [ ] A **consent form** is in place at every collection point — see `policies/CONSENT_FORM.md`
- [ ] Consent records store: timestamp, scope, opt-in text, IP, version of policy
- [ ] Withdrawal of consent is **as easy as** opt-in (same channel, no friction)
- [ ] Exceptions document (e.g. legitimate interests, legal obligation) is reviewed annually

<!-- agent:verify-consent -->

---

## 2. Purpose Limitation Obligation

> Collect for a stated purpose; don't repurpose without fresh consent.

- [ ] Every data field in your schema has a **purpose statement** (table comment or schema doc)
- [ ] You do **not** train ML/AI models on user data without explicit consent — see **AI Governance** section below
- [ ] If you depend on scraped / third-party data, document its provenance

<!-- agent:verify-purpose -->

---

## 3. Notification Obligation

> Tell individuals what you do with their data — at the point of collection.

- [ ] **Privacy Policy** is published and discoverable from every entry point — see `policies/PRIVACY_POLICY.md`
- [ ] Notification happens **at or before** collection (banner / form footer)
- [ ] Notification lists: identity of collector, purpose, recipients, retention, DPO contact, DSR channels

<!-- agent:verify-notification -->

---

## 4. Accuracy Obligation

> Personal data must be accurate and complete if it affects decisions.

- [ ] Data-validation middleware enforced at write (e.g. `ValidateEmail`, `ValidatePhoneSG`)
- [ ] Correction requests are honoured within **30 calendar days**
- [ ] Stale or unverified data is flagged after **180 days** for review

---

## 5. Protection Obligation

> Reasonable security arrangements to prevent unauthorised access, collection, use, disclosure, copying, modification, disposal.

- [ ] **Encryption at rest** for personal-data fields (AES-256 or equivalent)
- [ ] **Encryption in transit** (TLS ≥ 1.2)
- [ ] Access control (RBAC) with quarterly access-list review
- [ ] Audit logging of all data-access events, retained 1 year minimum
- [ ] 2FA for all data-accessing roles
- [ ] Penetration test annually
- [ ] **No hardcoded secrets** in tracked files
- [ ] **No raw NRICs** in code (singular reducer written to disk) — `pdpa.sg_clj/redact` strips them

<!-- agent:verify-protection -->

---

## 6. Retention Limitation Obligation

> Stop retaining personal data (or remove the link to the individual) when the purpose ends.

- [ ] Retention schedule published per data category
- [ ] Automated purge job runs (nightly or weekly)
- [ ] Soft-delete kept for **≤ 90 days** unless legal hold
- [ ] Anonymisation or pseudonymisation applied before any analytics use

<!-- agent:verify-retention -->

---

## 7. Transfer Limitation Obligation (§26)

> Overseas transfers only to organisations under a comparable standard.

Choose **at least one** mechanism and tick that box:

- [ ] APEC **CBPR** (Cross-Border Privacy Rules) certification
- [ ] APEC **PRP** (Privacy Recognition for Processors) certification
- [ ] EU **Standard Contractual Clauses** (SCCs)
- [ ] Singapore **Binding Corporate Rules**
- [ ] PDPC-approved code of practice
- [ ] Recipient country whitelisted (PDPC decision)

Document per recipient in `policies/DATA_TRANSFER_AGREEMENT.md`.

---

## 8. Access & Correction Obligation (Data Subject Requests — DSRs)

> Individuals may request: (a) access to their data, (b) correction, (c) cessation.

- [ ] DSR endpoint exists (e.g. `GET /api/dsr/export?user_id=…`, `POST /api/dsr/rectify`)
- [ ] Identity-verification flow before disclosing data
- [ ] Standard response time **≤ 30 calendar days** (PDPA default; can extend to 60 with notice)
- [ ] Free of charge for the first request per year

---

## 9. Withdrawal of Consent Obligation

> Withdrawal must work as easily as opt-in; reasonable notice given; consequences explained.

- [ ] Withdrawal endpoint exists: `POST /api/consent/withdraw`
- [ ] All downstream processing stops (or transitions to anonymised aggregate) within 7 days
- [ ] User receives confirmation including any unavoidable retained-financial-record obligations

---

## 10. Data Breach Notification Obligation

> Notify PDPC + affected individuals when a breach results in (a) significant harm, OR (b) ≥ 500 affected individuals.

- [ ] **Breach response plan** exists — see `policies/DATA_BREACH_RESPONSE.md`
- [ ] PDPC notification submitted **within 3 calendar days** of assessment
- [ ] Affected-individual notification goes out within 30 days
- [ ] Mock drill annually (Tabletop)
- [ ] DPO available 24/7 during a breach

<!-- agent:verify-breach -->

---

## 11. Accountability / DPO Designation Obligation

> Every organisation must designate ≥ 1 Data Protection Officer and publish a business contact.

- [ ] **DPO named** with full name + business email — see `policies/DPO_CONTACT.md`
- [ ] DPO contact published on homepage footer
- [ ] DPO role accountable to senior management
- [ ] DPO training refreshed annually
- [ ] pdpa-era logs preserved for **3+ years**

<!-- agent:verify-dpo -->

---

## 🤖 AI Governance Addendum (2026 PDPC guidance)

For AI/automated decision-making involving personal data:

- [ ] **Transparency** — users informed when an automated decision affects them
- [ ] **Explainability** — model outputs are interpretable to non-experts
- [ ] **Data-minimisation** — only fields required for the decision are processed
- [ ] **Human review** — a human-in-the-loop path exists for adverse decisions
- [ ] **No model retraining on user data without opt-in consent** (if collecting for ML)

---

## 🇸🇬 Safe NRIC Addendum (effective 31 December 2026)

> PDPC requires organisations to **stop using full NRICs** for authentication, identification or routine identification purposes. Avoid collecting them entirely unless legally required.

- [ ] NRIC/FIN field removed from forms (or replaced with masked `****567A`)
- [ ] No NRIC stored in plaintext; if absolutely required, salted-hash + encryption-at-rest
- [ ] All historical NRIC data pseudonymised or deleted by 31 Dec 2026
- [ ] Tooling scans run via `pdpa-sg-clj`'s redactor before commit

Run `bb scan` to auto-verify the protected-item markers above.

---

## 📋 Quarterly Review

- [ ] Re-run `bb audit .` on the production codebase
- [ ] Refresh Privacy Policy if any new data category is added
- [ ] Verify DPO contact is still live and reachable
- [ ] Review access logs for unexpected access patterns
- [ ] Confirm `pdpa-sg-clj` rule-stamp is current (`bb version`)

---

## 🤖 How `bb checklist` auto-ticks

The CLI reads `CHECKLIST.md`, looks for HTML-comment markers like
`<!-- agent:verify-protection -->` after a numbered obligation heading,
and ticks the **first** `[ ]` checkbox under that heading **iff** the relevant
scanner passes. The mapping is in `src/pdpa/checklist.clj`.

Manual ticks (any box without an auto-marker) are still your responsibility.