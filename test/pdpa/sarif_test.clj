(ns pdpa.sarif-test
  (:require [clojure.test :refer [deftest testing is]]
            [pdpa.sarif :as sarif]))

(def ^:private sample
  {:findings [{:id :nric-live :label "Live Singapore NRIC / FIN (Mod-11 valid)"
               :severity :critical :path "src/leak.clj" :line 3}
              {:id :aws-key :label "AWS access key id"
               :severity :high :path {:text "legacy-shape.txt"} :line 7}
              {:id :email-live :label "Email address in source code"
               :severity :low :path "docs.md" :line 1}]
   :counts   {:critical 1 :high 1 :medium 0 :low 1 :info 0}
   :clean?   false})

(deftest sarif-envelope
  (testing "top-level SARIF 2.1.0 envelope"
    (let [log (sarif/scan->sarif sample)]
      (is (= "2.1.0" (:version log)))
      (is (= sarif/schema-uri (:$schema log)))
      (is (= 1 (count (:runs log)))))))

(deftest sarif-driver-rules
  (testing "driver carries name, version, and one rule per finding id"
    (let [driver (get-in (sarif/scan->sarif sample) [:runs 0 :tool :driver])]
      (is (= "pdpa-sg-clj" (:name driver)))
      (is (string? (:version driver)))
      (is (= ["pdpa/aws-key" "pdpa/email-live" "pdpa/nric-live"]
             (mapv :id (:rules driver)))))))

(deftest sarif-results
  (testing "severity maps to SARIF levels; locations carry uri + line"
    (let [results (get-in (sarif/scan->sarif sample) [:runs 0 :results])]
      (is (= 3 (count results)))
      (is (= "error" (:level (first results))))
      (is (= "pdpa/nric-live" (:ruleId (first results))))
      (is (= "src/leak.clj"
             (get-in (first results)
                     [:locations 0 :physicalLocation :artifactLocation :uri])))
      (is (= 3 (get-in (first results)
                       [:locations 0 :physicalLocation :region :startLine])))
      (is (= "note" (:level (last results))))))
  (testing "legacy map-shaped :path is normalised to a string"
    (let [results (get-in (sarif/scan->sarif sample) [:runs 0 :results])]
      (is (= "legacy-shape.txt"
             (get-in (second results)
                     [:locations 0 :physicalLocation :artifactLocation :uri]))))))

(deftest sarif-empty-scan
  (testing "a clean scan yields a valid log with zero results"
    (let [log (sarif/scan->sarif {:findings [] :counts {} :clean? true})]
      (is (= [] (get-in log [:runs 0 :results])))
      (is (= [] (get-in log [:runs 0 :tool :driver :rules]))))))
