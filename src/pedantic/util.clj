(ns pedantic.util
  (:require [clojure.walk :as walk]
            [org.tobereplaced.lettercase :refer [lower-hyphen-keyword]])
  (:import [java.io StringWriter PrintWriter]))

(defn str-stacktrace
  [e]
  (let [sw (StringWriter.)]
    (.printStackTrace e (PrintWriter. sw))
    (str sw)))

(defn url-decode
  [s]
  (java.net.URLDecoder/decode s "UTF-8"))

(defn keywordize-keys
  [m]
  (let [f (fn [[k v]] [(lower-hyphen-keyword (name k)) v])]
    (walk/postwalk (fn [x] (if (map? x) (into {} (map f x)) x)) m)))

(defn- str-val
  [val]
  (if (coll? val)
    val
    (str val)))

(defn without-nils
  "Remove all keys from a map that have nil/empty values."
  [m]
  (into {} (filter (comp not empty? str-val val) m)))

(defn in?
  "true if seq contains element"
  [seq element]
  (some #(= element %) seq))

(defn compare-config
  "Returns a list of two vectors
   First vector is what is present in the remote config , which are not present in the local config
   and the second vector is those present in the local config, which are not applied to the aws config yet"
  [local remote]
  (list (vec (filter #(not (in? (set local) %)) (set remote)))
        (vec (filter #(not (in? (set remote) %)) (set local)))))

(defn join-policies
  [policy-docs]
  {:Statement (vec (map #(first (:Statement %)) policy-docs))})

(defn to-vec
  [item]
  (if (string? item) [item] (vec item)))

(defn- create-policy-statement
  ([sid effect principal action resource conditions]
   (let [effect (if (empty? effect) "Allow" effect)]
     (without-nils {:Effect effect
                    :Sid sid
                    :Principal principal
                    :Action (to-vec action)
                    :Resource (to-vec resource)
                    :Conditions conditions}))))

(defn create-policy
  ([sid effect principal action resource conditions]
   {:Statement [(create-policy-statement sid effect principal action resource conditions)]})
  ([{:keys [Sid Effect Principal Action Resource Conditions]}]
   (create-policy Sid Effect Principal Action Resource Conditions)))

(defn one-or-more
  "If xs is sequential (e.g. a vector or list of things) then apply f to
  every item in xs, otherwise, apply f to xs. Useful where you need to
  support 'one or more' and the input may be a single item or multiple
  items."
  [f xs]
  (when xs
    (if (sequential? xs)
      (doseq [x xs] (f x))
      (f xs))))

(defn dedupe-tags
  [tags]
  (->> tags
       (map (fn [{:keys [key value]}] [key value]))
       (into {})
       (map (fn [[key value]] {:key key :value value}))))

(defn required-tags
  [application]
  [{:key "PedanticApplication" :value application}])
