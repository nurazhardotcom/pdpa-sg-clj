(ns pdpa.audit
  "Orchestrator. Runs the full audit pipeline:

    1. `pdpa.scan` over the target path
    2. Detect published evidence files (Privacy Policy, DPO contact, etc.)
    3. `pdpa.checklist/auto-tick` on CHECKLIST.md
    4. Emit JSON or Human-readable summary

  Usage:
    (pdpa.audit/run [\"./path\" \"--json\"])"
  (:require [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string  :as str]
            [pdpa.scan       :as scan]
            [pdpa.checklist  :as checklist]
            [pdpa.policy     :as policy]
            [pdpa.version    :as version]))

;; ---------------------------------------------------------------------
;; Detect already-published compliance evidence files
;; ---------------------------------------------------------------------
;;
;; Only looks at the project ROOT (top-level) — published documents
;; should live at the top of the workspace, not buried in deep paths.
;; This avoids walking massive monorepos while still catching the typical
;; patterns (Privacy Policy in repo root, sns. taglib, …).

(def ^:private evidence-rules
  ;; [top-level-file-name  evidence-key]
  [["PRIVACY_POLICY.md"          :PRIVACY_POLICY]
   ["DPO_CONTACT.md"             :DPO_CONTACT]
   ["CONSENT_FORM.md"            :CONSENT_FORM]
   ["DATA_BREACH_RESPONSE.md"    :BREACH_PLAN]
   ["DATA_TRANSFER_AGREEMENT.md" :TRANSFER_AGREEMENT]
   ["SECURITY_HARDENING.md"      :SECURITY_HARDENING]
   ["RETENTION_SCHEDULE.md"      :RETENTION_SCHEDULE]
   ["DPIA.md"                    :DPIA]])

(defn- detect-evidence
  "Returns a vector of canonical evidence keys for files that exist at the
  project root."
  [path]
  (let [root (io/file path)]
    (when (.isDirectory root)
      (->> (.listFiles root)
           (map #(.getName ^java.io.File %))
           (into #{})
           (keep (fn [name]
                   (some (fn [[file ev]]
                           (when (= file name) (name ev)))
                         evidence-rules)))
           vec))))

(defn- evidence-from-args
  "Parse `--with-evidence=KEY` style overrides."
  [args]
  (->> args
       (filter #(str/starts-with? % "--with-evidence="))
       (map #(subs % (count "--with-evidence=")))))

;; ---------------------------------------------------------------------
;; CLI args parsing (compatible with both BB and JVM)
;; ---------------------------------------------------------------------

(defn- parse-args [args]
  (let [{:keys [options]} (try
                            (require 'babashka.cli)
                            (babashka.cli/parse-opts
                              args
                              [["-j" "--json" "JSON output only"]
                               ["-w" "--with-evidence TAGNAME"
                                "Extra evidence tags to recognise"]])
                            (catch Exception _
                              ;; Fallback for JVM Clojure
                              {:options
                               {:json (boolean (some #(= % "--json") args))}}))
        path (or (first (filter #(not (str/starts-with? % "-")) args))
                 ".")]
    [path options]))

;; ---------------------------------------------------------------------
;; Main entry point
;; ---------------------------------------------------------------------

(defn run
  "Babashka entry point.  CLI args: [<path>] [--json] [--with-evidence=KEY]."
  [args]
  (let [[path opts] (parse-args args)
        scan-res    (scan/scan path)
        evidence    (-> (detect-evidence path)
                        (into (evidence-from-args args))
                        distinct)
        chk-file    (io/file "CHECKLIST.md")
        chk-md      (if (.exists chk-file)
                      (slurp chk-file)
                      (slurp (io/resource "CHECKLIST.md")))
        ticked-md   (checklist/auto-tick chk-md scan-res evidence)
        c           (:counts scan-res)
        compliant?  (and (zero? (or (:critical c) 0))
                         (zero? (or (:high    c) 0)))]

    ;; Only rewrite CHECKLIST.md if the auto-tick logic actually changed it.
    ;; Avoids polluting git working tree on every run.
    (when (and (.exists chk-file) (not= ticked-md chk-md))
      (spit chk-file ticked-md))

    (if (:json opts)
      (println (json/generate-string
                 {:toolkit    (version/banner)
                  :path       path
                  :clean?     compliant?
                  :counts     c
                  :evidence   (vec evidence)}
                 {:pretty true}))
      (do
        (println "🛡️  pdpa-sg-clj audit")
        (println (format "📦  %s" (version/banner)))
        (println (format "📍  path: %s" path))
        (println (format "🔍  scan: critical=%d high=%d medium=%d low=%d"
                         (or (:critical c) 0) (or (:high    c) 0)
                         (or (:medium  c) 0) (or (:low     c) 0)))
        (println (format "📄  evidence found: %d" (count evidence)))
        (doseq [e evidence] (println (format "  ✓  published: %s" e)))
        (println (format "📋  CHECKLIST.md auto-tick delta: %s"
                         (if (= ticked-md chk-md) "none" "APPLIED")))
        (println (format "%s %s"
                         (if compliant? "✅" "⚠️")
                         (if compliant?
                           "0 CRITICAL + 0 HIGH findings — auto-ticked protections apply"
                           "Open findings remain — see `[CRITICAL]`/`[HIGH]` lines")))))))
