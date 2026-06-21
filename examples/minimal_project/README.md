# Minimal End-to-End Example

This is a tiny project demonstrating how an AI agent uses `pdpa-sg-clj` to walk a skeleton app from zero to Singapore-PDPA-compliant.

## State of play

Imagine you just wrote the following `users.clj` (the kind of thing an agent might write on first attempt):

```clojure
(ns minimal-project.users
  (:require [clojure.string :as str]))

(def users
  [{:name "Alice Tan"  :ic "S1234567D" :phone "+65 9123 4567" :email "alice@example.com.sg"}
   {:name "Bob Lim"    :ic "S9876543A" :phone "+65 9876 5432" :email "bob@example.com.sg"}
   {:name "Carol Wong" :ic "S0246810C" :phone "+65 8234 5678" :email "carol@example.com.sg"}])

(defn find-user [ic]
  (first (filter #(= (:ic %) ic) users)))

(defn display [user]
  (str (:name user) " (" (:ic user) ") — " (:phone user)))
```

**This file is non-compliant**: it embeds raw NRICs and phone + email PII.

## Step 1 — Run the scanner

```bash
cd pdpa-sg-clj
bb scan ../minimal_project/
```

Output (example):

```
[SCAN] ../minimal_project/ — clean? false
  counts: {:critical 3, :high 0, :medium 0, :low 3}
  [CRITICAL] users.clj:6 — Live Singapore NRIC / FIN (Mod-11 valid)
  [CRITICAL] users.clj:6 — Singapore phone number with country code
  ...
```

## Step 2 — Apply redaction

```bash
bb redact ../minimal_project/users.clj
```

After redaction:

```clojure
(ns minimal-project.users
  (:require [clojure.string :as str]))

(def users
  [{:name "Alice Tan"  :ic "[REDACTED_NRIC]"   :phone "[REDACTED_PHONE]" :email "[REDACTED_EMAIL]"}
   {:name "Bob Lim"    :ic "[REDACTED_NRIC]"   :phone "[REDACTED_PHONE]" :email "[REDACTED_EMAIL]"}
   {:name "Carol Wong" :ic "[REDACTED_NRIC]"   :phone "[REDACTED_PHONE]" :email "[REDACTED_EMAIL]"}])

;; ... plus a *.redact.bak backup of the original
```

## Step 3 — Fill in the policies

```bash
bb init ../minimal_project/
```

Then edit the templates in `../minimal_project/policies/` replacing `<<ORG_NAME>>` etc.

## Step 4 — Run the audit

```bash
bb audit ../minimal_project/
```

Output:

```
🛡️  pdpa-sg-clj audit
📦  pdpa-sg-clj 0.1.0 / Singapore PDPA 2026-06-21
📍  path: ../minimal_project
🔍  scan: critical=0 high=0 medium=0 low=0
📄  evidence found: 0
📋  CHECKLIST.md auto-ticked: OH YEAH ✅
```

## Result

- 0 CRITICAL findings → protection obligation ✓
- 0 MEDIUM findings → purpose obligation ✓
- 0 evidence found → notification / DPO still need MANUAL ticks
- The remaining ticks (1, 2, 3, 6, 7, 8, 9, 10, 11) are manual because they require HUMAN action (publishing a privacy page, naming a DPO, etc.) — not something the scanner can verify.

Read `../PDPA_CHECKLIST.md` and tick the manual boxes once you've completed the corresponding work.

Total time-to-compliant: ~30 minutes.
