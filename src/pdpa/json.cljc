(ns pdpa.json
  "Small JSON boundary for portable modules.

  The portable API deals in Clojure data. JSON is an edge concern: JVM and
  Babashka use Cheshire, while ClojureScript uses the host JSON object and
  converts Clojure data explicitly. Keeping the conversion here prevents
  portable namespaces from depending on a JVM JSON implementation."
  (:require #?(:clj [cheshire.core :as cheshire]
               :cljs [goog.object :as gobj])))

#?(:clj
   (defn parse-string
     "Parse JSON text, optionally keywordizing object keys."
     ([text] (parse-string text false))
     ([text keywordize?]
      (cheshire/parse-string text keywordize?)))

   :cljs
   (defn parse-string
     "Parse JSON text, optionally keywordizing object keys."
     ([text] (parse-string text false))
     ([text keywordize?]
      (cljs.core/js->clj (js/JSON.parse text)
                         :keywordize-keys (boolean keywordize?)))))

#?(:cljs
   (defn- value->js
     "Convert the ClojureScript values emitted by this project to JSON values."
     [value]
     (cond
       (keyword? value) (name value)
       (string? value) value
       (number? value) value
       (boolean? value) value
       (nil? value) nil
       (map? value)
       (let [object (js-obj)]
         (doseq [[k v] value]
           (gobj/set object
                     (if (keyword? k) (name k) (str k))
                     (value->js v)))
         object)
       (set? value) (into-array (map value->js value))
       (coll? value) (into-array (map value->js value))
       :else value)))

#?(:cljs
   (defn generate-string
     "Serialize Clojure data as JSON. `:pretty true` uses two-space indent."
     ([data] (generate-string data {}))
     ([data {:keys [pretty]}]
      (if pretty
        (js/JSON.stringify (value->js data) #js {:indent 2})
        (js/JSON.stringify (value->js data))))))

#?(:clj
   (defn generate-string
     "Serialize Clojure data as JSON."
     ([data] (generate-string data {}))
     ([data options] (cheshire/generate-string data options))))
