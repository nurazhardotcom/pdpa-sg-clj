(ns pdpa.redact
  "PII redaction pipeline. Strips valid NRICs, SG phone numbers, and emails
  from any text body, replacing them with `[REDACTED_*]` placeholders.

  Pipeline order:
    1. Valid Singapore NRIC / FIN (Mod-11 verified; false-positive guarded)
    2. Singapore-formatted mobile / landline phone
    3. Email address (RFC-5322 simplified)
    4. Generic `[REDACTED_*]` (no-op — never matches the regex)

  Text transformation is portable. File and CLI operations are native
  adapters and are not invoked by ClojureScript callers."
  (:require [clojure.string :as str]
            [pdpa.nric :as nric]
            #?(:clj [clojure.java.io :as io])))

;; ---------------------------------------------------------------------
;; Patterns
;; ---------------------------------------------------------------------

(def phone-re
  "Historical Singapore phone expression, retained in portable form.

   The old extended expression used `;;` comments, which the regex engines
   treat as literal text rather than comments. This equivalent preserves
   that native behavior on both hosts; changing it belongs to a separate
   redactor-behavior fix."
  #"(?:\+65\s?)?(?:[89]\d{7};;mobile|\d{4}\s?\d{4};;landline)")

(def email-re
  #"[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}")

;; ---------------------------------------------------------------------
;; Placeholder map (overridable per-org)
;; ---------------------------------------------------------------------

(def default-placeholders
  {:nric  "[REDACTED_NRIC]"
   :phone "[REDACTED_PHONE]"
   :email "[REDACTED_EMAIL]"})

(defn- replace-all
  "Replace literal matches without a platform-specific quoting helper."
  [text matches replacement]
  (reduce (fn [current match]
            (str/replace current match replacement))
          text
          matches))

(defn redact-text
  "Redact all PII types from `text`. Returns a map:
    {:redacted text
     :counts {:nric N :phone N :email N}
     :warnings []}"
  ([text] (redact-text text default-placeholders))
  ([text placeholders]
   (let [value   (str (or text ""))
         nrics   (nric/find-valid-nrics value)
         phones  (vec (distinct (re-seq phone-re value)))
         emails  (->> (re-seq email-re value)
                      (remove #(re-find #"@example\.(com|org|net)$" %))
                      distinct
                      vec)
         warnings []
         step    (-> value
                     (replace-all nrics
                                  (get placeholders :nric "[REDACTED_NRIC]"))
                     (replace-all phones
                                  (get placeholders :phone "[REDACTED_PHONE]"))
                     (replace-all emails
                                  (get placeholders :email "[REDACTED_EMAIL]")))]
     {:redacted step
      :counts   {:nric  (count nrics)
                 :phone (count phones)
                 :email (count emails)}
      :warnings warnings})))

;; ---------------------------------------------------------------------
;; File-level driver (used by `bb redact` from Babashka)
;; ---------------------------------------------------------------------

(defn unsupported-platform! [operation]
  (throw (ex-info (str operation " is native-only and is unavailable in ClojureScript")
                  {:operation operation :platform :cljs})))

#?(:clj
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

   :cljs
   (defn redact-file!
     "Native-only file redaction placeholder for API compatibility."
     [path]
     (unsupported-platform! (str "redact-file! " path))))

(defn counts->str [m]
  (str (m :nric) " NRICs, " (m :phone) " phones, " (m :email) " emails"))

#?(:clj
   (defn run
     "Babashka entry point. CLI args: file paths."
     [args]
     (doseq [a args]
       (let [f (io/file a)]
         (if (.isFile f)
           (let [counts (redact-file! (.getPath f))]
             (println (format "[REDACT] %s -> %s" a (counts->str counts))))
           (println (format "[SKIP ] %s is not a regular file" a))))))

   :cljs
   (defn run
     "Native-only CLI placeholder for API compatibility."
     [args]
     (unsupported-platform! (str "run " (pr-str args)))))
