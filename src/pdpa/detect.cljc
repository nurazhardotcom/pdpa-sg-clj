(ns pdpa.detect
  "Portable, in-memory detection primitives.

  `pdpa.scan` owns process invocation and filesystem traversal. This
  namespace only receives already-read line records, applies the shared
  rule pack, and produces the stable result shape. Keeping that boundary
  explicit lets JVM/Babashka and ClojureScript exercise the same fixtures."
  (:require [clojure.string :as str]
            [pdpa.nric :as nric]
            [pdpa.rules :as rules]))

(def ignore-marker
  "Lines containing this marker are excluded from findings.
   Use for intentional documentation examples:
     sed -i 's/S0466008B/S********G/g' ... # pdpa:ignore — fictional example
   or in Markdown prose:
     <!-- pdpa:ignore --> S0466008B is a fictional example value"
  "pdpa:ignore")

(def coverage-boundary
  "Explicit boundary for the portable detector.

   The detector is intentionally incomplete: it evaluates the configured
   rule pack against text supplied by its caller. It does not walk a
   filesystem, interpret policy documents, infer context, or certify legal
   compliance. Native adapters may add traversal, but they must not imply
   coverage beyond this contract."
  {:status :incomplete
   :scope :configured-rule-pack
   :covers ["caller-supplied text" "NRIC checksum validation" "portable rule-pack patterns"]
   :does-not-cover ["filesystem discovery" "binary-file decoding"
                    "semantic/contextual PII" "policy compliance certification"]})

(defn coverage
  "Return the detector's explicit coverage boundary."
  []
  coverage-boundary)

(defn ignored?
  "True when a line carries the intentional-example ignore marker."
  [text]
  (str/includes? (or text "") ignore-marker))

(defn excluded?
  "True when `path` contains any of the supplied exclusion substrings."
  [path excludes]
  (boolean (some #(str/includes? (str path) (str %)) excludes)))

(defn- portable-pattern-parts
  "Split the leading Java inline case-insensitive flag from a pattern.

   Rule-pack strings remain unchanged for external exporters. Detection
   compiles them through this boundary so the same strings work with both
   the JVM and JavaScript regular-expression engines."
  [pattern]
  (if (str/starts-with? pattern "(?i)")
    [(subs pattern 4) true]
    [pattern false]))

(defn- compile-pattern [pattern]
  (let [[pattern case-insensitive?] (portable-pattern-parts pattern)]
    #?(:clj (re-pattern (if case-insensitive?
                          (str "(?i)" pattern)
                          pattern))
       :cljs (js/RegExp. pattern (if case-insensitive? "i" "")))))

(defn compile-matcher
  "Compile one data rule into `{:id :label :sev :match-fn}`.

   Code-backed rules retain the Mod-11 validator. Pattern rules retain the
   historical whole-text exclusion behaviour: a negative guard suppresses
   the line when it matches anywhere in that line."
  [{:keys [id label sev pattern exclude code]}]
  {:id id
   :label label
   :sev sev
   :match-fn (cond
               (= :nric-valid code)
               (fn [text _path] (seq (nric/find-valid-nrics text)))

               (some? pattern)
               (let [matcher (compile-pattern pattern)
                     exclusion (when exclude (compile-pattern exclude))]
                 (fn [text _path]
                   (let [value (or text "")]
                     (boolean
                      (and (re-find matcher value)
                           (not (and exclusion (re-find exclusion value))))))))

               :else
               (throw (ex-info (str "Rule has neither :pattern nor :code: " id)
                               {:rule id})))})

(def rule-matchers
  "Matchers compiled from `pdpa.rules/rule-pack`, in rule-pack order."
  (mapv compile-matcher rules/rule-pack))

(def compile-rule compile-matcher)

(defn classify
  "Return the first rule matching `text`, or nil.

   Rule order is part of the existing behavior and is deliberately not
   sorted or combined here."
  ([text] (classify text nil))
  ([text path]
   (some (fn [rule]
           (when ((:match-fn rule) text path) rule))
         rule-matchers)))

(defn line->finding
  "Convert one caller-supplied line record into a finding, or nil."
  [{:keys [text path line] :as record}]
  (when-not (or (ignored? text)
                (excluded? path (:excludes record)))
    (when-let [rule (classify text path)]
      {:severity (:sev rule)
       :id       (:id rule)
       :label    (:label rule)
       :path     path
       :line     line})))

(def finding line->finding)

(defn result
  "Build the stable scan result from line records.

   `line-records` is a sequence of maps with `:text`, `:path`, and
   `:line`. The optional `:excludes` sequence has the same substring
   semantics as the native scanner. No process, filesystem, or JSON API
   is used here."
  ([line-records] (result line-records {}))
  ([line-records {:keys [excludes]}]
   (let [records (map #(if (contains? % :excludes)
                         %
                         (assoc % :excludes excludes))
                      line-records)
         findings (keep line->finding records)
         grouped  (group-by :severity findings)
         counts   (reduce-kv (fn [m severity values]
                               (assoc m severity (count values)))
                             {}
                             grouped)
         counts   (merge (zipmap [:critical :high :medium :low :info]
                                 (repeat 0))
                         counts)]
     {:findings findings
      :counts   counts
      :clean?   (and (zero? (:critical counts))
                     (zero? (:high counts)))})))

(def scan
  "Alias for `result`, useful to callers that treat detection as a scan."
  result)

(def aggregate result)
