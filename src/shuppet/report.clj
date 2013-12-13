(ns shuppet.report)

(def ^:dynamic report (atom []))

(defn add
  "add a new entry to the report"
  [action message & [info-map]]
  (swap! report conj (merge {:action action :message message} info-map)))
