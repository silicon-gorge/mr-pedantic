(ns shuppet.util
  (:require
   [clj-time.local :refer [local-now to-local-date-time]]
   [clj-time.format :as format]
   [clojure.string :refer [join upper-case split]]
   [slingshot.slingshot :refer [try+ throw+]]
   [clojure.data.zip.xml :refer [xml1-> text]]
   [environ.core :refer [env]])
  (:import [java.io StringWriter PrintWriter]))

(def rfc2616-format (format/formatter "EEE, dd MMM yyyy HH:mm:ss 'GMT'"))
(defn rfc2616-time
  []
  (format/unparse rfc2616-format (local-now)))

(defn str-stacktrace
  [e]
  (let [sw (StringWriter.)]
    (.printStackTrace e (PrintWriter. sw))
    (str sw)))
