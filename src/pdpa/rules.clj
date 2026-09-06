(ns pdpa.rules
  "Tool-independent detection rule pack.

  Every rule is PURE DATA — no backend imports, no `rg`, no Babashka.
  `pdpa.scan` compiles `:pattern` strings to matchers at load; exporters
  (`->gitleaks-toml`, `->rules-json`) ship the same pack to other tools,
  so one rule list drives Babashka, JVM Clojure, gitleaks, and IDE configs
  identically.

  Rule map keys:
    :id      keyword, stable across exports (SARIF ruleId = \"pdpa/<id>\")
    :label   human-readable finding label
    :sev     :critical | :high | :medium | :low
    :kind    :pii | :secret  (secret rules are DevSecOps-exportable)
    :pattern regex STRING (nil for code-backed rules)
    :exclude regex STRING, optional negative guard (same-line)
    :code    keyword naming a code-backed validator (only :nric-valid today)
    :refs    documentation links for the finding"
  (:require [clojure.string :as str]))

(def rule-pack
  "The full detection pack: PII (PDPA) + secrets (DevSecOps)."
  [;; ---------------- PII (PDPA) ----------------
   {:id      :nric-live
    :label   "Live Singapore NRIC / FIN (Mod-11 valid)"
    :sev     :critical
    :kind    :pii
    :pattern nil
    :code    :nric-valid
    :refs    ["https://www.pdpc.gov.sg/guidelines-and-consultation/2024/08/advisory-guidelines-on-the-use-of-nric-numbers"]}

   {:id      :phone-sg
    :label   "Singapore phone number with country code (+65)"
    :sev     :critical
    :kind    :pii
    :pattern "\\+65\\s?[89]\\d{7}"
    :refs    []}

   {:id      :email-live
    :label   "Email address in source code"
    :sev     :low
    :kind    :pii
    :pattern "[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}"
    :exclude "@example\\.(com|org|net)"
    :refs    []}

   ;; ---------------- Secrets (DevSecOps) ----------------
   {:id      :aws-key
    :label   "AWS access key id"
    :sev     :high
    :kind    :secret
    :pattern "AKIA[0-9A-Z]{16}"
    :refs    ["https://docs.aws.amazon.com/IAM/latest/UserGuide/id_credentials_access-keys.html"]}

   {:id      :aws-secret
    :label   "AWS secret access key assignment"
    :sev     :high
    :kind    :secret
    :pattern "(?i)aws_secret_access_key['\"]?\\s*[:=]\\s*['\"]?[A-Za-z0-9/+=]{40}"
    :refs    ["https://docs.aws.amazon.com/IAM/latest/UserGuide/id_credentials_access-keys.html"]}

   {:id      :aws-session
    :label   "AWS temporary session key id (ASIA…)"
    :sev     :high
    :kind    :secret
    :pattern "ASIA[0-9A-Z]{16}"
    :refs    ["https://docs.aws.amazon.com/IAM/latest/UserGuide/id_credentials_temp.html"]}

   {:id      :jwt-token
    :label   "JSON Web Token (three-segment bearer token)"
    :sev     :high
    :kind    :secret
    :pattern "eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_\\-=]{10,}"
    :refs    ["https://www.rfc-editor.org/rfc/rfc7519.html"]}

   {:id      :cyberark-conjur
    :label   "CyberArk Conjur API key assignment"
    :sev     :high
    :kind    :secret
    :pattern "(?i)conjur[^\\n]{0,80}?api[_-]?key\\s*[:=]\\s*['\"][^'\"]{8,}['\"]"
    :refs    ["https://docs.cyberark.com/conjur-enterprise/latest/en/content/developer/conjur-orm-conjur-api.html"]}

   {:id      :slack-token
    :label   "Slack token (xoxb/xoxp/xoxa/xoxs)"
    :sev     :high
    :kind    :secret
    :pattern "xox[bpas]-[A-Za-z0-9-]{10,}"
    :refs    ["https://api.slack.com/authentication/token-types"]}

   {:id      :openai-key
    :label   "OpenAI project API key"
    :sev     :high
    :kind    :secret
    :pattern "sk-proj-[A-Za-z0-9_-]{20,}"
    :refs    ["https://platform.openai.com/docs/api-reference/authentication"]}

   {:id      :anthropic-key
    :label   "Anthropic API key"
    :sev     :high
    :kind    :secret
    :pattern "sk-ant-[A-Za-z0-9_-]{20,}"
    :refs    ["https://docs.anthropic.com/en/api/admin-api/apikeys"]}

   {:id      :gcp-key
    :label   "Google Cloud API key"
    :sev     :high
    :kind    :secret
    :pattern "AIza[0-9A-Za-z_-]{35}"
    :refs    ["https://cloud.google.com/docs/authentication/api-keys"]}

   {:id      :azure-conn-str
    :label   "Azure storage connection string"
    :sev     :high
    :kind    :secret
    :pattern "DefaultEndpointsProtocol=https?;[^\\s'\"]+"
    :refs    ["https://learn.microsoft.com/en-us/azure/storage/common/storage-configure-connection-string"]}

   {:id      :azure-account-key
    :label   "Azure storage account key assignment"
    :sev     :high
    :kind    :secret
    :pattern "(?i)account[_-]?key\\s*[:=]\\s*['\"]?[A-Za-z0-9+/=]{44,}['\"]?"
    :refs    ["https://learn.microsoft.com/en-us/azure/storage/common/storage-configure-connection-string"]}

   {:id      :stripe-live
    :label   "Stripe live secret key"
    :sev     :high
    :kind    :secret
    :pattern "sk_live_[A-Za-z0-9]{16,}"
    :refs    ["https://docs.stripe.com/keys"]}

   {:id      :pat-token
    :label   "GitHub token (PAT / OAuth / App / user-to-server)"
    :sev     :high
    :kind    :secret
    :pattern "(?:ghp|gho|ghu|ghs|ghr)_[A-Za-z0-9]{36}|github_pat_[A-Za-z0-9_]{20,}"
    :refs    ["https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/managing-your-personal-access-tokens"]}

   {:id      :private-key
    :label   "PEM private key block"
    :sev     :high
    :kind    :secret
    :pattern "-----BEGIN (RSA |EC |DSA |OPENSSH )?PRIVATE KEY( BLOCK)?-----"
    :refs    []}

   {:id      :django-insecure-key
    :label   "Django 'django-insecure-' placeholder committed"
    :sev     :medium
    :kind    :secret
    :pattern "SECRET_KEY\\s*=\\s*['\"].*django-insecure-"
    :refs    []}

   {:id      :hardcoded-password
    :label   "Hardcoded password value (non-empty, non-test)"
    :sev     :medium
    :kind    :secret
    :pattern "(?i)(password|passwd|pwd)\\s*[:=]\\s*['\"][^.'\"{\\s]{6,}"
    :exclude "(?i)(test|fake|example)"
    :refs    []}

   {:id      :hardcoded-secret
    :label   "Generic API secret literal"
    :sev     :medium
    :kind    :secret
    :pattern "(?i)(api[_-]?key|secret|token)\\s*[:=]\\s*['\"][A-Za-z0-9_\\-]{16,}"
    :refs    []}])

;; ---------------------------------------------------------------------
;; Tool-independent exports
;; ---------------------------------------------------------------------

(defn secret-rules
  "Rules of kind :secret that carry a portable regex — the subset other
  secret scanners (e.g. gitleaks) can consume."
  []
  (filter #(and (= :secret (:kind %)) (some? (:pattern %))) rule-pack))

(defn ->gitleaks-toml
  "Render the secret subset as a gitleaks `[[rules]]` TOML file, so the
  same pack runs inside gitleaks. Code-backed rules (NRIC Mod-11) have no
  portable regex and are noted in the header comment instead."
  []
  (str "# Generated by pdpa-sg-clj (`bb export-rules --format gitleaks`).\n"
       "# Same rule pack as the built-in scanner; NRIC Mod-11 validation\n"
       "# is code-backed and intentionally NOT exported (no portable regex).\n"
       "title = \"pdpa-sg-clj secret rules\"\n\n"
       (->> (secret-rules)
            (map (fn [{:keys [id label pattern]}]
                   (str "[[rules]]\n"
                        "description = \"" label "\"\n"
                        "id = \"pdpa-" (name id) "\"\n"
                        "regex = '''" pattern "'''\n")))
            (str/join "\n"))))

(defn ->rules-json
  "Render the FULL pack (PII + secrets) as portable JSON for any consumer."
  []
  ((requiring-resolve 'cheshire.core/generate-string)
   (mapv #(select-keys % [:id :label :sev :kind :pattern :exclude :code :refs])
          rule-pack)
   {:pretty true}))

;; ---------------------------------------------------------------------
;; Babashka entry point
;; ---------------------------------------------------------------------

(defn run
  "Babashka entry point. Usage: `bb export-rules [--format gitleaks|json]
  [--out FILE]`. Prints to stdout unless --out is given."
  [args]
  (let [[fmt out]
        (loop [xs (seq args) fmt "gitleaks" out nil]
          (if (nil? xs)
            [fmt out]
            (let [a (first xs) r (next xs)]
              (cond
                (= a "--format")
                (recur (next r) (or (first r) fmt) out)

                (str/starts-with? a "--format=")
                (recur r (subs a (count "--format=")) out)

                (= a "--out")
                (recur (next r) fmt (first r))

                (str/starts-with? a "--out=")
                (recur r fmt (subs a (count "--out=")))

                :else (recur r fmt out)))))
        body (case fmt
               "json"     (->rules-json)
               "gitleaks" (->gitleaks-toml)
               (throw (ex-info (str "Unknown format: " fmt
                                    " (expected gitleaks|json)")
                               {:format fmt})))]
    (if out
      (do (spit out body) (println (str "[EXPORT] wrote " out " (" fmt ")")))
      (println body))))
