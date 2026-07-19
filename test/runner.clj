(ns runner
  (:require clojure.test))

(doseq [nsym '[pdpa.nric-test pdpa.redact-test pdpa.checklist-test pdpa.scan-test]]
  (require nsym))

(let [result (apply clojure.test/run-tests
                    '[pdpa.nric-test pdpa.redact-test pdpa.checklist-test pdpa.scan-test])]
  (System/exit (if (and (zero? (:fail result))
                        (zero? (:error result)))
                 0 1)))
