(ns pdpa.scan
  "Wraps `ripgrep --json` to detect PII / secrets / NRICs in a directory.
  Spawns `rg` with stderr sent to the parent's stderr (so the OS pipe
  never fills and the subprocess never deadlocks) and parses its
  NDJSON output into Clojure data.

  Why ripgrep? It's the fastest filesystem walker available and emits
  well-structured JSON.  We never re-implement walking."
  (:require [cheshire.core :as json]
            [clojure.string  :as str]
            [pdpa.detect     :as detect]
            [pdpa.sarif      :as sarif]))

;; -------------------------------------------------------------------------
;; Forward declaration: parse-rg-match is referenced by the rg-line-seq
;; variants, then defined further below.
;; The forward `declare` makes the symbol analyzable to SCI; the body
;; below is unchanged.
;; -------------------------------------------------------------------------
(declare parse-rg-match)

;; -------------------------------------------------------------------------
;; Detection is implemented by the portable `pdpa.detect` namespace. This
;; adapter owns only ripgrep/process/filesystem concerns and keeps the
;; historical skip globs and line parsing below unchanged.
;; -------------------------------------------------------------------------


;; ---------------------------------------------------------------------
;; ripgrep invocation with proper stderr handling
;; ---------------------------------------------------------------------
;;
;; We redirect stderr to the parent's stderr so the OS pipe never fills
;; and the subprocess never deadlocks.  Two backends:
;;   - babashka (preferred): uses babashka.process which drains correctly.
;;   - JVM Clojure fallback: launches rg via ProcessBuilder.

(def ^:private skip-globs
  "Directories never scanned: VCS metadata, build output, and vendored
  dependency trees. Without these, one `npm install` (or a JVM `target/`)
  can make `rg --json` emit gigabytes that the scanner holds in memory —
  which OOM-killed a real run (see CHANGELOG). `--no-ignore` stays so
  audits still catch secrets hiding in *other* ignored files."
  ["!.git/" "!node_modules/" "!target/" "!out/" "!.cpcache/"])

(defn- rg-args [path]
  (concat ["rg" "--no-heading" "--line-number" "--no-ignore"]
          (mapcat (fn [g] ["--glob" g]) skip-globs)
          ["--json" "." path]))

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
        pb   (doto (ProcessBuilder. ^java.util.List (vec (rg-args path)))
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
          result (apply sh (rg-args path))]
      (->> (:out result) str/split-lines (keep #'parse-rg-match)))
    (catch Exception _
      (try (rg-line-seq-jvm path)
           (catch Exception e
             (throw (ex-info "Could not run `rg`. Install ripgrep or
                              run from Babashka." {:cause e})))))))

;; ---------------------------------------------------------------------
;; Public API
;; ---------------------------------------------------------------------

(def ignore-marker
  "Compatibility alias for the portable detector's intentional-example marker."
  detect/ignore-marker)

(defn excluded?
  "Compatibility wrapper for the portable detector's substring filter."
  [path excludes]
  (detect/excluded? path excludes))

(defn scan
  "Walks `path` with ripgrep, then delegates classification to
  `pdpa.detect/result`.
  Returns {:findings [...], :counts {:critical N :high N ...}, :clean? ...}.
  Opts: {:excludes [substr ...]} drops findings whose path contains any
  substring (e.g. [\"test/\"] keeps fixtures out of production alerts)."
  ([path] (scan path {}))
  ([path {:keys [excludes]}]
   (detect/result (rg-line-seq path) {:excludes excludes})))

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
  "Split CLI args into [path format out excludes]. Supports both
  `--flag value` and `--flag=value` spellings. `--exclude` is repeatable."
  [args]
  (loop [xs (seq args) path nil fmt "text" out nil excludes []]
    (if (nil? xs)
      [(or path ".") fmt out excludes]
      (let [a (first xs) r (next xs)]
        (cond
          (or (= a "--format") (= a "-f"))
          (recur (next r) path (or (first r) fmt) out excludes)

          (str/starts-with? a "--format=")
          (recur r path (subs a (count "--format=")) out excludes)

          (= a "--json")     (recur r path "json" out excludes)
          (= a "--sarif")    (recur r path "sarif" out excludes)
          (= a "--quickfix") (recur r path "quickfix" out excludes)

          (or (= a "--out") (= a "-o"))
          (recur (next r) path fmt (first r) excludes)

          (str/starts-with? a "--out=")
          (recur r path fmt (subs a (count "--out=")) excludes)

          (= a "--exclude")
          (recur (next r) path fmt out (conj excludes (first r)))

          (str/starts-with? a "--exclude=")
          (recur r path fmt out (conj excludes (subs a (count "--exclude="))))

          (str/starts-with? a "-") (recur r path fmt out excludes)
          :else (recur r (or path a) fmt out excludes))))))

(defn run
  "Babashka entry point. CLI args: [<path>] [--format text|json|sarif|quickfix]
  [--json] [--sarif] [--quickfix] [--out FILE] [--exclude SUBSTR]...
  Prints to stdout unless --out is given. `quickfix` emits
  `path:line: [SEV] label` for editors. Repeatable `--exclude` drops
  findings whose path contains SUBSTR (e.g. test fixtures).
  Returns the scan result map."
  [args]
  (let [[path fmt out excludes] (parse-run-args args)
        result (scan path {:excludes excludes})
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
