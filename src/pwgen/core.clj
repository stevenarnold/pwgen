(ns pwgen.core
  (:gen-class)
  [:require [clojure.core :refer [re-pattern re-seq]]]
  [:require [clojure.string :refer [split blank? upper-case]]]
  [:require [clojure.tools.cli :refer [cli]]]
  [:require [clojure.walk :refer [keywordize-keys]]]
  (:use [slingshot.slingshot :only [throw+ try+]]))
(use 'clojure.core.contracts)
(require '[clojure.data.json :as json])
(import '(java.io File))
(load "helper-macros")
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
(def right-nocaps (str right-alpha-lower right-numeric right-special-nocaps))
(def all-chars (str alphanumeric special))
(def all-chars-with-space (str all-chars " "))
(def all-noshift-chars (str alpha-lower numeric special-nocaps))
(def character-classes ['alpha-lower 'right-alpha-lower 'left-alpha-lower 'alpha-upper
                        'alpha 'numeric 'left-numeric 'right-numeric 'alphanumeric
                        'special 'special-nocaps 'right-special-nocaps 'all-chars
                        'all-noshift-chars])
(def dict "wordlist.txt")
(def words (split (slurp (clojure.java.io/resource dict)) #"[\r\n]+"))
(def default-profile "{\"standard\":{\"min\":20,\"max\":30,\"min-capitals\":1,\"max-capitals\":4,
                     \"min-numbers\":1,\"max-numbers\":4,\"min-special\":1,\"max-special\":4,
                     \"allow-spaces\":0,\"special-charset\":\"special\",\"make-memorable-pct\":100}}")

(defn string-splice
  ([target new offset] (string-splice target new offset (count new)))
  ([target new offset length]
     (str (subs target 0 offset) new (subs target (+ offset length)))))

(defn re-pos [re s]
  (loop [m (re-matcher re s)
         res {}]
    (if (.find m)
      (recur m (assoc res (.start m) (.group m)))
      res)))

;; Basic subcommands:
;;   - generate [profile] [options] [pairs]
;;     This will generate a password using the given profile, overriding
;;     any options in that profile with the ones provided.  If the options
;;     include an assertion to save the profile, any provided pairs will
;;     be saved with it.  The generated password will be pushed to the 
;;     clipboard.  If the profile was encrypted, the user will be prompted
;;     for the master password.

(defrecord PasswordProfile
  [min                ;; Password must be at least this long
   max                ;; And no longer than this
   min-capitals       ;; Use at least this many uppercase chars
   max-capitals       ;; Use no more than this many uppercase chars
   min-numbers        ;; Use at least this many numeric chars
   max-numbers        ;; Use no more than this many numeric chars
   min-special        ;; Use at least this many special characters
   max-special        ;; Use no more than this many special characters
   allow-spaces       ;; Allow spaces to be in password
   special-charset    ;; Use these special characters by default
   memorable]         ;; Percentage chance we'll use a dictionary 
                      ;;     word when an alpha char would have been
                      ;;     picked
  )
(defn get-in-profile
  [map-obj ks]
  (if (= (count ks) 0)
    map-obj
    (let [head (if (contains? map-obj (first ks)) (first ks) (keyword (first ks)))]
      (recur (get map-obj head) (rest ks)))))

(defn-memo get-profile-path
  ([]
   (do
     ; (println "no path = " (get-profile-path "~/.pwgenrc"))
     (get-profile-path "~/.pwgenrc")))
  ([file]
  (let [homedir (System/getProperty (str "user.home"))]
    (if (.startsWith file "~")
      (do
        ; (println (str "with file and ~ =" homedir (subs file 1)))
        (str homedir (subs file 1)))
      (do
        ; (println "with file and no ~ =" file)
        file)))))

;; If all options in the list exist in the hash, print the string, and call
;; print-rules with the same options with those keys removed.  If all the options
;; don't exist, then return nil.
(declare print-rules)
(def mprint_rules 
  (with-constraints
    (fn [options optionset docstring]
      (if (= (count optionset) 0)
        nil
        (let [reduced-options (apply dissoc options optionset)]
          (if (= (- (count options) (count optionset))
                 (count reduced-options))
            (do
              (println docstring)
              reduced-options)
            {}))))
    (contract mprinting-rules
              "validates input/output types and properties for result"
                [options optionset docstring] [(map? options)
                                               (vector? optionset)
                                               (every? keyword? optionset)
                                               (string? docstring)
                                               => 
                                               (map? %)
                                               (not-any? (partial contains? %) optionset)])))

;; Good candidate for a macro.  Taken in a hash and any number of keys, and 
;; strings for each possible combination of the keys having a Boolean true/
;; false value.  Maybe some kind of pattern-match idiom.  Then call self
;; recursively with those keys removed.
(def print-rules 
  (with-constraints
    (fn [options]
      (-> options
          (mprint_rules [:min :max] 
                        (str "- Password must be at least " (:min options) " and no more than " (:max options) " characters long"))
          (mprint_rules [:min] 
                        (str "- Password must be at least " (:min options) " characters long"))
          (mprint_rules [:max] 
                        (str "- Password may not be more than " (:max options) " characters long"))))
    (contract printing-rules
              "validates types of param and return"
              [options] [map? => map?])))

(defn- save-profiles
  "Create a new ~/.pwgenrc file if it doesn't exist
  and populate it with our profiles."
  [profiles])

(defn- merge-profile
  [profile params]
  (let [kprofile (into {} 
                       (for [[k v] profile] 
                         [(keyword k) v]))]
    (reduce (fn [memo pair]
              (let [[k v] pair]
                (if (and (= v -1) (not (contains? kprofile k)))
                  (case k
                    "min"
                    (assoc memo k 15)
                    "max"
                    (assoc memo k 25)
                    (assoc memo k 0))
                  (if (not (or (= v "") (= v -1)))
                    (assoc memo k v)
                    memo))))
            kprofile params)))

(defn- -read-profiles
  [file & {:keys [json-str] :or {json-str "{}"}}]
  (let [profile-path (get-profile-path file)
        f (File. profile-path)]
    (if (.isFile f)
      (json/read-str (slurp profile-path))
      (do
        ; (println "file" profile-path "does not exist")
        ; (println "json-str =" json-str)
        (spit profile-path json-str)
        (json/read-str json-str)))))

(defn- read-profiles
  "Read in the existing profiles file if it exists and convert to a Clojure
  data structure from JSON"
([]
 (-read-profiles "~/.pwgenrc" :json-str default-profile))
([file & {:keys [json-str] :or {:json-str default-profile}}]
 (-read-profiles file :json-str default-profile))
)

(def add-profile-map
  "Generate a map representing the new ~/.pwgenrc file."
  (with-constraints
    (fn [profile args]
      (let [profile-rec (keywordize-keys (args profile))
            profile-record (map->PasswordProfile profile-rec)
            all-profiles (pwgen.core/read-profiles)]
        ; (println "profile = " profile)
        ; (println "profile-record = " profile-record)
        ; (println "profile-rec = " profile-rec)
        ; (println "min = " min ", max = " max)
        ; (println args)
        ; (println "result = " (assoc all-profiles profile profile-record))
        (assoc all-profiles profile profile-record)))
    (contract add-profile-contract
              "Ensure that the input and output params are well formed"
              [profile args] [
                              (map? args)
                              (every? map? (vals args))
                              (every? #(or (string? %) (number? %)) (first (map vals (vals args))))
                              (every? #(or (string? %) (keyword? %)) (keys args))
                              (string? profile)
                              =>
                              (map? %)
                              (every? (fn [x] (or (string? x) (number? x))) (first (map vals (vals %))))
                              ])))

(defn add-profile
  "Generate a string representing the new ~/.pwgenrc file."
  [profile args]
  (json/write-str (add-profile-map profile args)))

(defn write-profiles!
  "Write the given profile to the proper profile path."
  [profile args]
  (spit (get-profile-path) (add-profile profile args)))

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
  (let [esc-chars "()*&^%$#![]{}.+-|'?/<>,"]
    (zipmap esc-chars
            (map #(str "\\" %) esc-chars))))

(def normalize-charset
  "Escape a string that is intended as a regular expression."
  (with-constraints
    (fn [charset & {:keys [as-string] :or {as-string false}}]
      (let [normalized-charset (if (> (count charset) 0)
                                 (->> charset
                                      (replace regex-char-esc-smap)
                                      (reduce str)
                                      (#(str "[" %1 "]")))
                                 "")]
        (if as-string normalized-charset (re-pattern normalized-charset))))
    (contract normalize-charset-contract
              "Ensure that strings are properly escaped for a regular expression"
              [charset & {:keys [as-string] :or {as-string false}}]
              [(string? charset)
               =>
               (or (string? %) (= (re-pattern %) %))])))

(defn charset-diff
  ;; Replace the regex matches described by 'subset' with nothing in
  ;; the 'superset' string.
  [superset subset]
  (.replaceAll superset (str subset) ""))

(defn rule-charset-count
  [candidate num-chars charset & {:keys [rule-fn] :or {rule-fn false}}]
  ; (println "num-chars =" num-chars)
  ; (println "charset =" charset)
  ; (println "candidate =" candidate)
  (if (= 0 (count charset))
    candidate
    (loop [curr-password candidate]
      (let [normalized-charset (normalize-charset charset)
            curr-password-chars (count 
                                  (clojure.core/re-seq normalized-charset curr-password))
            default-charset-adder (fn [charset curr-password] 
                                    (let [rand-chr (str (rand-nth charset))
                                          pw-size (count curr-password)
                                          rand-pos (rand pw-size)]
                                      (str (string-splice curr-password rand-chr rand-pos))))]
        ; (println "current password candidate: " curr-password)
        ; (println "charset to match =" normalized-charset)
        ; (println "OK chars in candidate: " curr-password-chars)
        ; (println "need exactly" num-chars "in candidate")
        (cond 
          (= curr-password-chars num-chars)
          curr-password
          (> curr-password-chars num-chars)
          (let [index-to-change (rand-nth (keys (re-pos normalized-charset curr-password)))
                chars-to-use (charset-diff all-chars normalized-charset)
                rand-chr (str (rand-nth chars-to-use))]
            (recur (str (string-splice curr-password rand-chr index-to-change))))
          :else
          (if rule-fn
            (recur (rule-fn charset curr-password))
            (recur (default-charset-adder charset curr-password))))))))

(defn rule-numbers 
  [candidate count-numbers]
  ; (println "number charset validation")
  (rule-charset-count candidate count-numbers numeric))

(def rule-capitals
  (with-constraints
    (fn [candidate count-capitals]
      ; (println "capital charset validation")
      (rule-charset-count candidate count-capitals alpha-upper 
                          :rule-fn (fn [charset curr-password] 
                                     (let [rand-chr (str (rand-nth charset))
                                           rand-pos (rand (count curr-password))
                                           selected-char (nth curr-password rand-pos)]
                                       (println "selected-char is" selected-char)
                                       (if (clojure.core/re-seq (re-pattern (normalize-charset (str selected-char))) alpha)
                                         (str (string-splice curr-password (upper-case selected-char) rand-pos))
                                         (str (string-splice curr-password rand-chr rand-pos)))))))
    (contract place-capitals
              "validate sanity of input and correctness of result"
              [candidate count-capitals] [(string? candidate)
                                          (number? count-capitals)
                                          => 
                                          string?
                                          (= count-capitals (count (clojure.core/re-seq (normalize-charset alpha-upper) %)))])))

(defn rule-specials
  [candidate count-specials special-charset]
  ; (println "count of special chars =" count-specials)
  (let [specials-candidate (rule-charset-count candidate count-specials special-charset)
        chars-to-remove (charset-diff special (normalize-charset special-charset))]
    ; (println "*** VALIDATING")
    ; (println "specials-candidate =" specials-candidate)
    ; (println "special-charset =" special-charset)
    ; (println "chars-to-remove =" chars-to-remove)
    (rule-charset-count specials-candidate 0 chars-to-remove)))

(defn regex-charset
  [candidate charset init-regex end-regex splice-pos]
  (let [normalized-charset (re-pattern (str init-regex 
                                            (normalize-charset charset) 
                                            end-regex))
        replacement (str (rand-nth charset))]
    ; (println "Potential replacement =" replacement)
    ; (println "Normalized charset =" normalized-charset)
    (if (re-matches normalized-charset candidate)
      candidate
      (string-splice candidate replacement splice-pos))))

(defn rule-init-charset
  [candidate charset]
  ; (println "init charset validation")
  (regex-charset candidate charset "^" ".*$" 0))

(defn rule-ending-charset
  [candidate charset]
  (regex-charset candidate charset "^.*" "$" (dec (count candidate))))

(defn select-count
  [minimum maximum]
  ; (println "minimum =" minimum ", maximum =" maximum)
  (if (< maximum minimum) ;; includes the case of maximum = -1
    minimum
    (rand-between minimum maximum)))

(defn resolve-charset
  [charset]
  (if (some #{(symbol charset)} character-classes)
    (deref (resolve (symbol (str "pwgen.core/" charset))))
    charset))

;; Perform all the logic associated with calculating whether the requested
;; rules are compatible and "sane".  Generate some initial values and 
;; modify them, if needed, within the scope of the rules, so that a password
;; can be generated.
(defn generate-charset-counts
  [min max min-numbers max-numbers min-capitals max-capitals min-special
   max-special initial-charset ending-charset]
  ; (println "min-special =" min-special ", max-special =" max-special)
  (let [num-numbers (select-count min-numbers max-numbers)
        num-capitals (select-count min-capitals max-capitals)
        num-specials (select-count min-special max-special)]
    ; (println "num-specials =", num-specials)
    (if (> (+ 2 num-numbers num-capitals num-specials) max)
      ;; In the future, we can try harder to match passwords.  For example,
      ;; we can look at the min-* values and use those if the above check
      ;; failed, and we can consider the init and ending charsets.  For example,
      ;; and ending-charset that allowed numeric values provides an optional
      ;; slot for a numeric, if needed.  For now, if we fail the above test, 
      ;; we raise an exception and print a notice to the user.
      (throw+ {:type :invalid-rules})
      (doall
        ; (println (str num-numbers ", " num-capitals ", " num-specials ", " max))
        [num-numbers num-capitals num-specials]))))

(defn generate [{:keys [min max memorable allow-spaces min-numbers max-numbers
         min-capitals max-capitals min-special max-special
         charset special-charset initial-charset ending-charset 
         create-profile force] :as args}]
  ; (println "min =" min ", max =" max)
  (let [resolved-special-charset (resolve-charset special-charset)
        [num-numbers num-capitals num-specials] (generate-charset-counts
                                                  min max min-numbers max-numbers
                                                  min-capitals max-capitals min-special
                                                  max-special initial-charset
                                                  ending-charset)
        candidate (generate-candidate min max memorable allow-spaces :charset charset)]
    ; (println "num-numbers: " num-numbers "; num-capitals: " num-capitals "; num-specials: " num-specials)
    ; (println "charset = %[" charset "]")
    ; (println "resolved-special-charset = %[" resolved-special-charset "]")
    (println "args =" args)
    (println "map? args =" (map? args))
    (println "create-profile =" create-profile)
    (if (not (blank? create-profile))
      (write-profiles! create-profile force args))
    (loop [curr-password candidate
           tries 0]
      (let [new-candidate (-> curr-password
                              (rule-numbers num-numbers)
                              (rule-capitals num-capitals)
                              (rule-specials num-specials resolved-special-charset)
                              (rule-init-charset initial-charset)
                              (rule-ending-charset ending-charset))]
        ; (println "new candidate =" new-candidate ", curr-password =" curr-password)
        (if (= curr-password new-candidate)
          new-candidate
          (if (= (mod (inc tries) 100) 0)
            (recur (generate-candidate min max memorable allow-spaces :charset charset) 
                   (inc tries))
            (recur new-candidate (inc tries))))))))

(defn -main
  [& args]
  ; (println "args =" args)
  ;; work around dangerous default behaviour in Clojure
  (alter-var-root #'*read-eval* (constantly false))
  ;; Parse command-line options and create a PasswordPreferences record
  ;; to pass to the generate function
  (let [args (cli args
              ["-m" "--max" "The maximum number of characters" :default -1 :parse-fn #(Integer. %)] 
              ["-n" "--min" "The minimum number of characters" :default -1 :parse-fn #(Integer. %)]
              ["-nd" "--min-numbers" "The minimum number of numeric characters" :default -1 
                :parse-fn #(Integer. %)]
              ["-md" "--max-numbers" "The maximum number of numeric characters" :default -1 
                :parse-fn #(Integer. %)]
              ["-nc" "--min-capitals" "The minimum number of uppercase characters" :default -1 
                :parse-fn #(Integer. %)]
              ["-mc" "--max-capitals" "The maximum number of uppercase characters" :default -1 
                :parse-fn #(Integer. %)]
              ["-ns" "--min-special" "The minimum number of special (punctuation) characters" 
                :default -1 :parse-fn #(Integer. %)]
              ["-ms" "--max-special" "The maximum number of special (punctuation) characters" 
                :default -1 :parse-fn #(Integer. %)]
              ["-s" "--allow-spaces" "Allow spaces in the password" :default -1 
                :parse-fn #(Integer. %)]
              ["-c" "--charset" "Define the base character set to use for generating passwords"
                :default all-chars :parse-fn #(->> (-> % 
                                                       (String.)
                                                       (split #" +"))
                                                   (map resolve-charset)
                                                   (apply str))]
              ["-sc" "--special-charset" "Use the given special characters instead of the normal set" 
                :default special :parse-fn #(resolve-charset (String. %))]
              ["-ic" "--initial-charset" "Use the given initial characters instead of the normal set" 
                :default alpha :parse-fn #(resolve-charset (String. %))]
              ["-ec" "--ending-charset" "Use the given ending characters instead of the normal set" 
                :default alphanumeric :parse-fn #(resolve-charset (String. %))]
              ["-cp" "--create-profile" "Save the profile of this invocation with a given tag" 
                :default "" :parse-fn #(String. %)]
              ["-f" "--force" "Force --create-profile to overwrite an already-existing profile"
                :flag false]
              ["-up" "--use-profile" "Use the given profile in this invocation, overriding with other flags passed"
                :default "standard" :parse-fn #(String. %)]
              ["-h" "--memorable" "Use English words" :default 100 :parse-fn #(Integer. %)])
        {:keys [use-profile]} (nth args 0)
        subcommand (try 
                     (nth (nth args 1) 0)
                     (catch java.lang.IndexOutOfBoundsException e
                       "generate"))]
    ; (println "subcommand: " subcommand)
    (case subcommand
      "generate"
        (do
        ; (try+
            (if (blank? use-profile)
              (set-clip! (generate (nth args 0)))
              (let [profile (get (read-profiles) use-profile)
                    merged-options (merge-profile profile (nth args 0))
                    result (generate merged-options)]
                (if true (print-rules merged-options) false)
                (set-clip! result)
                (println result))))
          ; (catch Object o
            ; (println "The rules provided are not possible given the allowed size of the password: " o))
      "help"
      (println (slurp (clojure.java.io/resource "README")))
      (println (str "Invalid subcommand: '" subcommand "'")))))
