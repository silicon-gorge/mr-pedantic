(ns shuppet.elb
  (:require
   [shuppet
    [campfire :as cf]
    [securitygroups :refer [sg-id]]
    [signature :refer [v4-auth-headers]]
    [util :refer :all]]
   [clj-http.client :as client]
   [environ.core :refer [env]]
   [clojure.tools.logging :as log]
   [clojure.xml :as xml]
   [clojure.zip :as zip :refer [children xml-zip]]
   [slingshot.slingshot :refer [throw+ try+]]
   [clojure.data.zip.xml :refer [xml1-> text xml->]]))

(def ^:const ^:private elb-url (env :service-aws-elb-url))
(def ^:const ^:private elb-version (env :service-aws-elb-api-version))

(defn get-request
  [params]
  (let [url (str elb-url  "/?" (map-to-query-string
                                (merge {"Version" elb-version} params)))
        auth-headers (v4-auth-headers {:url url} )
        response (client/get url
                             {:headers auth-headers
                              :as :stream
                              :throw-exceptions false})
        status (:status response)
        body (-> (:body response)
                 (xml/parse)
                 (zip/xml-zip))]
    (if (= 200 status)
      body
      (throw-aws-exception "ELB" (get params "Action") url status body))))

(defn- sg-names-to-ids [config vpc-id]
  (assoc config :SecurityGroups (map #(sg-id % vpc-id) (:SecurityGroups config))))

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
(get-request (merge {"Action" "ConfigureHealthCheck"} (to-aws-format (healthcheck-config config))))
  (cf/info (str "I've created a new health check config for elb " (elb-name config)))
  config)

(defn create-elb [config]
  (get-request (merge {"Action" "CreateLoadBalancer"} (to-aws-format  (elb-config config))))
   (cf/info (str "I've created a new ELB called " (elb-name config)))
  config)

(defn- find-elb [name]
  (try+
   (get-request {"Action" "DescribeLoadBalancers"
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
      (cf/info (str "I've replaced healthcheck config for elb " (elb-name local))))
    config))

(defn- update-elb [elb-name action prefix fields]
  (get-request (merge {"Action" (name action)
                       "LoadBalancerName" elb-name}
                      (apply hash-map (flatten (list-to-member (name prefix) fields)))))
  (cf/info (str "I had to " (name action) " " (vec fields) " on " elb-name)))

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

(defn ensure-elb [{:keys [LoadBalancer SecurityGroups]}]
  (when LoadBalancer
    (let [vpc-id (or (:VpcId LoadBalancer) (:VpcId (first SecurityGroups)))
          local (sg-names-to-ids LoadBalancer vpc-id)
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
            (create-healthcheck))))))

(defn delete-elb [{:keys [LoadBalancer]}]
  (when LoadBalancer
    (get-request {"Action" "DeleteLoadBalancer"
                  "LoadBalancerName" (:LoadBalancerName LoadBalancer)})))
