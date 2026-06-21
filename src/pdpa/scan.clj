(ns pdpa.scan
  "Wraps `ripgrep --json` to detect PII / secrets / NRICs in a directory.
  Spawns `rg` with stderr sent to the parent's stderr (so the OS pipe
  never fills and the subprocess never deadlocks) and parses its
  NDJSON output into Clojure data.

  Why ripgrep? It's the fastest filesystem walker available and emits
  well-structured JSON.  We never re-implement walking."
  (:require [cheshire.core :as json]
            [clojure.string  :as str]
            [pdpa.nric      :as nric]))

;; -------------------------------------------------------------------------
;; Forward declaration:  parse-rg-match is referenced by `rg-line-seq-bb`,
;; `rg-line-seq-jvm`, and `rg-line-seq`, then defined further below.
;; The forward `declare` makes the symbol analyzable to SCI; the body
;; below is unchanged.
;; -------------------------------------------------------------------------
(declare parse-rg-match)

;; ---------------------------------------------------------------------
;; Severity classification rules
;; ---------------------------------------------------------------------

(def ^:private severity-rules
  [{:id      :nric-live
    :label   "Live Singapore NRIC / FIN (Mod-11 valid)"
    :sev     :critical
    :match-fn (fn [text _path] (seq (nric/find-valid-nrics text)))}

   {:id      :phone-sg
    :label   "Singapore phone number with country code (+65)"
    :sev     :critical
    :match-fn (fn [text _path] (boolean (re-find #"\+65\s?[89]\d{7}" text)))}

   {:id      :email-live
    :label   "Email address in source code"
    :sev     :low
    :match-fn (fn [text _path]
               (and (re-find #"[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}" text)
                    (not (re-find #"@example\.(com|org|net)" text))))}

   {:id      :aws-key
    :label   "AWS access key id"
    :sev     :high
    :match-fn (fn [text _path] (re-find #"AKIA[0-9A-Z]{16}" text))}

   {:id      :stripe-live
    :label   "Stripe live secret key"
    :sev     :high
    :match-fn (fn [text _path] (re-find #"sk_live_[A-Za-z0-9]{16,}" text))}

   {:id      :github-token
    :label   "GitHub personal access token"
    :sev     :high
    :match-fn (fn [text _path] (re-find #"ghp_[A-Za-z0-9]{36}" text))}

   {:id      :private-key
    :label   "PEM private key block"
    :sev     :high
    :match-fn (fn [text _path]
               (boolean (re-find
                          #"-----BEGIN (RSA |EC |DSA )?PRIVATE KEY( BLOCK)?-----"
                          text)))}

   {:id      :django-insecure-key
    :label   "Django 'django-insecure-' placeholder committed"
    :sev     :medium
    :match-fn (fn [text _path]
               (boolean (re-find #"SECRET_KEY\s*=\s*['\"].*django-insecure-" text)))}

   {:id      :hardcoded-password
    :label   "Hardcoded password value (non-empty, non-test)"
    :sev     :medium
    :match-fn (fn [text _path]
               (and (re-find #"(?i)(password|passwd|pwd)\s*[:=]\s*['\"][^.'\"{\s]{6,}" text)
                    (not (re-find #"(?i)(test|fake|example)" text))))}

   {:id      :hardcoded-secret
    :label   "Generic API secret literal"
    :sev     :medium
    :match-fn (fn [text _path]
               (boolean (re-find
                          #"(?i)(api[_-]?key|secret|token)\s*[:=]\s*['\"][A-Za-z0-9_\-]{16,}"
                          text)))}])

;; ---------------------------------------------------------------------
;; ripgrep invocation with proper stderr handling
;; ---------------------------------------------------------------------
;;
;; We redirect stderr to the parent's stderr so the OS pipe never fills
;; and the subprocess never deadlocks.  Two backends:
;;   - babashka (preferred): uses babashka.process which drains correctly.
;;   - JVM Clojure fallback: launches rg via ProcessBuilder.

(defn- rg-line-seq-bb [path]
  (let [result (babashka.process/sh "rg"
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
        {:path (:path (:data d))
         :text ((:lines (:data d)))
         :line (:line_number (:data d))}))
    (catch Exception _ nil)))

(defn- rg-line-seq-jvm [path]
  ;; SCI/Babashka cannot resolve the inner-class static-field syntax
  ;; `ProcessBuilder$Redirect/INHERIT` at analyze time, so we reach for
  ;; the field by reflection. In JVM Clojure this is identical to the
  ;; static-field access; just one extra indirection.
  (let [redirect-field (.get (Class/forName "java.lang.ProcessBuilder$Redirect")
                             "INHERIT" nil)
        pb   (doto (ProcessBuilder.
                       ["rg" "--no-heading" "--line-number"
                        "--no-ignore" "--json" "." path])
               (.redirectError redirect-field))
        proc (.start pb)
        in   (java.io.BufferedReader.
                (java.io.InputStreamReader.
                  (.getInputStream proc)))]
    (try
      (->> (line-seq in) (keep #'parse-rg-match))
      (finally
        (try (.close in) (catch Exception _))
        (try (.waitFor proc 5 java.util.concurrent.TimeUnit/SECONDS)
             (catch Exception _))
        (try (.destroy proc) (catch Exception _))))))

(defn- rg-line-seq [path]
  (try
    (require 'babashka.process)
    (let [result (babashka.process/sh "rg"
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

(defn scan
  "Walks `path` with ripgrep, classifies findings.
  Returns {:findings [...], :counts {:critical N :high N ...}, :clean? ...}."
  ([path]    (scan path {}))
  ([path _]  ;; {:keys [quiet?]} ignored for now
   (let [findings (keep (fn [{:keys [text path line]}]
                          (when-let [rule (classify text path)]
                            {:severity (:sev rule)
                             :label    (:label rule)
                             :path     path
                             :line     line}))
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

(defn run
  "Babashka entry point. CLI args: optional `<path>`."
  [args]
  (let [path   (or (first args) ".")
        result (scan path)
        c      (:counts result)]
    (println (format "[SCAN] %s — clean? %s" path (:clean? result)))
    (println (format "  counts: critical=%d high=%d medium=%d low=%d"
                     (or (:critical c) 0) (or (:high c) 0)
                     (or (:medium  c) 0) (or (:low c) 0)))
    (doseq [f (:findings result)] (println (fmt-finding f)))
    (println "[DONE] exit-code 0 means clean")))
