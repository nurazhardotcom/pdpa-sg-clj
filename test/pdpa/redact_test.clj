(ns pdpa.redact-test
  (:require [clojure.test :refer [deftest testing is]]
            [pdpa.redact :as redact]))

(def valid-nric "S0100000J")
(def another-nric "F0000002K")

(deftest redact-text-basic
  (testing "returns map shape"
    (let [r (redact/redact-text "Hello world")]
      (is (= "Hello world" (:redacted r)))
      (is (zero? (:nric  (:counts r))))
      (is (zero? (:phone (:counts r))))
      (is (zero? (:email (:counts r)))))))

(deftest redact-text-replaces-valid-nric
  (let [original (str "User " valid-nric " and " another-nric " live somewhere.")
        r        (redact/redact-text original)]
    (is (clojure.string/includes? (:redacted r) "[REDACTED_NRIC]"))
    ;; Both fixtures are Mod-11 valid; expect 2 redactions
    (is (= 2 (:nric (:counts r))))
    (is (not (clojure.string/includes? (:redacted r) valid-nric)))
    (is (not (clojure.string/includes? (:redacted r) another-nric)))))

(deftest redact-text-replaces-emails
  (testing "real emails are replaced"
    (let [r (redact/redact-text "Contact alice@acme.com or bob@example.com.sg")]
      (is (clojure.string/includes? (:redacted r) "[REDACTED_EMAIL]"))
      (is (= 2 (:email (:counts r))))))
  (testing "example.com emails kept (placeholder convention)"
    (let [r (redact/redact-text "alice@example.com")]
      (is (= 0 (:email (:counts r)))))))

(deftest redact-text-skips-bad-checksums
  ;; S0000000Z has correct shape but Mod-11 invalid; must not be redacted.
  (let [s  (str "User " "S0000000Z" " called")
        r  (redact/redact-text s)]
    (is (not (clojure.string/includes? (:redacted r) "[REDACTED_NRIC]")))
    (is (clojure.string/includes? (:redacted r) "S0000000Z"))))

(deftest redact-text-idempotent
  (let [s  (str "User " valid-nric " and bob@acme.sg called")
        r1 (redact/redact-text s)
        r2 (redact/redact-text (:redacted r1))]
    (is (= (:redacted r1) (:redacted r2)))))

(deftest redact-text-safe-on-bad-input
  (is (= "" (:redacted (redact/redact-text ""))))
  (let [r (redact/redact-text nil)]
    (is (or (nil? (:redacted r)) (= "" (:redacted r))))))
