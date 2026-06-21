(ns pdpa.checklist
  "Reads `CHECKLIST.md`, applies auto-ticks based on scan results, writes
  updated file back. The single source of truth for compliance status."
  (:require [clojure.string :as str]
            [pdpa.nric :as nric]))

;; ---------------------------------------------------------------------
;; Marker → obligation verification
;; ---------------------------------------------------------------------
;;
;; Each obligation's auto-tick condition is checked against the latest
;; scan result + supplied evidence (e.g. "DPO_CONTACT.md was published").
;;
;; The HTML comments in CHECKLIST.md map 1-to-1 to verifier names here.

(def ^:private verifiers
  ;; vert: scan-context + a small bag of evidence -> boolean
  {:consent
   (fn [{:keys [scan evidence]}]
     (boolean (or (some #(= "CONSENT_FORM" %) evidence)
                  (zero? (get-in scan [:counts :critical])))))

   :purpose
   (fn [{:keys [scan]}]
     (zero? (get-in scan [:counts :medium])))

   :notification
   (fn [{:keys [evidence]}]
     (boolean (some #(= "PRIVACY_POLICY" %) evidence)))

   :protection
   (fn [{:keys [scan evidence]}]
     (and (zero? (get-in scan [:counts :critical]))
          (zero? (get-in scan [:counts :high]))
          (boolean (some #(= "SECURITY_HARDENING" %) evidence))))

   :retention
   (fn [{:keys [evidence]}]
     (boolean (some #(= "RETENTION_SCHEDULE" %) evidence)))

   :breach
   (fn [{:keys [evidence]}]
     (boolean (some #(= "BREACH_PLAN" %) evidence)))

   :dpo
   (fn [{:keys [evidence]}]
     (boolean (some #(= "DPO_CONTACT" %) evidence)))})

;; ---------------------------------------------------------------------
;; Auto-tick signature line:  "<!-- agent:verify-protection -->"
;; Marker keyword = protection. Maps to (verifiers :protection ctx).
;; ---------------------------------------------------------------------

(defn- markers-in-line [line]
  (map (fn [m] (keyword (second m)))
       (re-seq #"<!--\s*agent:verify-([a-z\-]+)\s*-->" line)))

;; ---------------------------------------------------------------------
;; Walk the CHECKLIST.md line-by-line. Under each H2 heading (## N. ...),
;; the FIRST unmarked `[ ]` after `<!-- agent:verify-X -->` is auto-ticked
;; iff the verifier returns true. Manual ticks (lines without markers)
;; are untouched.
;; ---------------------------------------------------------------------

(defn auto-tick
  "Returns the updated CHECKLIST content as a single string."
  [md scan-result evidence]
  (let [lines (str/split-lines md)
        ctx   {:scan scan-result :evidence (set evidence)}]
    (loop [[line & rest] lines
           current-obligation nil
           saw-marker?        false
           output             (transient [])]
      (if (nil? line)
        (str/join "\n" (persistent! output))
        (let [cleaned line
              markers (markers-in-line line)
              trimmed (str/triml line)]
          (cond
            ;; H2 heading starts a new obligation
            (str/starts-with? trimmed "## ")
            (let [ob (let [m (re-find #"##\s+\d+\.\s+([A-Za-z ]+)" trimmed)]
                       (when m (some-> m second str/lower-case keyword)))]
              (recur rest
                     ob
                     false
                     (conj! output cleaned)))

            ;; Marker line — record that next [ ] is auto-tickable
            (seq markers)
            (let [m (or (first markers) current-obligation)]
              (recur rest m true (conj! output cleaned)))

            ;; Auto-tick candidate line (immediate [ ] after marker)
            (and saw-marker?
                 current-obligation
                 (str/includes? line "- [ ]")
                 (some? (current-obligation verifiers))
                 ((current-obligation verifiers) ctx))
            (do
              (recur rest
                     current-obligation
                     false
                     (conj! output (str/replace line "- [ ]" "- [x]" 1))))

            ;; Default: pass through untouched
            :else
            (recur rest current-obligation false (conj! output cleaned))))))))

;; ---------------------------------------------------------------------
;; CHECKLIST status summary (used in `bb checklist` and `pdpa/checklist-status`)
;; ---------------------------------------------------------------------

(defn status
  "Returns a map of obligation-keyword -> {:status \"OK\"|\"PENDING\",
                                          :ticked N, :total N, :manual N}"
  [md]
  (let [lines  (str/split-lines md)
        groups (loop [ls lines
                      current nil
                      acc {}]
                 (if (empty? ls)
                   acc
                   (let [l (str/triml (first ls))]
                     (cond
                       (str/starts-with? l "## ")
                       (let [m     (re-find #"##\s+\d+\.\s+([A-Za-z ]+)" l)
                             key  (some-> m second str/lower-case keyword)]
                         (recur (rest ls) key (assoc acc key {:ticked 0 :total 0 :manual 0})))

                       (str/includes? l "- [x]")
                       (recur (rest ls) current
                              (-> acc
                                  (update-in [current :ticked] inc)
                                  (update-in [current :total]  inc)))

                       (str/includes? l "- [ ]")
                       (recur (rest ls) current
                              (-> acc
                                  (update-in [current :total]  inc)
                                  (update-in [current :manual] inc)))

                       :else
                       (recur (rest ls) current acc)))))]
    (->> groups
         (map (fn [[k {:keys [ticked total]}]]
                [k {:status (if (and (pos? total) (= ticked total)) "OK" "PENDING")
                    :ticked  ticked
                    :total   total
                    :ratio   (if (zero? total) 0.0 (double (/ ticked total)))}]))
         (into {}))))

(defn run
  "Babashka entry point. Prints CHECKLIST.md status summary."
  [_]
  (let [md (slurp "CHECKLIST.md")
        st (status md)]
    (println "[CHECKLIST] Singapore PDPA compliance status")
    (println (format "  %-30s %-8s %s" "Obligation" "Status" "Progress"))
    (println (apply str (repeat 60 "-")))
    (doseq [[k v] (sort-by (fn [[k _]]
                             (Integer/parseInt
                              (or (str (re-find #"\d+" (name k)))
                                  "99")))
                           st)]
      (println (format "  %-30s %-8s %d/%d"
                       (clojure.string/capitalize (clojure.string/replace (name k) #"-" " "))
                       (:status v)
                       (:ticked v) (:total v))))
    (let [ok    (count (filter #(= "OK" (val (first %))) st))
          total (count st)]
      (println (format "[OK] %d/%d obligations satisfied" ok total)))))
