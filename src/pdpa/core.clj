(ns pdpa.core
  "Public API for pdpa-sg-clj — what an AI agent or Clojure library
  consumer should call. Re-exports a curated, minimal surface."
  (:require [pdpa.nric     :as nric]
            [pdpa.redact   :as redact]
            [pdpa.scan     :as scan]
            [pdpa.checklist :as checklist]
            [pdpa.policy   :as policy]
            [pdpa.audit    :as audit]
            [pdpa.rules    :as rules]
            [pdpa.sarif    :as sarif]
            [pdpa.report   :as report]
            [pdpa.version  :as version]))

;; ---------------------------------------------------------------------
;; Public functions (these are what consumers use)
;; ---------------------------------------------------------------------

(defn redact
  "Redact PII from `text`. Returns the cleaned text. For counts, use
  `redact-detail`."
  [text]
  (:redacted (redact/redact-text text)))

(defn redact-detail
  "Redact PII from `text`. Returns {:redacted str :counts map}."
  [text]
  (redact/redact-text text))

(defn scan
  "Scan `path` for PII / secrets. Returns a structured map."
  ([path] (scan/scan path))
  ([path opts] (scan/scan path opts)))

(defn checklist-status
  "Read CHECKLIST.md and return a map of obligation → status."
  []
  (let [md (or (try (slurp "CHECKLIST.md") (catch Exception _ nil))
               (slurp (clojure.java.io/resource "CHECKLIST.md")))]
    (checklist/status md)))

(defn fill-policy
  "Fill policy template `name` and write to `out-path`.
  `name` is the .template.md filename; `values` is a map of KEY→value pairs."
  [name values out-path]
  (policy/fill-and-write! name values out-path))

(defn audit
  "Run the full audit pipeline against `path` and return a summary map
  {:path :compliant? :counts :evidence :format :out}."
  ([path] (audit/run [path]))
  ([path args] (audit/run (into [path] args))))

(defn to-sarif
  "Scan result map (see `scan`) → SARIF 2.1.0 JSON string for auditors
  and GitHub code scanning."
  [scan-result]
  (sarif/generate-string scan-result))

(defn audit-report
  "Audit context map (see `pdpa.report`) → Markdown or HTML string.
  `format` is :md or :html."
  [ctx format]
  (case format
    :html (report/audit->html ctx)
    :md   (report/audit->markdown ctx)))

(defn rule-pack
  "The tool-independent detection rule pack (PII + secrets data)."
  []
  rules/rule-pack)

(defn version
  "Return the toolkit + PDPA rule version banner string."
  []
  (version/banner))
