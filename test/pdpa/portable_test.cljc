(ns pdpa.portable-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest testing is]]
            [pdpa.audit-context :as context]
            [pdpa.checklist :as checklist]
            [pdpa.clock :as clock]
            [pdpa.detect :as detect]
            [pdpa.json :as json]
            [pdpa.nric :as nric]
            [pdpa.policy-template :as template]
            [pdpa.redact :as redact]
            [pdpa.report :as report]
            [pdpa.rules :as rules]
            [pdpa.sarif :as sarif]
            [pdpa.version :as version]
            [pdpa.portable-fixtures :as fixtures]))

(deftest fictional-nric-fixtures-have-the-same-checksum-result
  (doseq [{:keys [label input valid? check-digit]} fixtures/nric-cases]
    (testing (str "NRIC fixture " label)
      (is (= valid? (boolean (nric/valid? input))))
      (is (= check-digit
             (nric/check-letter input))))))

(deftest fictional-redaction-fixture-is-portable
  (let [{:keys [input redacted counts known-phone-regex-defect]} fixtures/redaction-case
        result (redact/redact-text input)]
    (is (= redacted (:redacted result)))
    (is (= counts (:counts result)))
    (is (true? known-phone-regex-defect)))
  (let [{:keys [input redacted counts]} fixtures/legacy-phone-regex-case
        result (redact/redact-text input)]
    (is (= redacted (:redacted result)))
    (is (= counts (:counts result)))))

(deftest fictional-detection-fixture-is-portable
  (let [result (detect/result fixtures/detection-lines {:excludes []})]
    (is (= (:counts fixtures/detection-expected) (:counts result)))
    (is (= (:clean? fixtures/detection-expected) (:clean? result)))
    (is (= (mapv :id (:findings fixtures/detection-expected))
           (mapv :id (:findings result))))
    (is (= (mapv :line (:findings fixtures/detection-expected))
           (mapv :line (:findings result))))))

(deftest detector-boundary-is-explicit
  (let [boundary (detect/coverage)]
    (is (= :incomplete (:status boundary)))
    (is (= :configured-rule-pack (:scope boundary)))
    (is (some #{"filesystem discovery"} (:does-not-cover boundary)))
    (is (false? (:complete-assessment? (context/coverage))))))

(deftest audit-context-injects-timestamp-and-vectorizes-evidence
  (let [ctx (context/build {:path "fictional-project"
                             :scan-result {:counts {:critical 0 :high 0}}
                             :evidence ["PRIVACY_POLICY"]
                             :compliant? true
                             :timestamp (:timestamp fixtures/report-context)})]
    (is (= (:timestamp fixtures/report-context) (context/timestamp ctx)))
    (is (= ["PRIVACY_POLICY"] (:evidence ctx)))
    (is (true? (:compliant? ctx)))
    (is (false? (:compliant? (context/build {:scan-result {:counts {:critical 0 :high 0}}
                                                 :compliant? false}))))
    (is (= :incomplete (:status (context/coverage))))
    (is (false? (:complete-assessment? (context/coverage))))))

(deftest policy-template-fixture-renders-without-io
  (let [{:keys [template values rendered]} fixtures/policy-case]
    (is (= rendered (template/fill-content template values)))
    (is (= #{"ORG_NAME" "DPO_EMAIL" "RETENTION_DAYS"}
           (template/placeholders template)))))

(deftest checklist-marker-placement-is-characterized
  (testing "a marker immediately before a checkbox retains auto-tick behavior"
    (is (str/includes?
         (checklist/auto-tick (:marker-before fixtures/marker-semantics)
                              {:counts {:critical 0}} [])
         "- [x] candidate")))
  (testing "a marker after a checkbox does not retroactively tick it"
    (is (str/includes?
         (checklist/auto-tick (:marker-after fixtures/marker-semantics)
                              {:counts {:critical 0}} [])
         "- [ ] earlier")))
  ;; This is a preserved native/checklist limitation, not a portability fix.
  )

(deftest sarif-and-report-render-from-the-same-fixture
  (let [scan-result (:scan-result fixtures/report-context)
        sarif-map (sarif/scan->sarif scan-result)
        sarif-json (sarif/generate-string scan-result)
        parsed (json/parse-string sarif-json true)
        markdown (report/audit->markdown fixtures/report-context)
        html (report/audit->html fixtures/report-context)]
    (is (= "2.1.0" (:version sarif-map)))
    (is (= (:version sarif-map) (:version parsed)))
    (is (str/includes? markdown (:timestamp fixtures/report-context)))
    (is (str/includes? markdown "ACTION REQUIRED"))
    (is (str/includes? html "ACTION REQUIRED"))
    (is (str/includes? html "deploy.txt:9"))))

(deftest rule-export-round-trip-uses-the-portable-json-boundary
  (let [parsed (json/parse-string (rules/->rules-json) true)]
    (is (= (count rules/rule-pack) (count parsed)))
    (is (every? :id parsed))))

(deftest version-stamp-is-platform-independent-data
  (is (= "pdpa-sg-clj 0.3.0 / Singapore PDPA 2026-06-21"
         (version/banner)))
  (is (string? (clock/now-stamp))))
