(ns leiningen.makescript
  (:require [clojure.data.codec.base64 :as b64])
  (:require [clj-spawner.core :as spawner])
  [:require [clojure.string :refer [blank?]]]
  (:use [slingshot.slingshot :only [throw+ try+]]))

(defn exec-cmd
  [cmd]
  (println cmd)
  (let [command (spawner/exec cmd)
        out ((:read-output command))
        err ((:read-error command))]
    ; (println "out =" out)
    (if (blank? err) nil (println "An error occurred while processing subcommand: \n" err))
    out))

(defn create-uberjar []
  ;; Create the uberjar
  (exec-cmd "lein uberjar"))

(defn extract-path
  "Returns the full path of a standalone jar.
  
  Hacky.  Maybe there's a better way to hook into leiningen's innards."
  [text]
  (let [uberjar-path (second (re-find #"(?m).*Created .*?(/.*standalone.*?)$" text))]
    (if uberjar-path
      uberjar-path
      (do
        (println "*** Invalid path from text: " text)
        (throw+ {:type :cant-find-uberjar-name})))))

(defn makescript
  [project & args]
  (let 
    [uberjar-path (extract-path (create-uberjar))]
    (println "Uberjar created at: " uberjar-path)
    (exec-cmd (str "mv " uberjar-path " pwgen.jar"))
    (exec-cmd "cp src/pwgen/pwgen.template pwgen")
    (let 
      [uuencoded-str (exec-cmd "uuencode pwgen.jar pwgen.jar")]
      (spit "pwgen" uuencoded-str :append true))
    (exec-cmd "rm -f pwgen.jar")
    (exec-cmd "chmod a+x pwgen")
    (exec-cmd "mv pwgen target")
    (println "pwgen script created")))