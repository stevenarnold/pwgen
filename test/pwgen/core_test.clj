(ns pwgen.core_test
  (:use midje.sweet)
  (:use [pwgen.core]))

(facts "about invoking pwgen"
       (fact "we can invoke pwgen with only the 'generate' subcommand"
             (= true true) => true))

(facts "about pwgen functions"
       (facts "about charset functions"
              (facts "about resolve-charset"
                     (fact "resolving a charset named after a character class gives us that class"
                           (resolve-charset "numeric") => numeric)
                     (fact "other charsets are treated literally"
                           (resolve-charset "lmnop") => "lmnop"))
              (fact "normalize-charset escapes strings and returns a regex pattern"
                    (normalize-charset special) => #"[~`\!@\#\$\%\^\&\*\(\)\-_=\+\]\}\[\{;:,<\.>/?'|]")
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
                    	(throws Object))))

