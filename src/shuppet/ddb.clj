(ns shuppet.ddb
  (:require
   [shuppet
    [util :refer :all]
    [signature :refer [get-signed-request]]
    [campfire :as cf]]
   [environ.core :refer [env]]
   [clj-http.client :as client]
   [clojure.string :refer [replace]]
   [clojure.data.json :refer [write-str read-str]]
   [clojure.tools.logging :as log]
   [slingshot.slingshot :refer [try+ throw+]])
  (:refer-clojure :exclude [replace]))

(def ^:private ddb-version (replace (env :service-aws-ddb-api-version) #"-" ""))
(def ^:private action-prefix (str  "DynamoDB_" ddb-version "."))

(defn- nil-returnable-exception?
  [status {:keys [__type]} action]
  (and
   (= status 400)
   (and (.endsWith __type "ResourceNotFoundException")
        (or (= action :DescribeTable)
            (= action :DeleteTable)))))

(defn- post-request
  [action body]
  (let [request (get-signed-request "ddb" {:body (write-str body)
                                           :headers {:x-amz-target (str action-prefix (name action))
                                                     :content-type "application/x-amz-json-1.0"}
                                           :method :post})
        response  (client/post (request :url) {:headers (request :headers)
                                               :body (request :body)
                                               :as :json
                                               :throw-exceptions false})
        status (:status response)]
    (log/info "Ddb action: " action ", body: " body)
    (if (= status 200)
      (:body response)
      (let [body (read-str (:body response) :key-fn keyword)]
        (when-not (nil-returnable-exception? status body action)
          (throw-aws-exception "DynamoDB" action (request :url) status body :json))))))

(defn- create-attr-defns
  [attrs]
  {:AttributeDefinitions (set (map (fn [[k v]]
                                     {:AttributeName (name k)
                                      :AttributeType (name v)}) attrs))})
(defn- create-key-schema
  [attrs]
  {:KeySchema (set (map (fn [[k v]]
                          {:AttributeName (name k)
                           :KeyType (name v)}) attrs))})
(defn- formatted-projection
  [{:keys [NonKeyAttributes ProjectionType]}]
  (without-nils
   (zipmap [:NonKeyAttributes :ProjectionType]
           [(set (vec NonKeyAttributes)) ProjectionType])))

(defn- create-sec-index
  [opts]
  (let [key-schema (create-key-schema (:KeySchema opts))]
    (merge key-schema {:IndexName (:IndexName opts)
                       :Projection (formatted-projection (:Projection opts))})))

(defn- create-sec-indexes
  [attrs]
  {:LocalSecondaryIndexes (set (map create-sec-index attrs))})

(defn- create-table-body
  [opts]
  (let [attr-defns (create-attr-defns (:AttributeDefinitions opts))
        key-schema (create-key-schema (:KeySchema opts))
        sec-indexes (create-sec-indexes (:LocalSecondaryIndexes opts))]
    (merge attr-defns
           key-schema
           sec-indexes
           {:ProvisionedThroughput (:ProvisionedThroughput opts)
            :TableName (:TableName opts)})))

(defn- create-table
  [opts]
  (post-request :CreateTable (create-table-body opts))
  (cf/info (str "I've created a new dynamodb table called '" (:TableName opts) "'")))

(defn- formatted-throughput
  [{:keys [ProvisionedThroughput]}]
  (without-nils
   (zipmap [:ReadCapacityUnits :WriteCapacityUnits]
           [(:ReadCapacityUnits ProvisionedThroughput)
            (:WriteCapacityUnits ProvisionedThroughput)])))

(defn- formatted-index
  [index]
  (without-nils
   (zipmap [:IndexName :KeySchema :Projection]
           [(:IndexName index)
            (set (:KeySchema index))
            (formatted-projection (:Projection index))])))

(defn- formatted-indexes
  [{:keys [LocalSecondaryIndexes]}]
  (map formatted-index LocalSecondaryIndexes))

(defn- to-local-format
  [opts]
  (without-nils (zipmap [:TableName :AttributeDefinitions :KeySchema
                         :ProvisionedThroughput :LocalSecondaryIndexes]
                        [(:TableName opts)
                         (set (:AttributeDefinitions opts))
                         (set (:KeySchema opts))
                         (formatted-throughput opts)
                         (set (formatted-indexes opts)) ])))

(defn- confirm-and-delete
  [{:keys [ForceDelete TableName]} local remote]
  (when-not (= (dissoc local :ProvisionedThroughput) (dissoc remote :ProvisionedThroughput))
    (if ForceDelete
      (future (dorun
               (post-request :DeleteTable {:TableName TableName})
               (cf/info (str "I've succesfully deleted the dynamodb table '" TableName "'"))
               (Thread/sleep 45000)
               (post-request :CreateTable local)
               (cf/info (str "I've created a new dynamodb table called '" TableName "'"))))
      (throw-aws-exception "DynamoDB" "POST" (env :service-aws-ddb-url) "400" {:__type "Table Configuration Mismatch" :message "Mismatch in table configuration. If you want to apply the current local configuration, please add :ForceDelete true confirming that its ok to delete the current table and create a new table with the new configuration.Note: All data in the current table will be lost after this operation"} :json))))

(defn- compare-table
  [opts body]
  (let [local (without-nils (create-table-body opts))
        remote (without-nils (to-local-format body))]
    (when-not (= local remote)
      (if-not (= (:ProvisionedThroughput local) (:ProvisionedThroughput remote))
        (do
          (post-request :UpdateTable (merge {:ProvisionedThroughput (:ProvisionedThroughput local)}
                                            {:TableName (:TableName local)}))
          (cf/info (str "I've succesfully updated the dynamodb table "
                        (:TableName local)
                        " with :ProvisionedThroughput "
                        (:ProvisionedThroughput local))))
        (confirm-and-delete opts local remote)))))

(defn- ensure-ddb
  [{:keys [TableName] :as opts}]
  (let [body {:TableName TableName}]
    (if-let [response (post-request :DescribeTable body)]
      (compare-table opts (:Table response))
      (create-table opts))))

(defn ensure-ddbs
  [{:keys [DynamoDB]}]
  (doseq [ddb DynamoDB]
    (ensure-ddb ddb)))

(defn- delete-ddb
  [{:keys [TableName]}]
  (Thread/sleep 40000) ;Table creation might take a while
  (post-request :DeleteTable {:TableName TableName}))

(defn delete-ddbs
  [{:keys [DynamoDB]}]
  (doseq [ddb DynamoDB]
    (delete-ddb ddb)))
