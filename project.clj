(defproject pwgen "0.1.0-SNAPSHOT-002"
  :description "Software to generate passwords according to certain policies"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/tools.cli "0.2.2"]      ;; Read in command line arguments
                 [org.clojure/data.json "0.2.3"]
                 [slingshot "0.10.3"]
                 [me.raynes/fs "1.4.5"]               ;; File system tools; temp dirs
                 [org.clojure/core.contracts "0.0.5"] ;; Contract-oriented programming
                 [cdt "1.2.6.2"]
                 ]
  :plugins [[lein-midje "3.1.1"]
            [makescript "0.1.0-SNAPSHOT"]]
  :profiles {:dev {:dependencies [[midje "1.6-beta1"]]}}
  :main pwgen.core)
