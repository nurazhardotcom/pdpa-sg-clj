(ns pdpa.policy-template
  "Portable policy-template rendering primitives.

   Resource lookup and file writes stay in the native `pdpa.policy`
   adapter. This namespace only transforms an in-memory template string."
  (:require [clojure.string :as str]))

(def placeholder-re
  "The simple `<<KEY>>` placeholder form used by the bundled templates."
  #"<<([A-Z][A-Z0-9_]*)>>")

(defn placeholder
  "Return the literal placeholder for a template key."
  [key]
  (str "<<" (name key) ">>"))

(defn fill-content
  "Replace every supplied `<<KEY>>` placeholder in `content`.

   The replacement order follows the values map, matching the historical
   native implementation. The input and output are strings; no resource or
   filesystem operation is performed here."
  [content values]
  (reduce-kv (fn [text key value]
               (str/replace text (placeholder key) (str value)))
             content
             values))

(def render fill-content)
(def fill fill-content)

(defn placeholders
  "Return the set of placeholder names found in `content`."
  [content]
  (->> (re-seq placeholder-re (or content ""))
       (map second)
       set))
