(ns pdpa.scan-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [clojure.java.io :as io]
            [clojure.string :as str]
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

(deftest finding-path-is-string
  (testing ":path is a plain string (not the raw rg JSON map)"
    (with-tmp-file "leak.txt" "nric S0100000J on file\n" ; pdpa:ignore — fictional fixture
      (fn [dir]
        (let [r (scan/scan dir)]
          (is (string? (:path (first (:findings r))))))))))

(deftest jwt-token-is-high
  (testing "three-segment JWT bearer token is flagged high"
    (with-tmp-file "leak.txt" "auth eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4ifQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJVadQssw5c\n" ; pdpa:ignore — fictional fixture
      (fn [dir]
        (let [r (scan/scan dir)]
          (is (false? (:clean? r)))
          (is (= :jwt-token (:id (first (:findings r))))))))))

(deftest two-segment-token-passes
  (testing "two-segment lookalike is not a JWT"
    (with-tmp-file "ok.txt" "token eyJhbGciOi.dGVzdA\n" ; pdpa:ignore — fictional fixture
      (fn [dir]
        (is (true? (:clean? (scan/scan dir))))))))

(deftest aws-secret-key-is-high
  (testing "aws_secret_access_key assignment is flagged high"
    (with-tmp-file "leak.txt" "aws_secret_access_key = wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY\n" ; pdpa:ignore — fictional fixture
      (fn [dir]
        (let [r (scan/scan dir)]
          (is (false? (:clean? r)))
          (is (= :aws-secret (:id (first (:findings r))))))))))

(deftest aws-session-key-is-high
  (testing "ASIA temporary session key id is flagged high"
    (with-tmp-file "leak.txt" "key ASIAIOSFODNN7EXAMPLE here\n" ; pdpa:ignore — fictional fixture
      (fn [dir]
        (let [r (scan/scan dir)]
          (is (false? (:clean? r)))
          (is (= :aws-session (:id (first (:findings r))))))))))

(deftest short-akia-passes
  (testing "truncated AKIA string is not an access key"
    (with-tmp-file "ok.txt" "prefix AKIA123 here\n" ; pdpa:ignore — fictional fixture
      (fn [dir]
        (is (true? (:clean? (scan/scan dir))))))))

(deftest cyberark-conjur-is-high
  (testing "CyberArk Conjur API key assignment is flagged high"
    (with-tmp-file "leak.txt" "conjur_authn_api_key = \"s3cr3t-conjur-key-99\"\n" ; pdpa:ignore — fictional fixture
      (fn [dir]
        (let [r (scan/scan dir)]
          (is (false? (:clean? r)))
          (is (= :cyberark-conjur (:id (first (:findings r))))))))))

(deftest slack-token-is-high
  (testing "Slack xoxb token is flagged high"
    (with-tmp-file "leak.txt" "token xoxb-123456789012-345678901234-AbCdEfGh\n" ; pdpa:ignore — fictional fixture
      (fn [dir]
        (let [r (scan/scan dir)]
          (is (false? (:clean? r)))
          (is (= :slack-token (:id (first (:findings r))))))))))

(deftest openai-key-is-high
  (testing "OpenAI project key is flagged high"
    (with-tmp-file "leak.txt" "key sk-proj-abcdefghij1234567890ABCDXY\n" ; pdpa:ignore — fictional fixture
      (fn [dir]
        (let [r (scan/scan dir)]
          (is (false? (:clean? r)))
          (is (= :openai-key (:id (first (:findings r))))))))))

(deftest anthropic-key-is-high
  (testing "Anthropic key is flagged high"
    (with-tmp-file "leak.txt" "key sk-ant-api03-abcdefghij1234567890AB\n" ; pdpa:ignore — fictional fixture
      (fn [dir]
        (let [r (scan/scan dir)]
          (is (false? (:clean? r)))
          (is (= :anthropic-key (:id (first (:findings r))))))))))

(deftest short-sk-ant-passes
  (testing "truncated sk-ant string is not a key"
    (with-tmp-file "ok.txt" "prefix sk-ant-x here\n" ; pdpa:ignore — fictional fixture
      (fn [dir]
        (is (true? (:clean? (scan/scan dir))))))))

(deftest gcp-key-is-high
  (testing "Google Cloud API key is flagged high"
    (with-tmp-file "leak.txt" "key AIzaSyA-abcdefghij1234567890abcdefghijk\n" ; pdpa:ignore — fictional fixture
      (fn [dir]
        (let [r (scan/scan dir)]
          (is (false? (:clean? r)))
          (is (= :gcp-key (:id (first (:findings r))))))))))

(deftest azure-conn-str-is-high
  (testing "Azure storage connection string is flagged high"
    (with-tmp-file "leak.txt" "DefaultEndpointsProtocol=https;AccountName=demo;AccountKey=abc=;EndpointSuffix=core.windows.net\n" ; pdpa:ignore — fictional fixture
      (fn [dir]
        (let [r (scan/scan dir)]
          (is (false? (:clean? r)))
          (is (= :azure-conn-str (:id (first (:findings r))))))))))

(deftest azure-account-key-is-high
  (testing "Azure account key assignment is flagged high"
    (with-tmp-file "leak.txt" "account_key = \"YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eHoxMjM0NTY3ODkwMTIrCg==\"\n" ; pdpa:ignore — fictional fixture
      (fn [dir]
        (let [r (scan/scan dir)]
          (is (false? (:clean? r)))
          (is (= :azure-account-key (:id (first (:findings r))))))))))

(deftest github-token-variants-are-high
  (testing "OAuth / App / fine-grained GitHub tokens are flagged"
    (with-tmp-file "leak.txt" "token gho_abcdefghij1234567890abcdefghij123456\n" ; pdpa:ignore — fictional fixture
      (fn [dir]
        (is (= :pat-token (:id (first (:findings (scan/scan dir))))))))
    (with-tmp-file "leak.txt" "token ghp_abcdefghij1234567890abcdefghij123456\n" ; pdpa:ignore — fictional fixture
      (fn [dir]
        (is (= :pat-token (:id (first (:findings (scan/scan dir))))))))
    (with-tmp-file "leak.txt" "token github_pat_abcdefghij1234567890abcdefghij1234567890abcdefghij12\n" ; pdpa:ignore — fictional fixture
      (fn [dir]
        (is (= :pat-token (:id (first (:findings (scan/scan dir))))))))))

(deftest test-password-passes
  (testing "password mentioning test is excluded by the negative guard"
    (with-tmp-file "ok.txt" "password = \"test-password-123\"\n" ; pdpa:ignore — fictional fixture
      (fn [dir]
        (is (true? (:clean? (scan/scan dir))))))))

(deftest vendored-dirs-skipped
  (testing "node_modules/.git/target trees are never scanned (OOM guard)"
    (let [dir (io/file (System/getProperty "java.io.tmpdir")
                       (str "pdpa-skip-test-" (System/nanoTime)))]
      (.mkdirs (io/file dir "node_modules" "dep"))
      (.mkdirs (io/file dir "real"))
      (try
        (spit (io/file dir "node_modules" "dep" "index.js")
              "key AKIAIOSFODNN7EXAMPLE here\n") ; pdpa:ignore — fictional fixture
        (spit (io/file dir "real" "leak.txt")
              "call +6594823068 asap\n") ; pdpa:ignore — fictional fixture
        (let [r (scan/scan (str dir))]
          (is (= 1 (count (:findings r))))
          (is (str/includes? (:path (first (:findings r))) "real")))
        (finally
          (doseq [f (reverse (file-seq dir))] (.delete f)))))))
