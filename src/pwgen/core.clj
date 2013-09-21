(ns pwgen.core
  (:gen-class)
  [:require [clojure.string :refer [split blank?]]]
  [:require [clojure.tools.cli :refer [cli]]])
(require '[clojure.data.json :as json])
(import '(java.io File))
(load "record-defaults")
(load "clip-utils")


(def alpha-lower "abcdefghijklmnopqrstuvwxyz")
(def right-alpha-lower "hynmjuiklop")
(def left-alpha-lower "qazwsxedcrfvtgb")
(def alpha-upper "ABCDEFGHIJKLMNOPQRSTUVWXYZ")
(def alpha (str alpha-lower alpha-upper))
(def numeric "0123456789")
(def left-numeric "09876")
(def right-numeric "12345")
(def alphanumeric (str alpha numeric))
(def special "~`!@#$%^&*()-_=+]}[{;:,<.>/?'|")
(def special-nocaps "`-=;',./[]")
(def right-special-nocaps ",./;'[]-=")
(def all-chars (str alpha-lower alpha-upper numeric special))
(def all-chars-with-space (str all-chars " "))
(def all-noshift-chars (str alpha-lower numeric special-nocaps))
(def character-classes ['alpha-lower 'right-alpha-lower 'left-alpha-lower 'alpha-upper
                        'alpha 'numeric 'left-numeric 'right-numeric 'alphanumeric
                        'special 'special-nocaps 'right-special-nocaps 'all-chars
                        'all-noshift-chars])
(def dict "wordlist.txt")
(def words (split (slurp (clojure.java.io/resource dict)) #"[\r\n]+"))
; (def all-profiles (pwgen.core/read-profiles))

(defn string-splice
  ([target new offset] (string-splice target new offset (count new)))
  ([target new offset length]
     (str (subs target 0 offset) new (subs target (+ offset length)))))

;; Basic subcommands:
;;   - generate [profile] [options] [pairs]
;;     This will generate a password using the given profile, overriding
;;     any options in that profile with the ones provided.  If the options
;;     include an assertion to save the profile, any provided pairs will
;;     be saved with it.  The generated password will be pushed to the 
;;     clipboard.  If the profile was encrypted, the user will be prompted
;;     for the master password.

(defrecord+defaults PasswordProfile
  [min                     15        ;; Password must be at least this long
   max                     25        ;; And no longer than this
   min-capitals             1        ;; Use at least this many uppercase chars
   max-capitals             4        ;; Use no more than this many uppercase chars
   min-numeric              1        ;; Use at least this many numeric chars
   max-numeric              4        ;; Use no more than this many numeric chars
   min-special              1        ;; Use at least this many special characters
   max-special              4        ;; Use no more than this many special characters
   allow-spaces             true     ;; Allow spaces to be in password
   special-charset          #"[-_.]" ;; Use these special characters by default
   make-memorable-pct       100]     ;; Percentage chance we'll use a dictionary 
                                     ;;     word when an alpha char would have been
                                     ;;     picked
  )

(defn- get-profile-path
  [file]
  (let [homedir (System/getProperty (str "user.home"))]
    (if (.startsWith file "~")
      (str homedir (subs file 1))
      file)))

(defn- save-profiles
  "Create a new ~/.passwd-profiles file if it doesn't exist
  and populate it with our profiles."
  [profiles])

(defn- -read-profiles
  [file]
  (let [profile-path (get-profile-path file)
        f (File. profile-path)]
    (if (.isFile f)
      (json/read-str (slurp profile-path))
      (doall
        (spit profile-path "{}")
        (json/read-str "{}")))))

(defn- read-profiles
  "Read in the existing profiles file if it exists and convert to a Clojure
  data structure from JSON"
([]
 (-read-profiles "~/.pwgenrc"))
([file]
 (-read-profiles file)) 
)

(defn- add-profile
  "Add a new profile to the ~/.passwd-profiles file."
  [profile & args]
  (let [[min max memorable allow-spaces min-numbers max-numbers
         min-capitals max-capitals min-special max-special
         special-charset create-profile] args
        profile-record (->PasswordProfile min max min-capitals max-capitals
                                          min-numbers max-numbers min-special
                                          max-special allow-spaces special-charset
                                          memorable)
        all-profiles (pwgen.core/read-profiles)]
    (spit (get-profile-path "~/.pwgenrc")
          (json/write-str (assoc all-profiles profile profile-record)))))

(defn rand-between [at-least at-most]
  (+ at-least (int (rand (- (inc at-most) at-least)))))

(defn randomly-pick
  [pct]
  (<= (rand 100) pct))

(defn next-string
  [memorable-pct charset allow-spaces]
  (let [next-char (rand-nth charset)
        length-to-use (+ 3 (rand 12))
        word-to-use (rand-nth words)
        word-length (count word-to-use)
        start-point (rand (/ (count word-to-use) 2))
        candidate (str (subs word-to-use start-point (if (> length-to-use start-point)
                                                       (min length-to-use word-length)
                                                       word-length))
                       (if (randomly-pick allow-spaces) " " ""))]
    (if (and (.contains alpha (str next-char))
             (randomly-pick memorable-pct))
      (if (randomly-pick allow-spaces)
        candidate
        (clojure.string/replace candidate #" " ""))
      next-char)))

(defn generate-candidate [at-least at-most memorable allow-spaces
                & {:keys [charset] :or {charset all-chars}}]
  (let [password-length (rand-between at-least at-most)]
    (loop [curr-password ""]
      (cond 
        (>= (count curr-password) password-length)
          (subs curr-password 0 password-length)
        :else
          (recur (str curr-password (next-string memorable charset allow-spaces)))))))

(def regex-char-esc-smap
  (let [esc-chars "()*&^%$#![]{}.+-"]
    (zipmap esc-chars
            (map #(str "\\" %) esc-chars))))

(defn normalize-charset
  [charset]
  (->> charset
       (replace regex-char-esc-smap)
       (reduce str)
       (#(str "[" %1 "]"))
       re-pattern))

(defn rule-min-charset
  [candidate min-chars charset]
  (if (= 0 min-chars)
    candidate
    (loop [curr-password candidate]
      (let [normalized-charset (normalize-charset charset)
            curr-password-chars (count 
                                  (clojure.core/re-seq normalized-charset curr-password))]
        (println "current password candidate: " curr-password)
        (println "charset to match =" normalized-charset)
        (println "OK chars in candidate: " curr-password-chars)
        (if (>= curr-password-chars min-chars)
          curr-password
          (let [rand-num (str (rand-nth charset))
                pw-size (count curr-password)
                rand-pos (rand pw-size)]
            (recur (str (string-splice curr-password rand-num rand-pos)))))))))

(defn rule-min-numbers 
  [candidate min-numbers]
  (rule-min-charset candidate min-numbers numeric))

(defn rule-min-capitals
  [candidate min-capitals]
  (rule-min-charset candidate min-capitals alpha-upper))

(defn rule-min-specials
  [candidate min-specials special-charset]
  (rule-min-charset candidate min-specials special-charset))

(defn regex-charset
  [candidate charset init-regex end-regex splice-pos]
  (let [normalized-charset (re-pattern (str init-regex 
                                            (normalize-charset charset) 
                                            end-regex))
        replacement (str (rand-nth charset))]
    (println "Potential replacement =" replacement)
    (println "Normalized charset =" normalized-charset)
    (if (re-matches normalized-charset candidate)
      candidate
      (string-splice candidate replacement splice-pos))))

(defn rule-init-charset
  [candidate charset]
  (regex-charset candidate charset "^" ".*$" 0))

(defn rule-ending-charset
  [candidate charset]
  (regex-charset candidate charset "^.*" "$" (dec (count candidate))))

(defn select-count
  [minimum maximum]
  (if (< maximum minimum) ;; includes the case of maximum = -1
    minimum
    (rand-between minimum maximum)))

(defn resolve-charset
  [charset]
  (if (some #{(symbol charset)} character-classes)
    (deref (resolve (symbol (str "pwgen.core/" charset))))
    charset))

(defn generate [& args]
  (let [[min max memorable allow-spaces min-numbers max-numbers
         min-capitals max-capitals min-special max-special
         special-charset initial-charset ending-charset 
         create-profile] args  ;; Basic parameters
        resolved-special-charset (resolve-charset special-charset)
        num-numbers (select-count min-numbers max-numbers)
        num-capitals (select-count min-capitals max-capitals)
        num-specials (select-count min-special max-special)
        charset (str alphanumeric special-charset)
        candidate (generate-candidate min max memorable allow-spaces :charset charset)]
    (println "num-numbers: " num-numbers "; num-capitals: " num-capitals "; num-specials: " num-specials)
    (println "charset = %[" charset "]")
    (println "resolved-special-charset = %[" resolved-special-charset "]")
    (if (not (blank? create-profile))
      (apply (partial add-profile create-profile) args))
    (loop [curr-password candidate
           tries 0]
      (let [new-candidate (-> curr-password
                              (rule-min-numbers num-numbers)
                              (rule-min-capitals num-capitals)
                              (rule-min-specials num-specials resolved-special-charset)
                              (rule-init-charset initial-charset)
                              (rule-ending-charset ending-charset))]
        (if (= curr-password new-candidate)
          new-candidate
          (if (= (mod (inc tries) 20) 0)
            (recur (generate-candidate min max memorable allow-spaces :charset charset) 
                   (inc tries))
            (recur new-candidate (inc tries))))))))

(defn -main
  [& args]
  ;; work around dangerous default behaviour in Clojure
  (alter-var-root #'*read-eval* (constantly false))
  ;; Parse command-line options and create a PasswordPreferences record
  ;; to pass to the generate function
  (let [args (cli args
              ["-m" "--max" "The maximum number of characters" :parse-fn #(Integer. %)] 
              ["-n" "--min" "The minimum number of characters" :parse-fn #(Integer. %)]
              ["-nd" "--min-numbers" "The minimum number of numeric characters" :default 0 
                :parse-fn #(Integer. %)]
              ["-md" "--max-numbers" "The maximum number of numeric characters" :default -1 
                :parse-fn #(Integer. %)]
              ["-nc" "--min-capitals" "The minimum number of uppercase characters" :default 0 
                :parse-fn #(Integer. %)]
              ["-mc" "--max-capitals" "The maximum number of uppercase characters" :default -1 
                :parse-fn #(Integer. %)]
              ["-ns" "--min-special" "The minimum number of special (punctuation) characters" 
                :default 0 :parse-fn #(Integer. %)]
              ["-ms" "--max-special" "The maximum number of special (punctuation) characters" 
                :default -1 :parse-fn #(Integer. %)]
              ["-s" "--allow-spaces" "Allow spaces in the password" :default 0 
                :parse-fn #(Integer. %)]
              ["-sc" "--special-charset" "Use the given special characters instead of the normal set" 
                :default special :parse-fn #(resolve-charset (String. %))]
              ["-ic" "--initial-charset" "Use the given initial characters instead of the normal set" 
                :default alpha :parse-fn #(resolve-charset (String. %))]
              ["-ec" "--ending-charset" "Use the given ending characters instead of the normal set" 
                :default alphanumeric :parse-fn #(resolve-charset (String. %))]
              ["-cp" "--create-profile" "Save the profile of this invocation with a given tag" 
                :default "" :parse-fn #(String. %)]
              ["-h" "--memorable" "Use English words" :default 100 :parse-fn #(Integer. %)])
        {:keys [max min min-numbers max-numbers min-capitals max-capitals min-special
                max-special allow-spaces special-charset initial-charset
                ending-charset create-profile memorable]}
        (nth args 0)
        subcommand (nth (nth args 1) 0)]
    (println "subcommand: " subcommand)
    (case subcommand
      "generate"
      (set-clip! (generate min max memorable allow-spaces min-numbers max-numbers 
                           min-capitals max-capitals min-special max-special 
                           special-charset initial-charset ending-charset create-profile))
      (println (str "Invalid subcommand: '" subcommand "'")))))
