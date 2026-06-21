(ns pdpa.shipit
  "CI-friendly orchestrator that gives a binary ship/no-ship verdict.

  Unlike `audit`, `shipit` is strictly read-only on the target codebase
  (intercepts `clojure.core/spit` as defense-in-depth on the scan step)
  so CI runs never mutate `PDPA_CHECKLIST.md` or any other source file.

  If the target lacks the PDPA templates (`PDPA_CHECKLIST.md`),
  `shipit` bootstraps the templates once via `pdpa.init` BEFORE
  scanning. By default bootstrap is OFF (fail safe — a read-only
  CI gate must never write to the user's code). Two opt-ins:
    --bootstrap flag, or
    `PDPA_SHIPIT_BOOTSTRAP=1|true|yes` env var (case-insensitive).

  Usage:
    bb shipit <path>                ;; human one-line verdict
    bb shipit <path> --json         ;; machine-readable JSON verdict
    bb shipit <path> --bootstrap    ;; bootstrap missing templates first

  Exit code: 0 on SHIP, 1 on NO-SHIP. The `bb.edn` `shipit` task is
  the only thing that exits — this namespace returns the verdict
  map so the function remains REPL-friendly and library-composable."
  (:require [cheshire.core   :as json]
            [clojure.java.io :as io]
            [clojure.string  :as str]
            [pdpa.init       :as init]
            [pdpa.scan       :as scan]
            [pdpa.version    :as version]))

;; -------------------------------------------------------------------------
;; Argument parsing — minimal, no babashka.cli dep (keeps ns analyzable).
;; -------------------------------------------------------------------------

(defn- target-arg
  "First positional argument (anything not starting with `-`). Defaults to `.`."
  [args]
  (or (first (remove #(str/starts-with? ^String % "-") args))
      "."))

(defn- json-output?
  [args]
  (boolean (some #{"-j" "--json"} args)))

(defn- has-templates?
  "True iff the target directory already has PDPA templates installed
  (either the toolkit's `PDPA_CHECKLIST.md` from a prior init, or a
  custom `CHECKLIST.md`)."
  [path]
  (let [root (io/file path)]
    (and (.isDirectory root)
         (or (.exists (io/file root "PDPA_CHECKLIST.md"))
             (.exists (io/file root "CHECKLIST.md"))))))

(defn- bootstrap-opt-in?
  "Bootstrap is OFF by default to keep shipit strictly read-only on CI
  runs. Two opt-ins: the `--bootstrap` CLI flag, or the
  `PDPA_SHIPIT_BOOTSTRAP` env var set to a truthy value: 1, true, yes
  (case-insensitive)."
  [args]
  (boolean
    (or (some #{"-b" "--bootstrap"} args)
        (let [v (some-> (System/getenv "PDPA_SHIPIT_BOOTSTRAP")
                        str/lower-case)]
          (and v (contains? #{"1" "true" "yes"} v))))))

;; -------------------------------------------------------------------------
;; Scan invocation (direct, bypassing bb audit's --json path which
;; actually emits human output, not JSON).
;; -------------------------------------------------------------------------

(defn- run-scan
  "Invoke `pdpa.scan/scan` directly and return its structured map
  `{:findings ... :counts {...} :clean? ...}`. Returns nil on exception.

  Stdout noise from scan's `println` calls is silenced by rebinding
  `*out*` to a `StringWriter`; we still want scan's RETURN value
  (the structured map), so we cannot use `with-out-str` (which
  returns the captured string, not the inner form's value)."
  [path]
  (try
    (binding [*out* (java.io.StringWriter.)]
      (with-redefs [clojure.core/spit (constantly nil)]
        (scan/scan path)))
    (catch Exception _ nil)))

;; -------------------------------------------------------------------------
;; Verdict computation
;; -------------------------------------------------------------------------

(defn- compute-verdict
  "Returns a map describing the ship/no-ship decision:

    {:verdict         \"SHIP\"|\"NO-SHIP\"
     :counts          {:critical N :high N :medium N :low N}
     :scan            <full scan result, may be nil on exception>
     :bootstrap-needed bool
     :shipit-version  toolkit + rule version banner
     :error           <string, only on pre-scan failure>
     :path            <string, only on error>}"
  [path args]
  (let [root-f (io/file path)]
    (cond
      (not (.exists root-f))
      {:verdict      "NO-SHIP"
       :error        "target not found"
       :path         (str path)
       :shipit-version (version/banner)}

      (not (.isDirectory root-f))
      {:verdict      "NO-SHIP"
       :error        "target is not a directory"
       :path         (str path)
       :shipit-version (version/banner)}

      :else
      (let [bootstrap-needed (not (has-templates? path))]
        (cond
          ;; Bootstrap required but NOT opted-in: fail safe, do NOT write.
          (and bootstrap-needed (not (bootstrap-opt-in? args)))
          {:verdict          "NO-SHIP"
           :error            "target has no PDPA_CHECKLIST.md; run `bb init <path>` (or pass --bootstrap to shipit) before shipping"
           :path             (str path)
           :bootstrap-needed true
           :shipit-version   (version/banner)}

          :else
          (do
            (when bootstrap-needed
              (init/run [path]))
            (let [scan-result (run-scan path)
                  counts      (or (:counts scan-result) {})
                  clean?      (boolean (:clean? scan-result))]
              {:verdict          (if clean? "SHIP" "NO-SHIP")
               :counts           counts
               :scan             scan-result
               :bootstrap-needed bootstrap-needed
               :shipit-version   (version/banner)})))))))

;; -------------------------------------------------------------------------
;; Human-friendly verdict line
;; -------------------------------------------------------------------------

(defn- format-human
  "Single-line verdict for terminal output. shipit-version is included
  so a CI log can grep for the toolkit version that emitted the verdict."
  [{:keys [verdict counts bootstrap-needed error path shipit-version]}]
  (cond
    error
    (str "❌ NO-SHIP: " error " — " (or path ""))

    (= verdict "SHIP")
    (format "✅ SHIP: critical=%d high=%d medium=%d low=%d%s | %s"
            (get counts :critical 0)
            (get counts :high 0)
            (get counts :medium 0)
            (get counts :low 0)
            (if bootstrap-needed " (bootstrap applied)" "")
            shipit-version)

    :else
    (format "❌ NO-SHIP: critical=%d high=%d medium=%d low=%d — fix findings before shipping. | %s"
            (get counts :critical 0)
            (get counts :high 0)
            (get counts :medium 0)
            (get counts :low 0)
            shipit-version)))

;; -------------------------------------------------------------------------
;; CLI entry point — wired in bb.edn as `shipit`
;; -------------------------------------------------------------------------

(defn run
  "Babashka entry point. CLI args: `[<path>] [--json] [--bootstrap]`.

  Returns the verdict map (rather than `(System/exit)`-ing). The
  `bb.edn` `shipit` shim is the only thing that exits. This keeps
  the function REPL-friendly and library-composable, consistent with
  sibling namespaces (`pdpa.scan/run`, `pdpa.init/run`,
  `pdpa.audit/run` — none of them terminate the JVM)."
  [args]
  (let [json?   (json-output? args)
        target  (target-arg args)
        verdict (compute-verdict target args)]
    (if json?
      (println (json/generate-string verdict))
      (println (format-human verdict)))
    verdict))
