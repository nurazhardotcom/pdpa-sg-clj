(ns pdpa.report
  "Executive / auditor report rendering.

  Emits Markdown and standalone, print-friendly HTML from an audit
  context map. Pure string templating + `pdpa.version` — no extra deps,
  runs on Babashka. Auditors convert to PDF for ISO 27001 / MAS TRM
  filings with one of (documented in README):

    pandoc audit.md -o audit.pdf
    google-chrome --headless --print-to-pdf=audit.pdf audit.html"
  (:require [clojure.string :as str]
            [pdpa.version :as version]))

(defn now-stamp
  "Current UTC instant as an ISO-8601 string. BB + JVM safe."
  []
  (str (java.time.Instant/now)))

(defn- esc
  "Minimal HTML escaping for interpolated values."
  [s]
  (-> (str s)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")))

(defn audit->markdown
  "Audit context → Markdown executive summary. Context keys: :path,
  :scan-result ({:findings :counts :clean?}), :evidence (seq of strings),
  :compliant? bool, :timestamp string."
  [{:keys [path scan-result evidence compliant? timestamp]}]
  (let [c      (or (:counts scan-result) {})
        crit   (or (:critical c) 0)
        high   (or (:high c) 0)
        med    (or (:medium c) 0)
        low    (or (:low c) 0)
        ev     (or (seq evidence) ["(none)"])
        f-row  (fn [{:keys [severity label path line]}]
                 (format "| %s | %s | %s:%s |"
                         (str/upper-case (name (or severity :info)))
                         (or label "") (or path "") (or line "")))]
    (str "# PDPA compliance audit report\n\n"
         "- **Toolkit:** " (version/banner) "\n"
         "- **Date (UTC):** " (or timestamp (now-stamp)) "\n"
         "- **Scope:** `" (or path ".") "`\n"
         "- **Verdict:** " (if compliant? "✅ COMPLIANT" "⚠️ ACTION REQUIRED") "\n\n"
         "## Finding counts\n\n"
         "| Severity | Count |\n|---|---|\n"
         (format "| Critical | %d |\n| High | %d |\n| Medium | %d |\n| Low | %d |\n\n"
                 crit high med low)
         "## Published evidence\n\n"
         (str/join "\n" (map #(str "- [x] " %) ev))
         "\n\n## Findings\n\n"
         (if (seq (:findings scan-result))
           (str "| Severity | Rule | Location |\n|---|---|---|\n"
                (str/join "\n" (map f-row (:findings scan-result)))
                "\n")
           "_No findings._\n")
         "\n_Compliance guidance only — not legal advice._\n")))

(def ^:private html-css
  "body{font-family:-apple-system,'Segoe UI',Helvetica,Arial,sans-serif;max-width:960px;margin:2rem auto;padding:0 1rem;color:#1f2328}
h1{border-bottom:2px solid #1f2328;padding-bottom:.3rem}
table{border-collapse:collapse;width:100%;margin:1rem 0}
th,td{border:1px solid #d0d7de;padding:.4rem .6rem;text-align:left;font-size:.9rem}
th{background:#f6f8fa}
.badge{display:inline-block;padding:.2rem .6rem;border-radius:1rem;font-weight:600}
.ok{background:#dafbe1;color:#116329}
.warn{background:#fff8c5;color:#7d4e00}
.meta{color:#57606a;font-size:.9rem}
@media print{body{margin:0;max-width:none}}")

(defn audit->html
  "Audit context → standalone HTML executive report (inline CSS,
  print-to-PDF friendly). Same context keys as `audit->markdown`."
  [{:keys [path scan-result evidence compliant? timestamp] :as ctx}]
  (let [c    (or (:counts scan-result) {})
        getc (fn [k] (or (get c k) 0))
        ev   (or (seq evidence) ["(none)"])
        f-row (fn [{:keys [severity label path line]}]
                (str "<tr><td>" (esc (str/upper-case (name (or severity :info))))
                     "</td><td>" (esc (or label ""))
                     "</td><td>" (esc (str (or path "") ":" (or line "")))
                     "</td></tr>"))]
    (str "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n"
         "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n"
         "<title>PDPA compliance audit report</title>\n"
         "<style>" html-css "</style>\n</head>\n<body>\n"
         "<h1>PDPA compliance audit report</h1>\n"
         "<p class=\"meta\">" (esc (version/banner)) " · "
         (esc (or timestamp (now-stamp))) " · scope <code>"
         (esc (or path ".")) "</code></p>\n"
         "<p><span class=\"badge " (if compliant? "ok\">✅ COMPLIANT" "warn\">⚠️ ACTION REQUIRED")
         "</span></p>\n"
         "<h2>Finding counts</h2>\n<table>\n"
         "<tr><th>Severity</th><th>Count</th></tr>\n"
         (str/join "\n" (map (fn [[s k]]
                               (str "<tr><td>" s "</td><td>" (getc k) "</td></tr>"))
                             [["Critical" :critical] ["High" :high]
                              ["Medium" :medium] ["Low" :low]]))
         "\n</table>\n<h2>Published evidence</h2>\n<ul>\n"
         (str/join "\n" (map #(str "<li>" (esc %) "</li>") ev))
         "\n</ul>\n<h2>Findings</h2>\n"
         (if (seq (:findings scan-result))
           (str "<table>\n<tr><th>Severity</th><th>Rule</th><th>Location</th></tr>\n"
                (str/join "\n" (map f-row (:findings scan-result)))
                "\n</table>\n")
           "<p><em>No findings.</em></p>\n")
         "<p class=\"meta\">Compliance guidance only — not legal advice.</p>\n"
         "</body>\n</html>\n")))
