#!/usr/bin/env bb
;; blog-scan.bb — Scan blog posts before publishing to prevent PII leaks.
;; Usage: bb scripts/blog-scan.bb <blog-dir>
;;        bb scripts/blog-scan.bb ~/Repositories/homepage
;;
;; Uses pdpa.scan (ripgrep-backed) to detect:
;;   - Valid NRIC/FIN numbers (Mod-11 checksum)
;;   - SG phone numbers with country code
;;   - Private keys, API tokens, credentials
;;   - Email addresses (excluding test/example domains)
;;
;; Exit 0 = clean, exit 1 = findings detected.
;; Intended to run BEFORE `git push` or in CI before deploying the blog.

(require '[clojure.string :as str]
         '[cheshire.core :as json]
         '[pdpa.scan :as scan])

;; ─── Configuration ──────────────────────────────────────────────────────────

(def blog-dir (or (first *command-line-args*) "."))
(def output-json? (some #{"--json"} *command-line-args*))

;; ─── Main ───────────────────────────────────────────────────────────────────
;; Reuse pdpa.scan/scan directly — it already walks files, classifies by
;; severity (critical/high/medium/low), and returns structured results.

(let [result (scan/scan blog-dir)
      {:keys [findings counts clean?]} result
      c (merge {:critical 0 :high 0 :medium 0 :low 0} counts)]

  (if output-json?
    (println (json/generate-string {:findings findings
                                    :counts c
                                    :clean? clean?
                                    :blog-dir blog-dir}))
    (do
      (println (format "[BLOG-SCAN] %s — clean? %s" blog-dir clean?))
      (println (format "  counts: critical=%d high=%d medium=%d low=%d"
                       (:critical c) (:high c)
                       (:medium c) (:low c)))
      (doseq [{:keys [severity label path line]} findings]
        (println (format "  [%s] %s:%d — %s"
                         (str/upper-case (name severity))
                         path line label)))
      (when-not clean?
        (println)
        (println "[BLOCKED] Fix findings before publishing to public blog.")
        (println "  Use `bb redact <file>` to redact NRIC/phone/email in-place."))))

  (System/exit (if clean? 0 1)))
