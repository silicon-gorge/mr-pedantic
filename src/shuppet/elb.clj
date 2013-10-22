(ns shuppet.elb
  (:require
   [shuppet.aws :refer [elb-request]]
   [clj-http.client :as client]
   [clojure.string :refer [join]]
   [clojure.tools.logging :as log]
   [clojure.xml :as xml]
   [clojure.data :refer [diff]]
   [clojure.zip :as zip]
   [slingshot.slingshot :refer [throw+]]
   [clojure.data.zip.xml :refer [xml1-> attr xml-> text text= attr=]]))

(defn get-listeners
  "returns a list of maps, very specific"
  [xml]
  )

(defn get-subnets
  "returns a list of strings, can be generalised eg get-string-list name"
  [xml]
  )

(defn compare-lists
  "use indexof to see what is missing, returns map :a :b with lists, can be used for maps or strings"
  [a b]

  )

(defn get-security-groups
  "returns a list of strings, same as subnet"
  [xml])

(defn map-to-dot [prefix m]
  (map (fn [[k v]] [(str prefix "." (name k)) v])
       m))

(defn to-member [prefix i]
  (str prefix ".member." i))

(defn list-to-member [prefix list]
  (flatten (map (fn [i v]
                  (cond
                   (map? v) (map-to-dot (to-member prefix i) v)
                   :else [(to-member prefix i) v]))
                (iterate inc 1)
                list)))

(defn to-aws-format
  "Transforms shuppet config to aws config format"
  [config]
  (apply hash-map (flatten (map (fn [[k v]]
                                  (let [k (name k)]
                                    (cond (sequential? v) (list-to-member k v)
                                          (map? v) (map-to-dot k v)
                                          :else [ k v])))
                                config))))

(defn create-elb [config]
  (let [elb-config (dissoc config :HealthCheck)
        health-check-config (select-keys config [:LoadBalancerName :HealthCheck])]
    (elb-request (merge {"Action" "CreateLoadBalancer"} (to-aws-format elb-config)))
    (elb-request (merge {"Action" "ConfigureHealthCheck"} (to-aws-format health-check-config)))))

(defn find-elb [name]
  (elb-request {"Action" "DescribeLoadBalancers"
                "LoadBalancerNames.member.1" name}))

(defn check-text-value [zipper key-list value]
  (let [remote-value (apply
                      xml1->
                      zipper
                      (concat [:DescribeLoadBalancersResult :LoadBalancerDescriptions :member]
                              key-list
                              [text]))]
    (cond
     (nil? remote-value) (throw+ {:type ::missing-value :key key-list })
     (not (= remote-value value)) (throw+ {:type ::wrong-value :value remote-value}))))

(defn check-map-value [remote k1 v1]
  (map (fn [[k2 v2]]
         (check-text-value remote [k1 k2] v2))
       v1))

(defn check-basic-values [local remote]
  (map (fn [[k v]]
         (cond
          (string? v) (check-text-value remote [k] v)
          (map? v) (check-map-value remote k v)))
       local))

(defn create-listeners [config]
  (elb-request (merge {"Action" "CreateLoadBalancerListeners"} (to-aws-format config))))

(defn delete-listener [elb-name listener-port]
  (elb-request {"Action" "DeleteLoadBalancerListeners"
                "LoadBalancerName" elb-name
                "LoadBalancerPorts.member.1" listener-port}))

(defn delete-elb [elb-name]
  (elb-request {"Action" "DeleteLoadBalancer"
                "LoadBalancerName" elb-name}))

(defn ensure-config [local]
  (let [remote (find-elb (:LoadBalancerName local))
        zipper (zip/xml-zip (xml/parse remote))]
    (check-basic-values local zipper)
;    (ensure-security-groups)
 ;   (ensure-listener)
;    (ensure-subnet)
    ))
