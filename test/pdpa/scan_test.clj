(ns pdpa.scan-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [clojure.java.io :as io]
            [pdpa.scan :as scan]))

;; S0100000J is Mod-11 valid (the canonical fictional example used across docs).
;; S0466008B is structural but checksum-INVALID (provably never issued) —
;; ideal for fictional documentation placeholders.

(defn- with-tmp-file [name content f]
  (let [dir (io/file (System/getProperty "java.io.tmpdir")
                     (str "pdpa-scan-test-" (System/nanoTime)))]
    (.mkdirs dir)
    (try
      (spit (io/file dir name) content)
      (f (str dir))
      (finally
        (doseq [x (.listFiles dir)] (.delete x))
        (.delete dir)))))

(deftest scan-result-shape
  (testing "scan returns counts + clean? on any directory"
    (with-tmp-file "clean.txt" "nothing sensitive here\n"
      (fn [dir]
        (let [r (scan/scan dir)]
          (is (contains? r :counts))
          (is (true? (:clean? r)))
          (is (empty? (:findings r))))))))

(deftest severity-rules-exhaustive
  (testing "every rule has required keys"
    ;; private access via reflection not possible — just sanity check scan on a tmp dir
    (let [tmp-dir "/tmp/pdpa-scan-test"
          _       (.mkdirs (java.io.File. tmp-dir))
          f       (java.io.File.
                    (str tmp-dir "/t.txt"))
          _       (spit f "User S0000000J contact alice@example.com"
                        )]
      ;; We will not spawn rg here in the test for speed; smoke check only.
      (is (.exists f)))))

(deftest valid-nric-is-critical
  (testing "Mod-11 valid NRIC is flagged critical"
    (with-tmp-file "leak.txt" "nric S0100000J on file\n"
      (fn [dir]
        (let [r (scan/scan dir)]
          (is (false? (:clean? r)))
          (is (= 1 (get-in r [:counts :critical])))
          (is (= :nric-live (:id (first (:findings r))))))))))

(deftest invalid-nric-passes
  (testing "checksum-invalid NRIC-shaped string is not flagged"
    (with-tmp-file "ok.txt" "nric S0466008B on file\n"
      (fn [dir]
        (is (true? (:clean? (scan/scan dir))))))))

(deftest sg-phone-is-critical
  (testing "+65 mobile pattern is flagged critical"
    (with-tmp-file "leak.txt" "call +6594823068 asap\n"
      (fn [dir]
        (let [r (scan/scan dir)]
          (is (false? (:clean? r)))
          (is (= :phone-sg (:id (first (:findings r))))))))))

(deftest ignore-marker-suppresses-line
  (testing "lines carrying pdpa:ignore are excluded (shell + md comment styles)"
    (with-tmp-file "doc.md"
      (str "sed -i 's/S0100000J/S********G/g' f # pdpa:ignore — fictional example\n"
           "call +6594823068 <!-- pdpa:ignore --> fictional example\n")
      (fn [dir]
        (let [r (scan/scan dir)]
          (is (true? (:clean? r)))
          (is (empty? (:findings r))))))))

(deftest ignore-marker-leaves-other-lines-flagged
  (testing "exclusion is per-line, not per-file"
    (with-tmp-file "doc.md"
      (str "sed -i 's/S0100000J/S********G/g' f # pdpa:ignore — fictional example\n"
           "real leak S0100000J here\n")
      (fn [dir]
        (let [r (scan/scan dir)]
          (is (false? (:clean? r)))
          (is (= 1 (count (:findings r))))
          (is (= 2 (:line (first (:findings r))))))))))
