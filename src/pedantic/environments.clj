(ns pedantic.environments
  (:require [clojure.tools.logging :refer [warn]]
            [ninjakoala.ttlr :as ttlr]
            [pedantic.lister :as lister]))

(defn environments
  []
  (ttlr/state :environments))

(defn environment-names
  []
  (into (hash-set) (map :name (vals (environments)))))

(defn- map-by-name-kw
  [values]
  (apply merge (map (fn [v] {(keyword (:name v)) v}) values)))

(defn environment
  [environment-name]
  (get (environments) (keyword environment-name)))

(defn update-environments
  []
  (map-by-name-kw (filter (fn [e] (true? (get-in e [:metadata :pedantic :enabled]))) (map lister/environment (lister/environments)))))

(defn account-id
  [environment-name]
  (when-let [e (environment environment-name)]
    (get-in e [:metadata :account-id])))

(defn account-name
  [environment-name]
  (when-let [e (environment environment-name)]
    (get-in e [:metadata :account-name])))

(defn autoscaling-queue
  [environment-name]
  (when-let [e (environment environment-name)]
    (get-in e [:metadata :autoscaling-queue])))

(defn start-delay
  [environment-name]
  (when-let [e (environment environment-name)]
    (Integer/valueOf (get-in e [:metadata :pedantic :delay] 1))))

(defn healthy?
  []
  (not (zero? (count (keys (environments))))))

(defn init
  []
  (ttlr/schedule :environments update-environments (* 1000 60 30) (update-environments)))
