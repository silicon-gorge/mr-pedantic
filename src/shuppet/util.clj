(ns shuppet.util)


(defn without-nils
  "Remove all keys from a map that have nil values."
  [m]
  (into {} (filter (comp not nil? val) m)))
