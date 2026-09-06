(ns pdpa.sarif
  "SARIF 2.1.0 export for scan results.

  Why SARIF? Auditors and CI systems speak it natively: GitHub code
  scanning ingests it (`github/codeql-action/upload-sarif`), and the VS
  Code SARIF Viewer renders it inline. Pure data + cheshire, so it runs
  identically on Babashka and JVM Clojure."
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [pdpa.version :as version]))

(def schema-uri
  "https://json.schemastore.org/sarif-2.1.0.json")

(def sarif-version "2.1.0")
(def tool-name "pdpa-sg-clj")
(def repo-uri "https://github.com/nurazhardotcom/pdpa-sg-clj")

(defn level-for
  "Map finding severity to a SARIF level. Critical/high break gates."
  [sev]
  (case sev
    :critical "error"
    :high     "error"
    :medium   "warning"
    "note"))

(defn rule-id
  "Stable SARIF ruleId for a finding id."
  [id]
  (str "pdpa/" (name id)))

(defn finding-path
  "Finding :path as a string. Tolerates the legacy map shape
  {:text \"...\"} that older scans stored."
  [finding]
  (let [p (:path finding)]
    (cond
      (string? p) p
      (map? p)    (or (:text p) (str p))
      :else       (str p))))

(defn driver-rules
  "Deduped, sorted `tool.driver.rules` entries for a findings seq."
  [findings]
  (->> findings
       (map (fn [{:keys [id label]}]
              {:id               (rule-id id)
               :name             (name id)
               :shortDescription {:text (or label (name id))}
               :helpUri          (str repo-uri "/blob/main/README.md")}))
       (distinct)
       (sort-by :id)
       vec))

(defn finding->result
  "One SARIF `result` for one finding."
  [{:keys [id label severity line] :as finding}]
  {:ruleId    (rule-id id)
   :level     (level-for severity)
   :message   {:text (or label (name id))}
   :locations [{:physicalLocation
                {:artifactLocation {:uri (finding-path finding)}
                 :region {:startLine (or line 1)}}} ]})

(defn scan->sarif
  "Convert a `pdpa.scan/scan` result map into a SARIF log map."
  [scan-result]
  (let [findings (or (:findings scan-result) [])]
    {:$schema schema-uri
     :version sarif-version
     :runs   [{:tool   {:driver {:name           tool-name
                                 :version        version/toolkit-version
                                 :informationUri repo-uri
                                 :rules          (driver-rules findings)}}
                :results (mapv finding->result findings)}]}))

(defn generate-string
  "Scan result map → pretty-printed SARIF JSON string."
  [scan-result]
  (json/generate-string (scan->sarif scan-result) {:pretty true}))
