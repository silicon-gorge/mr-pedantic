(ns pedantic.util
  (:require [clj-time.local :as local]
            [clj-time.format :as format])
  (:import [java.io StringWriter PrintWriter]))

(def ^:private rfc2616-format
  (format/formatter "EEE, dd MMM yyyy HH:mm:ss 'GMT'"))

(defn rfc2616-time
  []
  (format/unparse rfc2616-format (local/local-now)))

(defn str-stacktrace
  [e]
  (let [sw (StringWriter.)]
    (.printStackTrace e (PrintWriter. sw))
    (str sw)))
