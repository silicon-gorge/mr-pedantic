(ns shuppet.elb
  (:require
   [shuppet
    [aws :refer [elb-request ec2-request security-group-id]]
    [util :refer :all]]
   [clj-http.client :as client]
   [clojure.tools.logging :as log]
   [clojure.xml :as xml]
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
  (log/info "I've created a new health check config for" (elb-name config))
  config)

(defn create-elb [config]
  (elb-request (merge {"Action" "CreateLoadBalancer"} (to-aws-format  (elb-config config))))
  (log/info "I've created a new ELB called" (elb-name config))
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

(defn- ensure-health-check [{:keys [local remote] :as config}]
  (let [remote-health-check (-> remote
                                (get-elements [:HealthCheck children])
                                (children-to-map))
        local-health-check (values-tostring (:HealthCheck local))]
    (when-not (= remote-health-check local-health-check)
      (create-healthcheck local)
      (log/info "I've replaced healthcheck config for" (elb-name local)))
    config))

(defn- update-elb [elb-name action prefix fields]
  (elb-request (merge {"Action" (name action)
                       "LoadBalancerName" elb-name}
                      (apply hash-map (flatten (list-to-member (name prefix) fields)))))
  (log/info "I had to" (name action) fields "on" elb-name))

(defn- ensure-listeners [{:keys [local remote] :as config}]
  (let [remote (-> remote
                   (get-elements [:ListenerDescriptions :member children])
                   (filter-children :Listener)
                   (children-to-maps))
        name (elb-name local)
        local (map #(values-to-uppercase %) (:Listeners local))
        [r l] (compare-config local remote)]
    (when-not (empty? r)
      (update-elb name :DeleteLoadBalancerListeners :LoadBalancerPorts (map #(:LoadBalancerPort %) r)))
    (when-not (empty? l)
      (update-elb name :CreateLoadBalancerListeners :Listeners l))
    config))

(defn- ensure-subnets [{:keys [local remote] :as config}]
  (let [remote (-> remote
                   (get-elements [:Subnets :member children]))
        name (elb-name local)
        local (:Subnets local)
        [r l] (compare-config local remote)]
    (when-not (empty? r)
      (update-elb name :DetachLoadBalancerFromSubnets :Subnets r))
    (when-not (empty? l)
      (update-elb name :AttachLoadBalancerToSubnets :Subnets l))
    config))

(defn- ensure-security-groups [{:keys [local remote] :as config}]
  (let [remote (-> remote
                    (get-elements [:SecurityGroups :member children]))
        name (elb-name local)
        local (:SecurityGroups local)]
    (when-not (= (set local) (set remote))
      (update-elb name :ApplySecurityGroupsToLoadBalancer :SecurityGroups local))
    config))

(defn ensure-elb [{:keys [LoadBalancer] :as config}]
  (let [local (sg-names-to-ids LoadBalancer)
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
          (create-healthcheck))))
  config)

(defn delete-elb [config]
  (elb-request {"Action" "DeleteLoadBalancer"
                "LoadBalancerName" (get-in config [:LoadBalancer :LoadBalancerName])})
  config)
