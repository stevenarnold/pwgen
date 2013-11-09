(defproject makescript "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[clj-spawner "0.0.2"]              ;; Tools to handle stdin and stdout for spawning
                 [org.clojure/data.codec "0.1.0"]   ;; Used in the script that makes a cli executable
                ]
  :eval-in-leiningen true)
