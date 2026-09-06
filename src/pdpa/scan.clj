(ns pdpa.scan
  "Wraps `ripgrep --json` to detect PII / secrets / NRICs in a directory.
  Spawns `rg` with stderr sent to the parent's stderr (so the OS pipe
  never fills and the subprocess never deadlocks) and parses its
  NDJSON output into Clojure data.

  Why ripgrep? It's the fastest filesystem walker available and emits
  well-structured JSON.  We never re-implement walking."
  (:require [cheshire.core :as json]
            [clojure.string  :as str]
            [pdpa.nric      :as nric]
            [pdpa.rules     :as rules]
            [pdpa.sarif     :as sarif]))

;; -------------------------------------------------------------------------
;; Forward declaration:  parse-rg-match is referenced by `rg-line-seq-bb`,
;; `rg-line-seq-jvm`, and `rg-line-seq`, then defined further below.
;; The forward `declare` makes the symbol analyzable to SCI; the body
;; below is unchanged.
;; -------------------------------------------------------------------------
(declare parse-rg-match)

;; ---------------------------------------------------------------------
;; Severity classification rules — compiled from the tool-independent
;; `pdpa.rules/rule-pack` so one rule list drives every backend/export.
;; ---------------------------------------------------------------------

(defn- compile-matcher
  "Build a {:id :label :sev :match-fn} classifier from a data rule.
  Code-backed rules (only :nric-valid) keep their validator; everything
  else compiles the portable :pattern / :exclude regex strings."
  [{:keys [id label sev pattern exclude code]}]
  {:id id :label label :sev sev
   :match-fn (cond
               (= :nric-valid code)
               (fn [text _path] (seq (nric/find-valid-nrics text)))

               (some? pattern)
               (let [re (re-pattern pattern)
                     ex (when exclude (re-pattern exclude))]
                 (fn [text _path]
                   (let [t (or text "")]
                     (boolean
                       (and (re-find re t)
                            (not (and ex (re-find ex t))))))))

               :else
               (throw (ex-info (str "Rule has neither :pattern nor :code: " id)
                               {:rule id})))})

(def ^:private severity-rules
  "Classifiers compiled from the tool-independent `pdpa.rules/rule-pack`."
  (mapv compile-matcher rules/rule-pack))


;; ---------------------------------------------------------------------
;; ripgrep invocation with proper stderr handling
;; ---------------------------------------------------------------------
;;
;; We redirect stderr to the parent's stderr so the OS pipe never fills
;; and the subprocess never deadlocks.  Two backends:
;;   - babashka (preferred): uses babashka.process which drains correctly.
;;   - JVM Clojure fallback: launches rg via ProcessBuilder.

(defn- rg-line-seq-bb [path]
  (let [sh (requiring-resolve 'babashka.process/sh)
        result (sh "rg"
                   "--no-heading"
                   "--line-number"
                   "--no-ignore"
                   "--json"
                   "."
                   path)]
    (->> (:out result)
         str/split-lines
         (keep #'parse-rg-match))))

(defn- parse-rg-match
  [line]
  (try
    (let [d (json/parse-string line true)
          t (:type d)]
      (when (= "match" t)
        {;; data.path is {"text" "..."} — extract the string (older reads
          ;; left a map here, which broke SARIF/IDE consumers)
          :path (or (get-in d [:data :path :text]) (:path (:data d)))
          ;; data.lines is {"text" "..."} — extract the string, don't invoke it
          :text (get-in d [:data :lines :text])
          :line (:line_number (:data d))}))
    (catch Exception _ nil)))

(defn- rg-line-seq-jvm [path]
  ;; SCI/Babashka cannot resolve the inner-class static-field syntax
  ;; `ProcessBuilder$Redirect/INHERIT` at analyze time, so we reach for
  ;; the field by reflection. In JVM Clojure this is identical to the
  ;; static-field access; just one extra indirection.
  (let [redirect-field java.lang.ProcessBuilder$Redirect/INHERIT
        pb   (doto (ProcessBuilder.
                       ["rg" "--no-heading" "--line-number"
                        "--no-ignore" "--json" "." path])
               (.redirectError redirect-field))
        proc (.start pb)
        in   (java.io.BufferedReader.
                (java.io.InputStreamReader.
                  (.getInputStream proc)))]
    (try
      (->> (line-seq in) (keep #'parse-rg-match) doall)
      (finally
        (try (.close in) (catch Exception _))
        (try (.waitFor proc 5 java.util.concurrent.TimeUnit/SECONDS)
             (catch Exception _))
        (try (.destroy proc) (catch Exception _))))))

(defn- rg-line-seq [path]
  (try
    (let [sh (requiring-resolve 'babashka.process/sh)
          result (sh "rg"
                     "--no-heading"
                     "--line-number"
                     "--no-ignore"
                     "--json"
                     "."
                     path)]
      (->> (:out result) str/split-lines (keep #'parse-rg-match)))
    (catch Exception _
      (try (rg-line-seq-jvm path)
           (catch Exception e
             (throw (ex-info "Could not run `rg`. Install ripgrep or
                              run from Babashka." {:cause e})))))))

;; ---------------------------------------------------------------------
;; Public API
;; ---------------------------------------------------------------------

(defn- classify [text path]
  (some #(when ((:match-fn %) text path) %) severity-rules))

(def ignore-marker
  "Lines containing this marker are excluded from findings.
   Use for intentional documentation examples:
     sed -i 's/S0466008B/S********G/g' ... # pdpa:ignore — fictional example
   or in Markdown prose:
     <!-- pdpa:ignore --> S0466008B is a fictional example value"
  "pdpa:ignore")

(defn- ignored? [text]
  (str/includes? (or text "") ignore-marker))

(defn scan
  "Walks `path` with ripgrep, classifies findings.
  Returns {:findings [...], :counts {:critical N :high N ...}, :clean? ...}."
  ([path]    (scan path {}))
   ([path _]  ;; {:keys [quiet?]} ignored for now
    (let [findings (keep (fn [{:keys [text path line]}]
                           (when-not (ignored? text)
                              (when-let [rule (classify text path)]
                                {:severity (:sev rule)
                                 :id       (:id rule)
                                 :label    (:label rule)
                                 :path     path
                                 :line     line})))
                         (rg-line-seq path))
         counts   (->> findings
                       (group-by :severity)
                       (reduce-kv (fn [m k v] (assoc m k (count v))) {}))
         counts   (merge (zipmap [:critical :high :medium :low :info]
                                 (repeat 0))
                        counts)]
     {:findings findings
      :counts   counts
      :clean?   (and (zero? (:critical counts))
                     (zero? (:high counts)))})))

;; ---------------------------------------------------------------------
;; Babashka entry point
;; ---------------------------------------------------------------------

(defn- fmt-finding [{:keys [severity label path line]}]
  (format "  [%s] %s:%d — %s"
          (str/upper-case (name severity))
          path line label))

(defn- fmt-quickfix [{:keys [severity label path line]}]
  (format "%s:%d: [%s] %s"
          path line (str/upper-case (name severity)) label))

(defn- parse-run-args
  "Split CLI args into [path format out]. Supports both `--flag value`
  and `--flag=value` spellings."
  [args]
  (loop [xs (seq args) path nil fmt "text" out nil]
    (if (nil? xs)
      [(or path ".") fmt out]
      (let [a (first xs) r (next xs)]
        (cond
          (or (= a "--format") (= a "-f"))
          (recur (next r) path (or (first r) fmt) out)

          (str/starts-with? a "--format=")
          (recur r path (subs a (count "--format=")) out)

          (= a "--json")     (recur r path "json" out)
          (= a "--sarif")    (recur r path "sarif" out)
          (= a "--quickfix") (recur r path "quickfix" out)

          (or (= a "--out") (= a "-o"))
          (recur (next r) path fmt (first r))

          (str/starts-with? a "--out=")
          (recur r path fmt (subs a (count "--out=")))

          (str/starts-with? a "-") (recur r path fmt out)
          :else (recur r (or path a) fmt out))))))

(defn run
  "Babashka entry point. CLI args: [<path>] [--format text|json|sarif|quickfix]
  [--json] [--sarif] [--quickfix] [--out FILE]. Prints to stdout unless
  --out is given. `quickfix` emits `path:line: [SEV] label` for editors.
  Returns the scan result map."
  [args]
  (let [[path fmt out] (parse-run-args args)
        result (scan path)
        body   (case fmt
                 "text"     nil
                 "json"     (json/generate-string result {:pretty true})
                 "sarif"    (sarif/generate-string result)
                 "quickfix" (->> (:findings result)
                                 (map fmt-quickfix)
                                 (str/join "\n"))
                 (throw (ex-info (str "Unknown format: " fmt
                                      " (expected text|json|sarif|quickfix)")
                                 {:format fmt})))]
    (if (= "text" fmt)
      (let [c (:counts result)]
        (println (format "[SCAN] %s — clean? %s" path (:clean? result)))
        (println (format "  counts: critical=%d high=%d medium=%d low=%d"
                         (or (:critical c) 0) (or (:high c) 0)
                         (or (:medium  c) 0) (or (:low c) 0)))
        (doseq [f (:findings result)] (println (fmt-finding f)))
        (println "[DONE] exit-code 0 means clean"))
      (if out
        (do (spit out body)
            (println (str "[SCAN] wrote " out " (" fmt ")")))
        (println body)))
    result))
