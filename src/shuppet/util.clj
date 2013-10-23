(ns shuppet.util)

(defn without-nils
  "Remove all keys from a map that have nil/empty values."
  [m]
  (into {} (filter (comp not empty? val) m)))

(defn in?
  "true if seq contains element"
  [seq element]
  (some #(= element %) seq))

(defn group-record
  "Creates a Ingress/Egress config for a security group"
  [protocol from-port to-port ip-ranges]
  (let [record (without-nils {:IpProtocol protocol
                            :FromPort (str from-port)
                            :ToPort (str to-port)})]
    (map #(merge record {:IpRanges %}) ip-ranges)   ))
