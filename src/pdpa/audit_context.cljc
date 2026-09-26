(ns pdpa.audit-context
  "Portable construction and normalization of audit report context.

   This namespace deliberately does not read files, scan directories, or
   decide legal compliance. Native orchestration supplies a scan result and
   an explicit timestamp; the report renderer consumes the resulting map.")

(def severity-keys
  "Severity keys emitted by the scan result, including the informational
   bucket retained for output compatibility."
  [:critical :high :medium :low :info])

(def compliance-boundary
  "Explicit boundary for the historical native compliance signal.

   `scan-compliant?` is a gate signal based only on critical/high counts;
   it is not a complete PDPA assessment. Manual obligations and evidence
   remain outside this value. The wording used by the native command is
   intentionally preserved during the portability extraction."
  {:status :incomplete
   :signal :critical-and-high-counts
   :complete-assessment? false
   :does-not-cover ["manual checklist obligations" "published-evidence sufficiency"
                    "semantic legal compliance"]})

(defn coverage
  "Return the compliance-signal boundary contract."
  []
  compliance-boundary)

(defn counts
  "Return scan counts with all historical severity buckets present."
  [scan-result]
  (merge (zipmap severity-keys (repeat 0))
         (or (:counts scan-result) {})))

(def normalize-counts counts)

(defn scan-compliant?
  "Historical scanner gate: true when critical and high counts are zero."
  [scan-result]
  (let [c (counts scan-result)]
    (and (zero? (:critical c))
         (zero? (:high c)))))

(def compliant? scan-compliant?)

(defn build
  "Build the context consumed by `pdpa.report`.

   The `:timestamp` value should be injected by the caller. It is kept as
   supplied rather than generated here, so report fixtures and browser/
   worker callers remain deterministic. `:compliant?` is also explicit by
   default; when omitted, the historical scanner gate is used."
  [{:keys [path scan-result evidence compliant? timestamp]}]
  {:path        path
   :scan-result scan-result
   :evidence    (vec (or evidence []))
   :compliant?  (if (some? compliant?)
                  (boolean compliant?)
                  (scan-compliant? scan-result))
   :timestamp   timestamp})

(def make-context build)
(def build-context build)
(def make build)
(def context build)

(defn timestamp
  "Read the injected report timestamp from a context map."
  [context]
  (:timestamp context))

