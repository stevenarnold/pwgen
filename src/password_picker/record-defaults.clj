(defmacro defrecord+defaults
  "Defines a new record, along with a new-RecordName factory function that
   returns an instance of the record initialized with the default values
   provided as part of the record's slot declarations.  e.g.
   (defrecord+ Foo [a 5 b \"hi\"])
   (new-Foo)
   => #user.Foo{:a 5, :b \"hi\"}"
  [name slots & etc]
  (let [fields (->> slots (partition 2) (map first) vec)
        defaults (->> slots (partition 2) (map second))]
    `(do
      (defrecord ~name
         ~fields
         ~@etc)
       (defn ~(symbol (str "new-" name))
         ~(str "A factory function returning a new instance of " name
            " initialized with the defaults specified in the corresponding defrecord+ form.")
         []
         (~(symbol (str name \.)) ~@defaults))
       ~name)))