(ns pdpa.test-runner
  "Node entry point for the portable ClojureScript test suite."
  (:require [cljs.test :as test :refer-macros [run-tests]]
            [pdpa.checklist-test]
            [pdpa.nric-test]
            [pdpa.portable-test]
            [pdpa.redact-test]
            [pdpa.report-test]
            [pdpa.rules-test]
            [pdpa.sarif-test]))

(enable-console-print!)

(defmethod test/report [:cljs.test/default :end-run-tests] [summary]
  (when-not (test/successful? summary)
    (set! (.-exitCode js/process) 1)))

(defn -main [& _]
  (run-tests (test/empty-env)
             'pdpa.nric-test
             'pdpa.redact-test
             'pdpa.checklist-test
             'pdpa.rules-test
             'pdpa.sarif-test
             'pdpa.report-test
             'pdpa.portable-test))
