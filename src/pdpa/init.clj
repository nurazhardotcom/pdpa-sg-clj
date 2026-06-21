(ns pdpa.init
  "Copies CHECKLIST.md and the 6 policy templates into a target directory.
  Backs up any existing files with a `.bak` suffix.
  Only runs in Babashka (uses babashka.fs/copy). Usage: `bb init [path]`"
  (:require [babashka.fs       :as fs]
            [babashka.classpath :as cp]
            [clojure.java.io   :as io]
            [clojure.string    :as str]
            [pdpa.policy       :as policy]))

(defn- safe-copy
  "Copy file from `src` (a resource url or file) to `dst` file.
  If dst exists, back up to dst.bak first."
  [src dst]
  (when (.exists (io/file src))
    (fs/create-dirs (fs/parent dst))
    (cond
      (not (.exists (io/file dst)))
      (fs/copy src dst)

      (fs/directory? dst)
      (throw (ex-info (str dst " is a directory") {:path dst}))

      :else
      (do
        (fs/copy src (str (.getPath (io/file dst)) ".bak"))
        (fs/copy src dst))))
  (.getName (io/file dst)))

(defn run
  "Babashka entry point. CLI args: [target-dir]"
  [args]
  (cp/add-classpath "src:test:resources")
  (let [target (or (first args) ".")
        tgt    (io/file target)
        _      (fs/create-dirs tgt)]
    ;; Copy the master checklist, README and ARCHITECTURE
    (safe-copy
      (io/file (io/resource "CHECKLIST.md"))
      (io/file target "PDPA_CHECKLIST.md"))
    (safe-copy
      (io/file (io/resource "ARCHITECTURE.md"))
      (io/file target "PDPA_ARCHITECTURE.md"))
    (safe-copy
      (io/file (io/resource "README.md"))
      (io/file target "PDPA_README.md"))
    ;; Copy each policy template (rename .template.md → .md)
    (let [templates (policy/list-templates)
          policy-out (io/file target "policies")
          _          (fs/create-dirs policy-out)
          _copied    (for [t templates]
                       (safe-copy
                         (io/file (io/resource (str "policies/" t)))
                         (io/file policy-out (str/replace t ".template.md" ".md"))))]
      (println (format "[INIT] copied %d templates into %s/policies/"
                       (count _copied) target))
      (println "[INIT] copied CHECKLIST.md, README.md, ARCHITECTURE.md")
      (println "[INIT] Next steps:")
      (println "        1. fill in <<ORG_NAME>> placeholders in policies/*.md")
      (println "        2. tick boxes in PDPA_CHECKLIST.md as you complete them")
      (println "        3. run `bb audit .` to verify"))))
