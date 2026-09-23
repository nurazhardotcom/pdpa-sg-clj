(ns pdpa.nric
  "Singapore NRIC / FIN detection with canonical ICA Modulo-11 algorithm.

  Why Mod-11?  Because the loose regex `[STFG]\\d{7}[A-Z]` matches roughly
  1 in every 36M random 9-character windows — including a non-trivial
  fraction of hex strings (SHA-256 chunks, git short hashes, BSV txid
  prefixes). The Mod-11 check-digit algorithm filters structural false
  positives down to ~1 in 11 of any structural match.

  Algorithm (published Singapore NRIC/FIN Mod-11, cross-checked against
  independent validators — see CHANGELOG):
    Weights [2 7 6 5 4 3 2] over the 7 digits for S / T / F / G.
    Prefix offset: +4 for T / G only; S / F take +0.
      idx = (sum + offset) mod 11
      S / T check-letter = \"JZIHGFEDCBA\"[idx]
      F / G check-letter = \"XWUTRQPNMLK\"[idx]

    Prefix M (FIN — foreigners):
      weights = [1 2 7 6 5 4 3 2]   (one extra weight for the prefix)
      The M prefix contributes value 3 to position 0
      (i.e. 3 × 1 = 3 added to the sum); +4 offset kept as-shipped
      (no published M reference found — see CHANGELOG).
      check-letter = \"XWUTRQPNMLK\"[idx]

  Reference value validation in REPL:
    (valid? \"S0100000D\")  ;; => true   (sum=7; 7 mod 11 = 7 → 'D')  pdpa:ignore fictional example
    (valid? \"F0000002R\")  ;; => true   (sum=4; 4 mod 11 = 4 → 'R')  pdpa:ignore fictional example
    (valid? \"S0000000Z\")  ;; => false  (sum=0; 0 mod 11 = 0 → 'J')
    (valid? \"deadbeefF\")  ;; => false  (hex false-positive guard works)"
  (:require [clojure.string :as str]))

(def nric-re
  ;; Singapore NRIC (citizens / PRs): \b S/T/F/G + 7 digits + check letter
  ;; FIN     (foreigners):           \b M       + 7 digits + check letter
  #"(?i)\b[STFGM]\d{7}[A-Z]\b")

(def ^:private citizen-chars  "JZIHGFEDCBA")  ; S / T
(def ^:private foreigner-chars "XWUTRQPNMLK") ; F / G / M

(defn nric-string?
  "True iff `s` structurally matches the Singapore NRIC/FIN shape."
  [s]
  (boolean (re-find nric-re s)))

(defn- digits [s]
  (mapv #(Integer/parseInt (str %)) (re-seq #"\d" s)))

(defn- prefixed-weights [c]
  (case c
    (\S \T \F \G) [2 7 6 5 4 3 2]
    \M            [1 2 7 6 5 4 3 2]
    nil))

(defn check-digit
  "Given a Singapore NRIC/FIN `nric`, return its ICA-computed check
  letter (uppercase) or nil if the input doesn't match the structural shape."
  [nric]
  (when-let [s (some-> nric str .toUpperCase)]
    (when (re-matches nric-re s)
      (let [prefix    (first s)
            d         (digits (subs s 1 8))
            weights   (prefixed-weights prefix)
            ;; For M-prefix, prepend value 3 to the digit vector
            input     (if (= prefix \M) (cons 3 d) d)
            sum       (reduce + (map * input weights))
            ;; Published offsets: +4 for T/G (and M, kept as-shipped);
            ;; S/F take no offset.
            offset    (if (contains? #{\T \G \M} prefix) 4 0)
            idx       (mod (+ sum offset) 11)
            chars     (if (contains? #{\S \T} prefix)
                        citizen-chars
                        foreigner-chars)]
        (nth chars idx)))))

(defn valid?
  "True iff `s` is a Singapore NRIC/FIN AND its last character agrees
  with the ICA Mod-11 algorithm.  This is the false-positive guard.
  Returns false for nil / empty input."
  [s]
  (when-let [computed (check-digit s)]
    (let [provided (str/upper-case (str (last s)))]
      (= (str computed) provided))))

(defn find-valid-nrics
  "Returns a vector of NRIC/FIN strings in `s` whose Mod-11 checksum is
  valid.  Skips structural matches that fail the check-digit (hex string
  guard)."
  [s]
  (->> (re-seq nric-re (or s ""))
       (filter valid?)
       distinct
       vec))

;; ---------------------------------------------------------------------
;; REPL examples
;; ---------------------------------------------------------------------

(comment
  (require '[pdpa.nric :as n])

  ;; Positives (Mod-11 valid):
  (n/valid? "S0100000D")  ;; => true  ; pdpa:ignore — fictional example
  (n/valid? "F0000002R")  ;; => true  ; pdpa:ignore — fictional example
  (n/valid? "S1234567D")  ;; => true  ; pdpa:ignore — canonical community vector
  (n/valid? "G0000002M")  ;; => true  ; pdpa:ignore — pins the MLK tail

  ;; Negatives (structural match, checksum fails):
  (n/valid? "S0100000J")  ;; => false (previous +4-for-S output)
  (n/valid? "S0000000Z")  ;; => false
  (n/valid? "deadbeefF")  ;; => false (hex false-positive guard)
  (n/valid? "X1234567A")  ;; => nil  (prefix not in {S,T,F,G})
  (n/valid? "S12345A")    ;; => nil  (less than 7 digits)

  ;; Bulk finding:
  (n/find-valid-nrics
    "User S0100000D and S0000000Z and deadbeefdeadbeefF applied.")  ; pdpa:ignore — fictional example
  ;; => ["S0100000D"]  ; pdpa:ignore — fictional example
  )
