(ns pdpa.nric-test
  (:require [clojure.test :refer [deftest testing is]]
            [pdpa.nric :as nric]))

;; ---------------------------------------------------------------------
;; Valid fixtures (Mod-11 hand-checked)
;; ---------------------------------------------------------------------
;;
;; S0100000J:  prefix S, digits [0,1,0,0,0,0,0]; weights [2,7,6,5,4,3,2];
;;             sum = 1*7 = 7; (7+4) mod 11 = 0 -> "JZIHGFEDCBA"[0] = 'J'  ✓
;;
;; F0000002K:  prefix F, digits [0,0,0,0,0,0,2]; weights [2,7,6,5,4,3,2];
;;             sum = 2*2 = 4; (4+4) mod 11 = 8 -> "XWUTRQPNKLM"[8] = 'K'  ✓
;;
;; M5000000P:  prefix M (numeric value = 3, weight 1); digits [5,0,0,0,0,0,0];
;;             weights for full input [3,5,0,0,0,0,0,0] = [1,2,7,6,5,4,3,2]
;;             sum = 3*1 + 5*2 = 13; (13+4) mod 11 = 17 mod 11 = 6
;;             "XWUTRQPNKLM"[6] = 'P'  ✓

(def valid-citizen   "S0100000J")
(def valid-foreigner "F0000002K")
(def valid-fin       "M5000000P")

;; Invalid fixtures (structural match but Mod-11 fails):
(def invalid-citizen "S0000000Z")    ; sum=0; (0+4) mod 11 = 4 -> 'G' != 'Z'
(def invalid-hex     "deadbeefdeadbeefdeadbeefdeadbeefF") ;; hex false-positive

;; ---------------------------------------------------------------------

(deftest nric-regex-matches-basic-shapes
  (testing "recognises lowercase"
    (is (nric/nric-string? "s0100000j")))
  (testing "rejects prefixes outside the SG set"
    (is (not (nric/nric-string? "X1234567A"))))
  (testing "rejects too few digits"
    (is (not (nric/nric-string? "S12345A")))))

(deftest valid?-true-on-canonical-samples
  (is (nric/valid? valid-citizen)   "S0100000J passes Mod-11")
  (is (nric/valid? valid-foreigner) "F0000002K passes Mod-11 (foreigner charset)")
  (is (nric/valid? valid-fin)       "M5000000P passes Mod-11 (FIN charset)")
  (is (nric/valid? (clojure.string/upper-case valid-citizen))
      "uppercase input also passes"))

(deftest valid?-false-on-bad-checksums
  (testing "structural match with wrong check letter is rejected"
    (is (not (nric/valid? invalid-citizen))))
  (testing "hex false-positives are filtered out"
    (is (not (nric/valid? invalid-hex))))
  (testing "nil and empty are safe"
    (is (nil? (nric/valid? nil)))
    (is (nil? (nric/valid? "")))))

(deftest find-valid-nrics-filters-hex-false-positives
  (let [text (str "User " valid-citizen " and " invalid-citizen
                  " and a long hex string " invalid-hex " applied.")]
    (is (= [valid-citizen] (nric/find-valid-nrics text))
        "Only Mod-11 valid NRICs survive; hex strings are dropped")))

(deftest f-prefix-uses-foreigner-charset
  (testing "the F/G/M charset is XWUTRQPNKLM (different from S/T charset)"
    (is (nric/valid? "F0000002K"))))
