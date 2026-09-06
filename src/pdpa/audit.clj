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

(defn- opt-val
  "Value of a `--key=value` CLI arg, or nil."
  [args k]
  (some #(when (str/starts-with? % (str "--" k "="))
           (subs % (count (str "--" k "="))))
        args))

(defn- parse-args
  "Hand-rolled parser: [<path>] [--json|-j] [--sarif] [--format|-f text|json|
  sarif|html|md] [--out|-o FILE] [--with-evidence|-w KEY] (both `--k v` and
  `--k=v` spellings). Deliberately NOT babashka.cli: its parse-opts
  spec/return shapes drift across Babashka versions (flat map vs {:spec}
  map spec) and a mismatch silently degrades every flag to text output.
  Returns [path options]."
  [args]
  (loop [xs (seq args) path nil fmt nil out nil
         json? false sarif? false extra-evidence []]
    (if (nil? xs)
      [path {:format (or fmt (when sarif? "sarif") (when json? "json") "text")
             :out    out
             :json   json?
             :extra-evidence extra-evidence}]
      (let [a (first xs) r (next xs)]
        (cond
          (or (= a "--format") (= a "-f"))
          (recur (next r) path (or (first r) fmt) out
                 json? sarif? extra-evidence)

          (str/starts-with? a "--format=")
          (recur r path (subs a (count "--format=")) out
                 json? sarif? extra-evidence)

          (or (= a "--out") (= a "-o"))
          (recur (next r) path fmt (first r)
                 json? sarif? extra-evidence)

          (str/starts-with? a "--out=")
          (recur r path fmt (subs a (count "--out="))
                 json? sarif? extra-evidence)

          (or (= a "--json") (= a "-j"))
          (recur r path fmt out true sarif? extra-evidence)

          (= a "--sarif")
          (recur r path fmt out json? true extra-evidence)

          (or (= a "--with-evidence") (= a "-w"))
          (recur (next r) path fmt out
                 json? sarif? (conj extra-evidence (first r)))

          (str/starts-with? a "--with-evidence=")
          (recur r path fmt out
                 json? sarif?
                 (conj extra-evidence (subs a (count "--with-evidence="))))

          (str/starts-with? a "-")
          (recur r path fmt out json? sarif? extra-evidence)

          :else
          (recur r (or path a) fmt out json? sarif? extra-evidence))))))

;; ---------------------------------------------------------------------
;; Main entry point
;; ---------------------------------------------------------------------

(defn run
  "Babashka entry point. CLI args: [<path>] [--json] [--sarif]
  [--format text|json|sarif|html|md] [--out FILE] [--with-evidence=KEY].
  Prints to stdout unless --out is given. Returns a summary map."
  [args]
  (let [[path opts] (parse-args args)
        scan-res    (scan/scan path)
        evidence    (-> (detect-evidence path)
                        (into (:extra-evidence opts))
                        (into (evidence-from-args args))
                        distinct)
        chk-file    (io/file "CHECKLIST.md")
        chk-md      (if (.exists chk-file)
                      (slurp chk-file)
                      (slurp (io/resource "CHECKLIST.md")))
        ticked-md   (checklist/auto-tick chk-md scan-res evidence)
        c           (:counts scan-res)
        compliant?  (and (zero? (or (:critical c) 0))
                         (zero? (or (:high    c) 0)))
        fmt         (or (:format opts) "text")
        out         (:out opts)
        ts          (str (java.time.Instant/now))
        ctx         {:path        path
                     :scan-result scan-res
                     :evidence    (vec evidence)
                     :compliant?  compliant?
                     :timestamp   ts}
        body        (case fmt
                      "text" nil
                      "json" (json/generate-string
                               {:toolkit    (version/banner)
                                :path       path
                                :clean?     compliant?
                                :counts     c
                                :evidence   (vec evidence)}
                               {:pretty true})
                      "sarif" ((requiring-resolve 'pdpa.sarif/generate-string)
                               scan-res)
                      "html"  ((requiring-resolve 'pdpa.report/audit->html) ctx)
                      "md"    ((requiring-resolve 'pdpa.report/audit->markdown) ctx)
                      (throw (ex-info (str "Unknown format: " fmt
                                           " (expected text|json|sarif|html|md)")
                                      {:format fmt})))]

    ;; Only rewrite CHECKLIST.md if the auto-tick logic actually changed it.
    ;; Avoids polluting git working tree on every run.
    (when (and (.exists chk-file) (not= ticked-md chk-md))
      (spit chk-file ticked-md))

    (if (= "text" fmt)
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
                           "Open findings remain — see `[CRITICAL]`/`[HIGH]` lines"))))
      (if out
        (do (spit out body)
            (println (str "[AUDIT] wrote " out " (" fmt ")")))
        (println body)))
    {:path path :compliant? compliant? :counts c
     :evidence (vec evidence) :format fmt :out out}))
