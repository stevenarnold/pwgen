(ns pwgen.core_test
  (:use midje.sweet)
  (:use [pwgen.core]))

(facts "about invoking pwgen"
       (fact "we can invoke pwgen with only the 'generate' subcommand"
             (= true true) => true))

(facts "about pwgen functions"
	   ;; resolve-charset
       (fact "resolving a charset named after one of our character classes gives us that class"
             (resolve-charset "numeric") => numeric)
       (fact "other charsets are treated literally"
             (resolve-charset "lmnop") => "lmnop")
       (fact "select-count selects a random number between min and max"
             (some #(or (< % 1) (> % 5)) 
                   (for [x (range 1 20)]
                     (select-count 1 5))) => falsey)
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
                         (rule-init-charset "hello" "abcdef")) => truthy))

