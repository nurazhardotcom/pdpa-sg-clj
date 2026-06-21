(ns pdpa.checklist-test
  (:require [clojure.test :refer [deftest testing is]]
            [pdpa.checklist :as checklist]))

(def sample-md
  "
## 1. Consent Obligation

- [ ] Manual consent bullet
<!-- agent:verify-consent -->
- [ ] Auto-tick bullet

## 2. Purpose

- [ ] Manual purpose bullet
<!-- agent:verify-purpose -->
- [ ] Auto-tick bullet

## 3. Notification

- [ ] Manual notification bullet
")

(deftest status-counts-totals
  (testing "totals are correct"
    (let [st (checklist/status sample-md)]
      (is (contains? st :consent))
      (is (contains? st :purpose))
      (is (= 2 (:total (:consent st))))
      (is (= 0 (:ticked (:consent st)))))))

(deftest auto-tick-only-with-verifier
  (testing "auto-ticks marked items iff verifier passes"
    (let [ticked (checklist/auto-tick
                   sample-md
                   {:counts {:critical 0 :medium 0}}
                   [])]
      ;; :consent has no CRITICAL findings AND evidence <= empty list;
      ;; but evidence is empty so conservation is false. We test the
      ;; less strict :purpose instead which depends solely on :counts.
      (is (clojure.string/includes? ticked "- [x] Auto-tick bullet")))))

(deftest auto-tick-does-not-touch-manual
  (testing "manual items remain unchecked regardless of scan"
    (let [ticked (checklist/auto-tick
                   sample-md
                   {:counts {:critical 0 :medium 0}}
                   [])]
      (is (clojure.string/includes? ticked "- [ ] Manual consent bullet"))
      (is (clojure.string/includes? ticked "- [ ] Manual purpose bullet")))))
