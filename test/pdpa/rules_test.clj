(ns pdpa.rules-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.string :as str]
            [pdpa.rules :as rules]))

(deftest pack-schema-valid
  (testing "every rule carries the required tool-independent keys"
    (is (seq rules/rule-pack))
    (doseq [{:keys [id label sev kind pattern code refs]} rules/rule-pack]
      (is (keyword? id) (str "rule id missing: " (pr-str id)))
      (is (string? label))
      (is (contains? #{:critical :high :medium :low} sev))
      (is (contains? #{:pii :secret} kind))
      (is (or (string? pattern) (keyword? code))
          (str "rule needs :pattern or :code: " id))
      (is (vector? refs)))))

(deftest patterns-compile
  (testing "every :pattern string compiles to a regex"
    (doseq [{:keys [id pattern]} rules/rule-pack
            :when (string? pattern)]
      (is (instance? java.util.regex.Pattern (re-pattern pattern))
          (str "uncompilable pattern: " id)))))

(deftest pack-covers-requested-secrets
  (testing "the DevSecOps secret set from the feature request is present"
    (let [ids (set (map :id rules/rule-pack))]
      (doseq [id [:jwt-token :aws-secret :aws-session :cyberark-conjur
                  :slack-token :openai-key :anthropic-key :gcp-key
                  :azure-conn-str :azure-account-key]]
        (is (contains? ids id) (str "missing secret rule: " id))))))

(deftest gitleaks-export-round-trips
  (testing "gitleaks export lists every portable secret rule"
    (let [toml (rules/->gitleaks-toml)
          n    (count (re-seq #"\[\[rules\]\]" toml))]
      (is (str/includes? toml "[[rules]]"))
      (is (= (count (rules/secret-rules)) n))
      (doseq [{:keys [id]} (rules/secret-rules)]
        (is (str/includes? toml (str "pdpa-" (name id)))))))
  (testing "code-backed NRIC rule is documented, not exported"
    (is (str/includes? (rules/->gitleaks-toml) "Mod-11"))))

(deftest json-export-parses
  (testing "rules JSON is the full pack in portable form"
    (let [parsed ((requiring-resolve 'cheshire.core/parse-string)
                  (rules/->rules-json) true)]
      (is (= (count rules/rule-pack) (count parsed)))
      (is (every? :id parsed)))))
