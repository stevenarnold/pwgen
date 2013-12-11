(ns pwgen.core_test
  (:use midje.sweet)
  (:use [midje.util :only [testable-privates]])
  (:use [pwgen.core])
  (:require [me.raynes.fs :refer [temp-dir copy file]]))

(testable-privates pwgen.core read-profiles)

(facts "about invoking pwgen"
       (fact "we can invoke pwgen with only the 'generate' subcommand"
             (= true true) => true))

(facts "about pwgen functions"
       (facts "about charset functions"
              (fact "generate-candidate creates candidates that match the given charset"
                    (re-matches #"^[abcdef]+$" 
                                (generate-candidate 10 20 0 0 :charset "abcdef")) => truthy)
              (facts "about resolve-charset"
                     (fact "resolving a charset named after a character class gives us that class"
                           (resolve-charset "numeric") => numeric)
                     (fact "other charsets are treated literally"
                           (resolve-charset "lmnop") => "lmnop"))
              (fact "normalize-charset escapes strings and returns a regex pattern"
                    (normalize-charset special) => #"[~`\!@\#\$\%\^\&\*\(\)\-_=\+\]\}\[\{;:\,\<\.\>\/\?\'\|]")
              (facts "about rule-charset-count"
                     (fact "it ensures that the candidate string has at least the number of chars specified"
                           (let [new-candidate (rule-charset-count "some random text" 5 numeric)]
                             (count (clojure.core/re-seq (normalize-charset numeric) new-candidate))) => 5)
                     (fact "it ensures that the candidate string has at most the number of chars specified"
                           (let [new-candidate (rule-charset-count "some 012345 random text" 3 numeric)]
                             (count (clojure.core/re-seq (normalize-charset numeric) new-candidate))) => 3))
              (fact "rule-init-charset requires initial character to be in the given charset"
                    (re-matches #"^[abcdef].*$" 
                                (rule-init-charset "hello" "abcdef")) => truthy)
              (fact "rule-ending-charset requires ending character to be in the given charset"
                    (re-matches #"^.*[abcdef]$" 
                                (rule-ending-charset "hello" "abcdef")) => truthy))
       (fact "select-count selects a random number between min and max"
             (some #(or (< % 1) (> % 5)) 
                   (for [x (range 1 20)]
                     (select-count 1 5))) => falsey)
       (facts "about generate-charset-counts"
              (fact "selects random values between min and max"
                    (let [[num-numbers num-capitals num-specials]
                          (generate-charset-counts 15 25 1 3 1 3 1 3 alpha alphanumeric)]
                      (and (and (>= num-specials 1) (<= num-specials 3))
                           (and (>= num-capitals 1) (<= num-capitals 3))
                           (and (>= num-numbers 1) (<= num-numbers 3)))) => truthy)
              (fact "passing in impossible parameters prints error notice"
                    (generate-charset-counts 10 10 5 5 5 5 5 5 alpha alphanumeric) => 
                    ;; Would like to introspect into this object a little more
                    (throws Object))
              )
       ;; Not clear if :ending-charset always produces a password with that ending char
       ;; Maybe passwords ending in * or + produce an exception (if randomly generated)
       (facts "about generate"
              (facts "a simple invocation produces passwords"
                     (let [example-args {:initial-charset alpha 
                                         :max-numbers 4 :use-profile "standard" :allow-spaces 50 :min-special 6 
                                         :max 30 :create-profile "" :max-capitals -1 :max-special -1 :memorable 95
                                         :special-charset "" :min 20 
                                         :charset "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789()*:"
                                         :min-numbers 1 :ending-charset alphanumeric
                                         :min-capitals 1}
                           example-args2 (assoc example-args :special-charset "()*:")
                           character-pattern (re-pattern (str "^[" (normalize-charset (example-args :charset)) " ]+$"))
                           generated-passwords (repeatedly 20
                                                        #(let [new-pass (generate example-args2)]
                                                           (re-matches character-pattern new-pass)))
                           example-pass (nth generated-passwords 0)
                           example-pass-length (count example-pass)]
                       (fact "with the right length"
                             (and (>= example-pass-length 20) (<= example-pass-length 30)) => truthy)
                       (fact "consisting of valid characters"
                             (not-any? nil? generated-passwords) => truthy)
                       (fact "that are unique"
                             (= (count (distinct generated-passwords)) (count generated-passwords)) => truthy)
                       (println "about to test having the right number of symbols")
                       (fact "always have the right number of symbols"
                             (let [specials-counts (map #(- (count %) (count (charset-diff % (normalize-charset 
                                                                                               (example-args2 :special-charset))))) 
                                                        generated-passwords)]
                               (println "specials-counts = " specials-counts)
                               (every? #(>= % 6) specials-counts)) => truthy)
                       )
                     )
              (fact "an invocation that used to fail the rules analysis now works"
                    (let [example-args {:initial-charset alpha 
                                         :use-profile "standard" :allow-spaces 50 :min-special 2 
                                         :max 30 :create-profile "" :max-capitals -1 :max-special -1 :memorable 95
                                         :special-charset special :min 20 
                                         :charset all-chars :max-numbers 4
                                         :min-numbers 1 :ending-charset alphanumeric
                                         :min-capitals 1}
                          password (generate example-args)
                          pwlen (count password)]
                      (and (>= pwlen 20) (<= pwlen 30)) => truthy))
              )
       (facts "about profile functions"
              (fact "can modify a profile"
                    (let [sp {"standard" {"max-numbers" 4, "max" 30, "min-capitals" 1,
                                          "make-memorable-pct" 100, "min-numbers" 1, "max-special" 4,
                                          "allow-spaces" 0, "min" 20, "max-capitals" 4,
                                          "special-charset" "special","min-special" 1}}
                          ;; Modify the profile
                          ans (add-profile-map "standard" (assoc-in sp ["standard" "min"] 10))]
                      (get-in-profile ans ["standard" "min"]) => 10))
              (future-fact "can override an existing profile with force flag"
                           ;; Get profile
                           ;; Add to profile
                           ;; Get new profile
                           ;; Override key w/force && different profile
                           ;; Expect difference
                           true => truthy
                           )
              (future-fact "can't override an existing profile without force flag"
                           ;; Get profile
                           ;; Add to profile
                           ;; Get new profile
                           ;; Override key w/o force && different profile
                           ;; Expect equivalence
                           true => truthy
                           )
              )
       )

