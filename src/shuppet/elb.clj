(ns shuppet.elb
  (:require
   [shuppet
    [aws :refer [elb-request ec2-request security-group-id]]
    [util :refer :all]]
   [clj-http.client :as client]
   [clojure.tools.logging :as log]
   [clojure.xml :as xml]
   [clojure.data :refer [diff]]
   [clojure.zip :as zip :refer [children]]
   [slingshot.slingshot :refer [throw+ try+]]
   [clojure.data.zip.xml :refer [xml1-> text xml->]]))

(defn get-elements [xml path]
  (apply xml-> xml (concat [:DescribeLoadBalancersResult :LoadBalancerDescriptions :member] path)))

(defn- get-listeners
  "returns a list of maps, very specific"
  [xml]
  )

(defn- get-subnets
  "returns a list of strings, can be generalised eg get-string-list name"
  [xml]
  )

(defn- compare-lists
  "use indexof to see what is missing, returns map :a :b with lists, can be used for maps or strings"
  [a b]

  )

(defn- get-security-groups
  "returns a list of strings, same as subnet"
  [xml])

(defn- map-to-dot [prefix m]
  (map (fn [[k v]] [(str prefix "." (name k)) (str v)])
       m))

(defn- to-member [prefix i]
  (str prefix ".member." i))

(defn- list-to-member [prefix list]
  (map (fn [i v]
         (cond
          (map? v) (map-to-dot (to-member prefix i) v)
          :else [(to-member prefix i) (str v)]))
       (iterate inc 1)
       list))

(defn- to-aws-format
  "Transforms shuppet config to aws config format"
  [config]
  (apply hash-map (flatten (map (fn [[k v]]
                                  (let [k (name k)]
                                    (cond (sequential? v) (list-to-member k v)
                                          (map? v) (map-to-dot k v)
                                          :else [k (str v)])))
                                config))))

(defn- healthcheck-config [config]
  (select-keys config [:LoadBalancerName :HealthCheck]))

(defn- elb-config [config]
  (dissoc config :HealthCheck))

(defn create-healthcheck [config]
  (elb-request (merge {"Action" "ConfigureHealthCheck"} (to-aws-format (healthcheck-config config))))
  config)

(defn create-elb [config]
  (elb-request (merge {"Action" "CreateLoadBalancer"} (to-aws-format  (elb-config config))))
  config)

(defn find-elb [name]
  (try+
   (elb-request {"Action" "DescribeLoadBalancers"
                 "LoadBalancerNames.member.1" name})
   (catch [:code "LoadBalancerNotFound"] _
       nil)))

(defn- check-string-value [remote k v]
  (let [remote-value (xml1->
                      remote
                      :DescribeLoadBalancersResult :LoadBalancerDescriptions :member k text)]
    (cond
     (nil? remote-value) (throw+ {:type ::missing-value :key k })
     (not (= remote-value (str v))) (throw+ {:type ::wrong-value :key k
                                           :local-value v
                                           :remote-value remote-value}))))

(defn- check-fixed-values [{:keys [local remote] :as config}]
  (dorun (map (fn [[k v]]
                (cond
                 (string? v) (check-string-value remote k v)))
              local))
  config)

(defn- create-listeners [config]
  (elb-request (merge {"Action" "CreateLoadBalancerListeners"} (to-aws-format config))))

(defn- delete-listener [elb-name listener-port]
  (elb-request {"Action" "DeleteLoadBalancerListeners"
                "LoadBalancerName" elb-name
                "LoadBalancerPorts.member.1" listener-port}))

(defn- delete-elb [elb-name]
  (elb-request {"Action" "DeleteLoadBalancer"
                "LoadBalancerName" elb-name}))

(defn ensure-health-check [{:keys [local remote] :as config}]
  (let [remote-health-check (-> remote
                                (get-elements [:HealthCheck children])
                                (children-to-map))
        local-health-check (values-tostring (:HealthCheck local))]
    (when-not (= remote-health-check local-health-check)
      (create-healthcheck local))
    config))

(defn ensure-listeners [{:keys [local remote] :as config}]
  (let [remote-listenters (-> remote
                              (get-elements [:ListenerDescriptions :member children])
                              (filter-children :Listener)
                              (children-to-maps))]
    ; compare lists
    config))

(defn ensure-subnets [{:keys [local remote] :as config}]
  (let [remote-subnets (-> remote
                           (get-elements [:Subnets :member children]))]
                                        ; compare
    ;need ids??
    remote-subnets))

(defn elb-name [config]
  (:LoadBalancerName config))

(defn ensure-security-groups [{:keys [local remote] :as config}]
  (let [remote (set (-> remote
                        (get-elements [ :SecurityGroups :member children])))
        name (elb-name local)
        local (:SecurityGroups local)]
    (when-not (= (set local) (set remote))
      (elb-request (into {"Action" "ApplySecurityGroupsToLoadBalancer"
                          "LoadBalancerName" name}
                         (list-to-member "SecurityGroups" local))))
    config))

(defn sg-names-to-ids [config]
  (assoc config :SecurityGroups (map #(security-group-id %) (:SecurityGroups config))))

(defn ensure-config [local]
  (let [local (sg-names-to-ids local)
        remote (find-elb (:LoadBalancerName local))]
    (if remote
      (-> {:local local :remote remote}
          (check-fixed-values)
          (ensure-health-check)
          (ensure-security-groups)
          (ensure-listeners))
      (-> local
          (create-elb)
          (create-healthcheck)))))
