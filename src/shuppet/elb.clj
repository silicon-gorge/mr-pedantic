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

(defn- sg-names-to-ids [config]
  (assoc config :SecurityGroups (map #(security-group-id %) (:SecurityGroups config))))

(defn- get-elements [xml path]
  (apply xml-> xml (concat [:DescribeLoadBalancersResult :LoadBalancerDescriptions :member] path)))

(defn- elb-name [config]
  (:LoadBalancerName config))

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

(defn ensure-health-check [{:keys [local remote] :as config}]
  (let [remote-health-check (-> remote
                                (get-elements [:HealthCheck children])
                                (children-to-map))
        local-health-check (values-tostring (:HealthCheck local))]
    (when-not (= remote-health-check local-health-check)
      (create-healthcheck local))
    config))

(defn ensure-listeners [{:keys [local remote] :as config}]
  (let [remote (-> remote
                   (get-elements [:ListenerDescriptions :member children])
                   (filter-children :Listener)
                   (children-to-maps))
        name (elb-name local)
        local (map #(values-to-uppercase %) (:Listeners local))
        [r l] (compare-config local remote)]
    (when-not (empty? r)
      (elb-request (into {"Action" "DeleteLoadBalancerListeners"
                          "LoadBalancerName" name}
                         (list-to-member "LoadBalancerPorts" (map #(:LoadBalancerPort %) r)))))
    (when-not (empty? l)
      (elb-request (merge {"Action" "CreateLoadBalancerListeners"
                          "LoadBalancerName" name}
                          (apply hash-map (flatten (list-to-member "Listeners" l))))))
    config))

(defn ensure-subnets [{:keys [local remote] :as config}]
  (let [remote (-> remote
                   (get-elements [:Subnets :member children]))
        name (elb-name local)
        local (:Subnets local)
        [r l] (compare-config local remote)]
    (when-not (empty? r)
      (elb-request (into {"Action" "DetachLoadBalancerFromSubnets"
                          "LoadBalancerName" name}
                         (list-to-member "Subnets" r))))
    (when-not (empty? l)
      (elb-request (into {"Action" "AttachLoadBalancerToSubnets"
                          "LoadBalancerName" name}
                         (list-to-member "Subnets" l))))
    config))

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

(defn ensure-config [local]
  (let [local (sg-names-to-ids local)
        remote (find-elb (:LoadBalancerName local))]
    (if remote
      (-> {:local local :remote remote}
          (check-fixed-values)
          (ensure-health-check)
          (ensure-security-groups)
          (ensure-subnets)
          (ensure-listeners))
      (-> local
          (create-elb)
          (create-healthcheck)))))
