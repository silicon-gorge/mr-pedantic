(ns shuppet.util
  (:require [clojure.string :refer [join]]))

(defn children-to-map
  "transform children of an xml element to a map"
  [children]
  (apply hash-map (flatten
                   (map (fn [item]
                          [(:tag item) (join (:content item))])
                        children))))

(defn filter-children
  "select xml elements by element name"
  [children key]
  (filter #(= (:tag %) key)
          children))

(defn children-to-maps
  "transform children of an xml element to a list of maps"
  [children]
  (map #(children-to-map (:content %))
       children))

(defn without-nils
  "Remove all keys from a map that have nil/empty values."
  [m]
  (into {} (filter (comp not empty? val) m)))

(defn in?
  "true if seq contains element"
  [seq element]
  (some #(= element %) seq))

(defn values-tostring [m]
  (into {} (map (fn [[k v]] [k (str v)]) m)))

(defn group-record
  "Creates a Ingress/Egress config for a security group"
  [protocol from-port to-port ip-ranges]
  (let [record (without-nils {:IpProtocol protocol
                            :FromPort (str from-port)
                            :ToPort (str to-port)})]
    (map #(merge record {:IpRanges %}) ip-ranges)   ))
