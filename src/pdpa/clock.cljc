(ns pdpa.clock
  "Clock boundary for portable report callers.

  The report renderer accepts an injected timestamp. This adapter exists for
  the native audit command and for callers that explicitly want a current
  instant; it is not used to make report fixtures non-deterministic.")

#?(:clj
   (defn now-stamp
     "Current UTC instant as an ISO-8601 string on the JVM/Babashka."
     []
     (str (java.time.Instant/now)))

   :cljs
   (defn now-stamp
     "Current instant as an ISO-8601 string in a JavaScript host."
     []
     (.toISOString (js/Date.))))
