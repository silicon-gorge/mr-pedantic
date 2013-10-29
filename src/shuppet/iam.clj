(ns shuppet.iam
  (:require
   [shuppet.aws :refer [iam-request]]
   [shuppet.util :refer :all]
   [clojure.tools.logging :as log]
   [clojure.data.zip.xml :refer [xml1-> text xml->]]
   [slingshot.slingshot :refer [try+ throw+]]))

(defn- process
  [action params]
  (iam-request (merge params {"Action" (name action)})))

(defn- role-exists? [name]
  (not (empty? (process :GetRole {"RoleName" name}))))

;(prn (role-exists? "filter-gigs2"))
