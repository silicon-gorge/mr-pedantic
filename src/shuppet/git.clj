(ns shuppet.git
  (:require [clj-time
             [core :as time]
             [format :as fmt]]
            [clojure.java.io :refer [resource]]
            [clojure.string :as str]
            [clojure.tools.logging :refer [info warn error]]
            [environ.core :refer [env]]
            [io.clj.logging :refer [with-logging-context]]
            [slingshot.slingshot :refer [try+ throw+]]
            [tentacles
             [data :as data]
             [repos :as repos]]))

(intern 'tentacles.core 'url (env :github-base-url))
(intern 'tentacles.core 'defaults {:oauth-token (env :github-auth-token)})

(def default-config
  (slurp (resource "default.clj")))

(def ^:private organisation
  (env :github-organisation))

(def ^:private valid-environments
  (->
   (str/split (env :environments) #",")
   (set)
   (disj "local" "poke")
   (conj "prod")))

(defn- repo-branch
  "Only look for the prod branch when environment = prod"
  [environment name]
  (if (and (not= environment name) (= environment "prod"))
    "prod"
    "master"))

(defn- send-error
  [status message]
  (throw+ {:type ::git
           :status status
           :message message}))

(defn- busted?
  [response]
  (if-let [status (:status response)]
    (or (< status 200) (>= status 400))
    false))

(defn- data-from-repo
  [environment name]
  (try
    (let [file (str name ".clj")
          branch (repo-branch environment name)]
      (:content (repos/contents organisation name file {:ref branch
                                                        :str? true})))
    (catch Exception e
      (error e "Failed to get data from repository"))))

(defn- *get-data
  "Fetches the data corresponding to the given application from Git"
  [environment name]
  (if-let [data (data-from-repo environment name)]
    data
    (send-error 404 "Missing Shuppet configuration")))

(defn get-data
  ([environment]
     (get-data environment environment))
  ([environment name]
     (*get-data environment name)))

(defn- create-repository
  [application]
  (try
    (let [options {:auto_init true :has-downloads false :has-issues false :has-wiki false :public false}
          response (repos/create-org-repo organisation application options)]
      (if (busted? response)
        (do
          (with-logging-context {:response response}
            (warn "Failed to create repository" application))
          (throw+ {:status 500
                   :message (format "Failed to create repository %s" application)}))
        response))
    (catch Exception e
      (with-logging-context {:application application}
        (error e "Failed to create repository"))
      (throw+ {:status 500
               :message "Failed to create repository"}))))

(defn- properties-tree
  [application]
  [{:content default-config
    :mode "100644"
    :path (str application ".clj")
    :type "blob"}])

(defn- create-tree
  [application]
  (try
    (let [tree (properties-tree application)
          options {}
          response (data/create-tree organisation application tree options)]
      (if (busted? response)
        (do
          (with-logging-context {:response response}
            (warn "Failed to create tree" application))
          (throw+ {:status 500
                   :message (format "Failed to create tree %s" application)}))
        response))
    (catch Exception e
      (with-logging-context {:application application}
        (error e "Failed to create tree"))
      (throw+ {:status 500
               :message "Failed to create tree"}))))

(defn- create-commit
  [application tree]
  (try
    (let [date (fmt/unparse (fmt/formatters :date-time-no-ms) (time/now))
          info {:date date :email "mixradiobot@gmail.com" :name "Mix Radio Bot"}
          options {:author info :committer info :parents []}
          response (data/create-commit organisation application "Initial commit" tree options)]
      (if (busted? response)
        (do
          (with-logging-context {:response response}
            (warn "Failed to create commit" application))
          (throw+ {:status 500
                   :message (format "Failed to create commit %s" application)}))
        response))
    (catch Exception e
      (with-logging-context {:application application
                             :tree tree}
        (error e "Failed to create commit"))
      (throw+ {:status 500
               :message "Failed to create commit"}))))

(defn- update-ref
  [application commit]
  (try
    (let [options {:force true}
          response (data/edit-reference organisation application "heads/master" commit options)]
      (if (busted? response)
        (do
          (with-logging-context {:response response}
            (warn "Failed to update reference" application))
          (throw+ {:status 500
                   :message (format "Failed to update reference %s" application)}))
        response))
    (catch Exception e
      (with-logging-context {:application application
                             :commit commit}
        (error e "Failed to update reference"))
      (throw+ {:status 500
               :message "Failed to update reference"}))))

(defn- create-ref
  [application branch commit]
  (try
    (let [options {:force true}
          response (data/create-reference organisation application (str "refs/heads/" branch) commit options)]
      (if (busted? response)
        (do
          (with-logging-context {:response response}
            (warn "Failed to create reference" application))
          (throw+ {:status 500
                   :message (format "Failed to create reference %s" application)}))
        response))
    (catch Exception e
      (with-logging-context {:application application
                             :branch branch
                             :commit commit}
        (error e "Failed to create reference"))
      (throw+ {:status 500
               :message "Failed to create reference"}))))

(defn create-application
  [application master-only?]
  (let [repo (create-repository application)
        ssh-url (:ssh_url repo)]
    (let [tree (create-tree application)
          tree-sha (:sha tree)
          commit (create-commit application tree-sha)
          commit-sha (:sha commit)]
      (update-ref application commit-sha)
      (if-not master-only?
        (create-ref application "prod" commit-sha))
      {:name application :path ssh-url})))
