(ns shuppet.test-util
  (:require [clojure.test :refer [deftest]]))

(defmacro lazy-fact-group [& body]
  `(deftest ~'_ ~@body))
