(ns pdpa.portable-fixtures
  "Fictional, shared parity inputs for JVM and Node ClojureScript tests.

   Every value that resembles PII or a secret is explicitly marked as a
   fictional fixture on its source line. These records are data, not live
   identifiers, and are intentionally kept outside native filesystem tests.")

(def nric-cases
  [{:label :citizen :input "S0100000D" :valid? true :check-digit "D"} ; pdpa:ignore — fictional parity fixture
   {:label :foreigner :input "F0000002R" :valid? true :check-digit "R"} ; pdpa:ignore — fictional parity fixture
   {:label :fin :input "M5012345J" :valid? true :check-digit "J"} ; pdpa:ignore — fictional parity fixture
   {:label :bad-checksum :input "S0000000Z" :valid? false :check-digit "J"} ; pdpa:ignore — fictional parity fixture
   {:label :hex-lookalike :input "deadbeefdeadbeefF" :valid? false :check-digit nil}]) ; pdpa:ignore — fictional parity fixture

(def redaction-case
  {:input "User S0100000D called +65 9123 4567 or alice@acme.test." ; pdpa:ignore — fictional parity fixture
   :redacted "User [REDACTED_NRIC] called +65 9123 4567 or [REDACTED_EMAIL]."
   :counts {:nric 1 :phone 0 :email 1}
   :known-phone-regex-defect true}) ; pdpa:ignore — fictional parity fixture

(def legacy-phone-regex-case
  {:input "91234567;;mobile" ; pdpa:ignore — fictional parity fixture
   :redacted "[REDACTED_PHONE]"
   :counts {:nric 0 :phone 1 :email 0}}) ; pdpa:ignore — fictional parity fixture

(def detection-lines
  [{:path "fixtures/nric.txt" :text "identity S0100000D" :line 4 :expected :nric-live} ; pdpa:ignore — fictional parity fixture
   {:path "fixtures/secret.txt" :text "AWS_SECRET_ACCESS_KEY = \"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY\"" :line 9 :expected :aws-secret} ; pdpa:ignore — fictional parity fixture
   {:path "fixtures/ignored.txt" :text "identity S0100000D # pdpa:ignore" :line 2 :expected nil} ; pdpa:ignore — fictional parity fixture
   {:path "fixtures/excluded.txt" :text "identity S0100000D" :line 3 :expected :nric-live}]) ; pdpa:ignore — fictional parity fixture

(def detection-expected
  {:findings [{:severity :critical :id :nric-live :path "fixtures/nric.txt" :line 4}
              {:severity :high :id :aws-secret :path "fixtures/secret.txt" :line 9}
              {:severity :critical :id :nric-live :path "fixtures/excluded.txt" :line 3}]
   :counts {:critical 2 :high 1 :medium 0 :low 0 :info 0}
   :clean? false})

(def policy-case
  {:template "Org: <<ORG_NAME>>\nDPO: <<DPO_EMAIL>>\nRetention: <<RETENTION_DAYS>> days\n"
   :values {:ORG_NAME "Fictional Pte Ltd"
            :DPO_EMAIL "dpo@fictional.test" ; pdpa:ignore — fictional parity fixture
            :RETENTION_DAYS "30"}
   :rendered "Org: Fictional Pte Ltd\nDPO: dpo@fictional.test\nRetention: 30 days\n"}) ; pdpa:ignore — fictional parity fixture

(def report-context
  {:path "fictional-project"
   :scan-result {:findings [{:id :aws-key :label "AWS access key id"
                             :severity :high :path "deploy.txt" :line 9}]
                 :counts {:critical 0 :high 1 :medium 0 :low 0 :info 0}
                 :clean? false}
   :evidence ["PRIVACY_POLICY"]
   :compliant? false
   :timestamp "2026-09-25T00:00:00Z"})

(def marker-semantics
  "The marker only qualifies a following checkbox; it is not retroactive."
  {:marker-before "## 1. Consent\n<!-- agent:verify-consent -->\n- [ ] candidate\n"
   :marker-after "## 1. Consent\n- [ ] earlier\n<!-- agent:verify-consent -->\n"})
