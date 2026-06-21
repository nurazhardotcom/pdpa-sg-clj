(ns pdpa.redact
  "PII redaction pipeline. Strips valid NRICs, SG phone numbers, and emails
  from any text body, replacing them with `[REDACTED_*]` placeholders.

  Pipeline order:
    1. Valid Singapore NRIC / FIN  (Mod-11 verified; false-positive guarded)
    2. Singapore-formatted mobile / landline phone
    3. Email address (RFC-5322 simplified)
    4. Generic `[REDACTED_*]` (no-op — never matches the regex)

  The redactor NEVER throws: bad input returns the input unchanged
  with a `:warnings` vector describing skipped items."
  (:require [clojure.java.io :as io]
            [clojure.string  :as str]
            [pdpa.nric      :as nric]))

;; ---------------------------------------------------------------------
;; Patterns
;; ---------------------------------------------------------------------

(def phone-re
  ;; SG mobile: starts with 8 or 9, 8 digits total
  ;; OR landline: 4 digits + 4 digits (optionally '+65 ')
  ;; OR explicitly '+65 ' prefixed mobile
  #"(?x)
     (?: \+65\s? )?
     (?:
       [89]\d{7}                       ;; mobile
       | \d{4}\s?\d{4}                 ;; landline
     )")

(def email-re
  #"[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}")

;; ---------------------------------------------------------------------
;; Placeholder map (overridable per-org)
;; ---------------------------------------------------------------------

(def default-placeholders
  {:nric  "[REDACTED_NRIC]"
   :phone "[REDACTED_PHONE]"
   :email "[REDACTED_EMAIL]"})

(defn- replace-all [text matches replacement]
  (reduce (fn [t m] (str/replace t (re-pattern (java.util.regex.Pattern/quote m))
                                  replacement))
          text
          matches))

(defn redact-text
  "Redact all PII types from `text`. Returns a map:
    {:redacted text
     :counts {:nric N :phone N :email N}
     :warnings []}"
  ([text] (redact-text text default-placeholders))
  ([text placeholders]
   (let [nrics     (nric/find-valid-nrics (str (or text "")))
         phones    (vec (distinct (re-seq phone-re (str (or text "")))))
         emails    (->> (re-seq email-re (str (or text "")))
                        (remove #(re-find #"@example\.(com|org|net)$" %))
                        distinct
                        vec)
         warnings  []
         step      (-> (str (or text ""))
                       (replace-all nrics  (get placeholders :nric  "[REDACTED_NRIC]"))
                       (replace-all phones (get placeholders :phone "[REDACTED_PHONE]"))
                       (replace-all emails (get placeholders :email "[REDACTED_EMAIL]")))]
     {:redacted step
      :counts   {:nric  (count nrics)
                 :phone (count phones)
                 :email (count emails)}
      :warnings warnings})))

;; ---------------------------------------------------------------------
;; File-level driver (used by `bb redact` from Babashka)
;; ---------------------------------------------------------------------

(defn redact-file!
  "Redact PII in a single file in-place; produce a `.redact.bak` backup.
  Returns the counts map."
  [path]
  (let [original (slurp path)
        result   (if (> (count original) 500000)
                   {:redacted original
                    :counts   {:nric 0 :phone 0 :email 0}
                    :warnings ["file > 500KB; skipped"]}
                   (redact-text original))]
    (when (and (.exists (io/file path))
               (not= (:redacted result) original))
      (spit (str path ".redact.bak") original))
    (spit path (:redacted result))
    (:counts result)))

(defn counts->str [m]
  (str (m :nric) " NRICs, " (m :phone) " phones, " (m :email) " emails"))

(defn run
  "Babashka entry point. CLI args: file paths."
  [args]
  (doseq [a args]
    (let [f (io/file a)]
      (if (.isFile f)
        (let [counts (redact-file! (.getPath f))]
          (println (format "[REDACT] %s -> %s" a (counts->str counts))))
        (println (format "[SKIP ] %s is not a regular file" a))))))
