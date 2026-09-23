(ns pdpa.nric-test
  (:require [clojure.string]
            [clojure.test :refer [deftest testing is]]
            [pdpa.nric :as nric]))

;; ---------------------------------------------------------------------
;; Valid fixtures (Mod-11 hand-checked against the published algorithm:
;; weights [2 7 6 5 4 3 2], +4 offset for T/G only, S/T table
;; "JZIHGFEDCBA", F/G/M table "XWUTRQPNMLK")
;; ---------------------------------------------------------------------
;;
;; S0100000D:  prefix S (offset 0), digits [0,1,0,0,0,0,0];
;;             sum = 1*7 = 7; 7 mod 11 = 7 -> "JZIHGFEDCBA"[7] = 'D'  ✓
;;
;; F0000002R:  prefix F (offset 0), digits [0,0,0,0,0,0,2];
;;             sum = 2*2 = 4; 4 mod 11 = 4 -> "XWUTRQPNMLK"[4] = 'R'  ✓
;;
;; M5000000U:  prefix M (offset +3), digits [5,0,0,0,0,0,0];
;;             weights [2,7,6,5,4,3,2]; sum = 5*2 = 10;
;;             (10+3) mod 11 = 13 mod 11 = 2 -> "XWUTRQPNJLK"[2] = 'U'  ✓
;;
;; M5012345J:  corroborating M anchor (independent validators agree);
;;             digits [5,0,1,2,3,4,5]; sum = 10+0+6+10+12+12+10 = 60;
;;             (60+3) mod 11 = 63 mod 11 = 8 -> "XWUTRQPNJLK"[8] = 'J'  ✓
;;
;; S1234567D / F1234567N: canonical community vectors; digit sum = 106,
;;             106 mod 11 = 7 -> 'D' / 'N' with offset 0  ✓

(def valid-citizen   "S0100000D")
(def valid-foreigner "F0000002R")
(def valid-fin       "M5000000U")

;; Invalid fixtures (structural match but Mod-11 fails):
(def invalid-citizen "S0000000Z")    ; sum=0, offset 0 -> idx 0 -> 'J' != 'Z'
(def invalid-hex     "deadbeefdeadbeefdeadbeefdeadbeefF") ;; hex false-positive

;; ---------------------------------------------------------------------

(deftest nric-regex-matches-basic-shapes
  (testing "recognises lowercase"
    (is (nric/nric-string? "s0100000d")))
  (testing "rejects prefixes outside the SG set"
    (is (not (nric/nric-string? "X1234567A"))))
  (testing "rejects too few digits"
    (is (not (nric/nric-string? "S12345A")))))

(deftest valid?-true-on-canonical-samples
  (is (nric/valid? valid-citizen)   "S0100000D passes Mod-11")
  (is (nric/valid? valid-foreigner) "F0000002R passes Mod-11 (foreigner charset)")
  (is (nric/valid? valid-fin)       "M5000000U passes Mod-11 (M charset)")
  (is (nric/valid? (clojure.string/upper-case valid-citizen))
      "uppercase input also passes"))

(deftest valid?-true-on-community-vectors
  (is (nric/valid? "S1234567D") "canonical S vector (offset 0)")
  (is (nric/valid? "F1234567N") "canonical F vector (offset 0)")
  (is (nric/valid? "T0000000G") "T prefix takes the +4 offset")
  (is (nric/valid? "G0000002M") "G prefix takes +4; idx 8 pins the MLK tail"))

(deftest valid?-false-on-bad-checksums
  (testing "structural match with wrong check letter is rejected"
    (is (not (nric/valid? invalid-citizen))))
  (testing "previous (+4-for-all, KLM-tail) outputs are now rejected"
    (is (not (nric/valid? "S0100000J")))
    (is (not (nric/valid? "F0000002K")))
    (is (not (nric/valid? "S1234567J"))))
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
  (testing "the F/G charset is XWUTRQPNMLK (different from S/T charset)"
    (is (nric/valid? "F0000002R"))))

(deftest m-prefix-uses-own-table-and-offset
  (testing "M takes +3 with its own table (J at idx 8, not M)"
    (is (nric/valid? "M5012345J")))
  (testing "previous M mechanics output is now rejected"
    (is (not (nric/valid? "M5000000P")))))
