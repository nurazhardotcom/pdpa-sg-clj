(ns pdpa.nric
  "Singapore NRIC / FIN detection with canonical ICA Modulo-11 algorithm.

  Why Mod-11? Because the loose regex `[STFG]\\d{7}[A-Z]` matches roughly
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

    Prefix M (FIN — foreigners, issued 2022+):
      Same 7-digit weights [2 7 6 5 4 3 2]; prefix offset +3.
      check-letter = \"XWUTRQPNJLK\"[idx] (own table: J at idx 8, not M).

  Reference value validation in REPL:
    (valid? \"S0100000D\")  ;; => true   (sum=7; 7 mod 11 = 7 → 'D')  pdpa:ignore fictional example
    (valid? \"F0000002R\")  ;; => true   (sum=4; 4 mod 11 = 4 → 'R')  pdpa:ignore fictional example
    (valid? \"S0000000Z\")  ;; => false  (sum=0; 0 mod 11 = 0 → 'J')
    (valid? \"deadbeefF\")  ;; => false  (hex false-positive guard works)"
  (:require [clojure.string :as str]))

(def nric-re
  "Case-insensitive structural NRIC/FIN expression.

   The public value remains a regex on both platforms; only its construction
   differs because JavaScript RegExp has no inline `(?i)` flag."
  #?(:clj #"(?i)\b[STFGM]\d{7}[A-Z]\b"
     :cljs (js/RegExp. "\\b[STFGM]\\d{7}[A-Z]\\b" "i")))

(def ^:private citizen-chars   "JZIHGFEDCBA")  ; S / T
(def ^:private foreigner-chars "XWUTRQPNMLK")  ; F / G
(def ^:private m-series-chars  "XWUTRQPNJLK")  ; M (differs from F/G at idx 8)

(defn nric-string?
  "True iff `s` structurally matches the Singapore NRIC/FIN shape."
  [s]
  (boolean (re-find nric-re s)))

(defn- digit-value [ch]
  (case (str ch)
    "0" 0 "1" 1 "2" 2 "3" 3 "4" 4
    "5" 5 "6" 6 "7" 7 "8" 8 "9" 9
    (throw (ex-info (str "Not an ASCII digit: " (pr-str ch)) {:digit ch}))))

(defn- digits [s]
  ;; Avoid JVM/JS integer parsing: this function is shared by both hosts.
  (mapv digit-value (re-seq #"\d" s)))

(def ^:private digit-weights [2 7 6 5 4 3 2])

(def ^:private prefix-offsets
  "Published Mod-11 prefix offsets, corroborated by independent validators."
  {\S 0 \T 4 \F 0 \G 4 \M 3})

(defn check-digit
  "Given a Singapore NRIC/FIN `nric`, return its ICA-computed check
  letter (uppercase) or nil if the input doesn't match the structural shape."
  [nric]
  (when-let [s (some-> nric str str/upper-case)]
    (when (re-matches nric-re s)
      (let [prefix    (first s)
            d         (digits (subs s 1 8))
            sum       (reduce + (map * d digit-weights))
            offset    (get prefix-offsets prefix 0)
            idx       (mod (+ sum offset) 11)
            chars     (cond (= prefix \M) m-series-chars
                            (contains? #{\S \T} prefix) citizen-chars
                            :else foreigner-chars)]
        (nth chars idx)))))

(defn check-letter
  "Return the computed check letter as a string on every platform."
  [nric]
  (some-> (check-digit nric) str))

(defn valid?
  "True iff `s` is a Singapore NRIC/FIN AND its last character agrees
  with the ICA Mod-11 algorithm. This is the false-positive guard.
  Returns false for nil / empty input."
  [s]
  (when-let [computed (check-digit s)]
    (let [provided (str/upper-case (str (last s)))]
      (= (str computed) provided))))

(defn find-valid-nrics
  "Returns a vector of NRIC/FIN strings in `s` whose Mod-11 checksum is
  valid. Skips structural matches that fail the check-digit (hex string
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
  (n/valid? "M5012345J")  ;; => true  ; pdpa:ignore — corroborated M anchor

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
