(ns pdpa.report-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.string :as str]
            [pdpa.report :as report]))

(def ^:private ctx
  {:path        "demo-project"
   :scan-result {:findings [{:id :aws-key :label "AWS access key id <prod>"
                             :severity :high :path "deploy.sh" :line 9}]
                 :counts   {:critical 0 :high 1 :medium 0 :low 0}
                 :clean?   false}
   :evidence    ["PRIVACY_POLICY"]
   :compliant?  false
   :timestamp   "2026-09-06T00:00:00Z"})

(deftest markdown-report
  (testing "markdown executive summary carries verdict, counts, evidence"
    (let [md (report/audit->markdown ctx)]
      (is (str/includes? md "# PDPA compliance audit report"))
      (is (str/includes? md "ACTION REQUIRED"))
      (is (str/includes? md "| High | 1 |"))
      (is (str/includes? md "PRIVACY_POLICY"))
      (is (str/includes? md "AWS access key id <prod>"))))
  (testing "clean scan renders the compliant verdict and no-findings note"
    (let [md (report/audit->markdown
               (assoc ctx :scan-result {:findings [] :counts {}}
                           :compliant? true :evidence []))]
      (is (str/includes? md "COMPLIANT"))
      (is (str/includes? md "No findings")))))

(deftest html-report
  (testing "html report is standalone, escaped, and verdict-stamped"
    (let [html (report/audit->html ctx)]
      (is (str/includes? html "<!DOCTYPE html>"))
      (is (str/includes? html "ACTION REQUIRED"))
      (is (str/includes? html "AWS access key id &lt;prod&gt;"))
      (is (str/includes? html "deploy.sh:9"))))
  (testing "compliant context renders the green badge"
    (let [html (report/audit->html
                 (assoc ctx :scan-result {:findings [] :counts {}}
                             :compliant? true))]
      (is (str/includes? html "COMPLIANT")))))
