(ns pdpa.policy
  "Loads policy templates from resources/policies/ and substitutes
  `<<KEY>>` placeholders with caller-supplied values. Zero magic — just
  clojure.string/replace, keep it boring and easy to audit."
  (:require [clojure.string :as str]
            [clojure.java.io :as io]))

(def ^:private policy-dir "policies/")

(defn list-templates
  "Returns vec of template filenames in resources/policies/."
  []
  (->> (file-seq (clojure.java.io/file (clojure.java.io/resource policy-dir)))
       (filter #(.isFile %))
       (map #(.getName %))
       (filter #(str/ends-with? % ".template.md"))
       sort
       vec))

(defn fill
  "Read template `name` and substitute every `<<KEY>>` with `values`.
  Returns the filled markdown content as a string."
  [name values]
  (let [resource-url (io/resource (str policy-dir name))
        _             (when-not resource-url
                        (throw (ex-info (str "template not found: " name)
                                        {:name name})))
        content       (slurp resource-url)]
    (reduce-kv (fn [t k v] (str/replace t (str "<<" (name k) ">>") (str v)))
               content
               values)))

(defn fill-and-write!
  "Fill template `name` and write to `out-path`. Returns the out-path."
  [name values out-path]
  (let [parent (.getParentFile (io/file out-path))]
    (when parent (.mkdirs parent)))
  (spit out-path (fill name values))
  out-path)

;; ---------------------------------------------------------------------
;; DPO generator
;; ---------------------------------------------------------------------

(defn dpo
  "Generate a DPO contact page. CLI arg: [name email]"
  [args]
  (require '[babashka.cli :as cli])
  (let [opts   (cli/parse-opts args [["-n" "--name NAME" "DPO full name"]
                                       ["-e" "--email EMAIL" "DPO business email"]])
        name   (or (:name opts)   "Data Protection Officer")
        email  (or (:email opts)  "dpo@example.com")
        today  (str (java.time.LocalDate/now))]
    (fill-and-write!
     "DPO_CONTACT.template.md"
     {"DPO_NAME"  name
      "DPO_EMAIL" email
      "EFFECTIVE_DATE" today}
     "DPO_CONTACT.md")))
