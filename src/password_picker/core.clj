(ns password-picker.core
  (:gen-class))
(use '[clojure.tools.cli :only [cli]])
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
(def all-noshift-chars (str alpha-lower numeric special-nocaps))
(def dict "resources/wordlist.txt")

(def words (with-open [rdr (clojure.java.io/reader dict)]
    (doall (line-seq rdr))))

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

(defrecord+defaults PasswordPreferences
  [at-least                15        ;; Password must be at least this long
   at-most                 25        ;; And no longer than this
   lower-alpha-weight      20        ;; Weighting for lowercase characters
   upper-alpha-weight       4        ;; Weighting for uppercase characters
   avoid-shift-pct         10        ;; Percentage chance we'll use a shifted char
                                     ;;     if one is picked
   use-at-least-upper       1        ;; Use at least this many uppercase chars.
                                     ;;     All use-at-least values must be < at-least
   numeric-weight           4        ;; Weighting for numeric characters
   use-at-least-numeric     1        ;; Use at least this many numeric chars
   special-weight           0        ;; Weighting for special chars
   use-at-least-special     0        ;; Use at least this many special characters
   allow-spaces             false    ;; Allow spaces to be in password
   make-memorable-pct       0]       ;; Percentage chance we'll use a dictionary 
                                     ;;     word when an alpha char would have been
                                     ;;     picked
  )

(defn- create-new-profiles
  "Create a new ~/.passwd-profiles file if it doesn't exist
  and populate it with some common preferences."
  [])

(defn- add-profile
  "Add a new profile to the ~/.passwd-profiles file."
  [profile])

(defn- read-profiles
  "Read in the existing profiles file.  Create one if it doesn't
  exist and use that."
  [])

(defn rand-between [at-least at-most]
  (+ at-least (int (rand (- at-most at-least)))))

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

(defn generate-candidate [at-least at-most memorable allow-spaces min-numbers
                          min-capitals
                & {:keys [charset] :or {charset all-chars}}]
  (let [password-length (rand-between at-least at-most)]
    (loop [curr-password ""]
      (cond 
        (>= (count curr-password) password-length)
          (subs curr-password 0 password-length)
        :else
          (recur (str curr-password (next-string memorable charset allow-spaces)))))))

(defn rule-min-charset
  [candidate min-chars charset]
  (if (= 0 min-chars)
    candidate
    (loop [curr-password candidate]
      (let [curr-password-chars (count (clojure.core/re-seq (clojure.core/re-pattern (str "[" charset "]")) curr-password))]
        (println "current password candidate: " curr-password)
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

(defn select-count
  [minimum maximum]
  (if (< maximum minimum) ;; includes the case of maximum = -1
    minimum
    (rand-between minimum maximum)))

(defn generate [& args]
  (let [min-numbers (nth args 4)
        max-numbers (nth args 5)
        num-numbers (select-count min-numbers max-numbers)
        min-capitals (nth args 6)
        max-capitals (nth args 7)
        num-capitals (select-count min-capitals max-capitals)
        candidate (apply generate-candidate args)]
    (println "num-numbers: " num-numbers "; num-capitals: " num-capitals)
    (loop [curr-password candidate
           tries 0]
      (let [new-candidate (-> curr-password
                            (rule-min-numbers num-numbers)
                            (rule-min-capitals num-capitals))]
        (if (= curr-password new-candidate)
          new-candidate
          (if (= (mod (inc tries) 10) 0)
            (recur (apply generate-candidate args) (inc tries))
            (recur new-candidate (inc tries))))))))

(defn -main
  [& args]
  ;; work around dangerous default behaviour in Clojure
  (alter-var-root #'*read-eval* (constantly false))
  ;; Parse command-line options and create a PasswordPreferences record
  ;; to pass to the generate function
  (let [{:keys [max min memorable allow-spaces min-numbers max-numbers min-capitals 
                max-capitals]}
        (nth (cli args
              ["-m" "--max" "The maximum number of characters" :parse-fn #(Integer. %)] 
              ["-n" "--min" "The minimum number of characters" :parse-fn #(Integer. %)]
              ["-nd" "--min-numbers" "The minimum number of numeric characters" :default 0 :parse-fn #(Integer. %)]
              ["-md" "--max-numbers" "The maximum number of numeric characters" :default -1 :parse-fn #(Integer. %)]
              ["-nc" "--min-capitals" "The minimum number of uppercase characters" :default 0 :parse-fn #(Integer. %)]
              ["-mc" "--max-capitals" "The maximum number of uppercase characters" :default -1 :parse-fn #(Integer. %)]
              ["-s" "--allow-spaces" "Allow spaces in the password" :default 0 :parse-fn #(Integer. %)]
              ["-h" "--memorable" "Use English words" :default 100 :parse-fn #(Integer. %)])
             0)]
    (set-clip! (generate min max memorable allow-spaces min-numbers max-numbers 
                         min-capitals max-capitals))))
