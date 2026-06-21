(ns pdpa.version
  "Single source of truth for toolkit + Singapore PDPA rule version stamp.")

(def toolkit-version "0.1.0")
(def pdpa-rule-stamp "2026-06-21")

(defn banner []
  (str "pdpa-sg-clj " toolkit-version
       " / Singapore PDPA " pdpa-rule-stamp))

(defn print-stamp []
  (println (banner)))
