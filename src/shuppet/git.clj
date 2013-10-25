(ns shuppet.git
  (:require [environ.core :refer [env]]
            [clojure.java.io :refer [as-file make-reader copy file resource]]
            [clojure.tools.logging :refer [info warn error]]
            [clj-http.client :as client]
            [clj-time.coerce :refer [from-long]]
            [clj-time.format :refer [unparse formatters]]
            [clojure.string :refer [upper-case split]]
            [cheshire.core :refer [parse-string]]
            [slingshot.slingshot :refer [try+ throw+]]
            [me.raynes.conch :as conch])
  (:import [org.eclipse.jgit.api Git MergeCommand MergeCommand$FastForwardMode]
           [org.eclipse.jgit.api.errors InvalidRemoteException]
           [org.eclipse.jgit.errors MissingObjectException NoRemoteRepositoryException]
           [org.eclipse.jgit.revwalk RevWalk]
           [org.eclipse.jgit.treewalk TreeWalk]
           [org.eclipse.jgit.transport JschConfigSessionFactory SshSessionFactory]
           [com.jcraft.jsch JSch]
           [java.io FileNotFoundException ByteArrayInputStream]))

;; The private key of the shuppet-bot installed in SNC. Shouldn't change.
(def shuppet-private-key
  "-----BEGIN RSA PRIVATE KEY-----
MIIEoQIBAAKCAQEAnDnHv9qj0lqZAUC1K5JnxfIDG5NOzsIgeJE52FCs8v3rrM7k
D729LwXoG1b3JsGGMRd9eyz2rApCG5nKAFIQjhGmiisDyzGX5a0U/xPYCZnZhn9i
762GJWLXxAc+AVYsdoTflqiCYDHLp8pPHSGfqojHz9lfTzLXN2c/RCrO1mVRQk2W
dYyQPFiST1ilCV27VRAmrjBKFnoowxnzh57RW3SCj503GiHE6Pno+8VeikqVRGQv
S0hI9576DXYykNMgn33hcdu4N205/rF29SCIszlUegyL43/Y8xKSs41XPZCRzmnz
leVOWx5vICnWebRhQF4aKj/LucUOw3fjNBb8GwIBIwKCAQEAl8MZ0FC8ZfGb8pah
XYbodyWcnnHXhwW5JKpVcwyKwCndoI53JTv5m2TS2LrhdhsUpLe7uXwi0wKmnngj
UMS/n/PjnCnJK8nGwdtWOZ4/lExPpznrFLcx2yzRmdsmSnD3/hqtUIZwBay3NUgv
mKRR9h549cvY7dmd3gyGmf2y/CZjvrYRfb/ad/mB7sHhS+ciJImQJhPGCdi/ZjEf
GvN5b4x7KIjPinnKs+W2y0ZxR8qXfw8JHUEtlWFT5kstJRBEomgqGIB5yWT+WVZc
EzJhQY+ED2Q4rPA44yEZEGiZTT0NDULvCw0Rv7wW6hWSqXIUKsABBEk3YFP1+koG
mlC0KwKBgQDPThgRD/FjbwTN5/45xBxma11SHGSpSf2noH8qI9TDfr/1uNHrEUcN
dFthvtwj7wAmw/IjnmI5F4ElL/D3Qpp/wXxp+Gp3k4ivscXSK3m2SRz+CQcURh9O
rpm58sDd4hFX2PcGSipZmeN47YVtRi8LRB+edEmPbgg9HcTaV0wFcwKBgQDA7B++
r3PL6GmCagtOpEve8UNWxPOCHLP4V4ZWi89rNSt3eFyUgdXQM+bDicZclScOyPGg
aSL3ihuGBwqq81yWcw61jWV3+jjPw8dzLlxfPTXG98Px+mlApfTkmVG9bd5/WL0V
VvFEXb0a2ELo0GK5sh56qgU3/yonQiqYp1nEuQKBgGqdMPLU/8y8wKRorqFdivLY
IV1tsByMc9KNDjLt2yK2NtYkiTcQMyt9q1bXL2Lv8XMFr7qL+AAat3I16aO72m2W
tQNMjoajpWGr4hRQ34JRfJ/nYrn4LVuqQG5CVI9eUg/r7MFn+IXaHTbgjcMraKa1
Uhb5+fH+Et2aR/tC1qO3AoGACwYtsx/3/QX3Zom3gNYwOKASefygM1IY6Z6Z9lEh
y5yjZeme4+opeZyQ1/k+iPKUhICC1fdhFXWbLDrqqq7SFznGU4RsMr3XXFRUij0p
2Y54Gf+HiiuQ/GFBMaJccTl6ba1MoiI5rCKdF3oSc7Qi5gotoJwATFe5RBJo1YXn
31sCgYBoxIDGcXJeA0FwiJvOm1DrFP9ZwqMyBYCOgdhLgbNoJegGbjzD7s+RaEmx
SLGcpD+5yyDOdiN0L84dVRimR+VK2hTMv379ajnXbYzVel3pVpQxA16JbS/dcoz4
fIfvxMoc06E3U1JnKbPAPBN8HWNDnR7Xtpp/fXSW2c7vJLqZHA==
-----END RSA PRIVATE KEY-----
")

;; The known-hosts, i.e. source.nokia.com
(def known-hosts
  "|1|UoVqPabY168wScQJfyEUyDX35Xk=|DTUa0H6lR05jNuvHIMl4ReJLqXM= ssh-rsa AAAAB3NzaC1yc2EAAAABIwAAAQEAuK96oIAr4mPDxbiJqlSi7KFM9GY1jnzb+LhZlJyvJRqK925hgEdTS/QG4uoH4VI0NqMWiCLn8LiPLyj2+WLnYBWpaPIsp728ighAahYY1TsZiUiP4EqpRd093Ur+EE+de7cjfuNy5iJfkU092SqLUJwQCMA05N9vvkSc0lR/hOR77bs/YLucaGyZfXGfHFbosd4+sm82hcqLJKIdQ0+ChEp3ROyZnzferlKqJbFFjJdN4TTq3ITPNjmQ1Hqmmb0kjBJ6M8W11SgqANjdzfnkXHhV46rYrjXesxoPxw3jS1BPEjbLljrY1NMBMhFOLI6tlvFTJc5Jk7c7ytmtG5+sCQ==
|1|xtbIYF+FIx2dSIOML++8N0Ohwuw=|f11MX7uxFmdYTaPNxh961FunJI0= ssh-rsa AAAAB3NzaC1yc2EAAAABIwAAAQEAuK96oIAr4mPDxbiJqlSi7KFM9GY1jnzb+LhZlJyvJRqK925hgEdTS/QG4uoH4VI0NqMWiCLn8LiPLyj2+WLnYBWpaPIsp728ighAahYY1TsZiUiP4EqpRd093Ur+EE+de7cjfuNy5iJfkU092SqLUJwQCMA05N9vvkSc0lR/hOR77bs/YLucaGyZfXGfHFbosd4+sm82hcqLJKIdQ0+ChEp3ROyZnzferlKqJbFFjJdN4TTq3ITPNjmQ1Hqmmb0kjBJ6M8W11SgqANjdzfnkXHhV46rYrjXesxoPxw3jS1BPEjbLljrY1NMBMhFOLI6tlvFTJc5Jk7c7ytmtG5+sCQ==
")

(def ^:const base-git-url (env :service-base-git-repository-url))
(def ^:const base-git-path (env :service-base-git-repository-path))
(def ^:const base-git-branch (env :service-base-git-repository-branch))


(def my-jcs-factory
  (proxy [JschConfigSessionFactory] []
    (configure [host session]
      (info "Configuring JschConfigSessionFactory.")
      (.setConfig session "StrictHostChecking" "yes"))
    (createDefaultJSch [fs]
      (let [jsch (JSch.)]
        (info "Creating default JSch using tyranitar private key and known-hosts.")
        (.addIdentity jsch "tyranitar" (.getBytes shuppet-private-key) nil nil)
        (.setKnownHosts jsch (ByteArrayInputStream. (.getBytes known-hosts)))
        jsch))))

(SshSessionFactory/setInstance my-jcs-factory)

(conch/programs rm)

(defn- repo-url
  [repo-name]
  (str base-git-url repo-name))

(defn- repo-path
  [name]
  (str base-git-path name))

(defn- repo-branch
  [name]
  (if (or (= name "dev") (= name "prod"))
    "master"
    base-git-branch))

(defn clone-repo
  "Clones the latest version of the specified repo from GIT."
  [repo-name]
  (info "First ensuring that repository directory does not exist")
  (rm "-rf" (repo-path repo-name))
  (info "Cloning repository to" (repo-path repo-name))
  (->
   (Git/cloneRepository)
   (.setURI (repo-url repo-name))
   (.setDirectory (as-file (repo-path repo-name)))
   (.setRemote "origin")
   (.setBranch (repo-branch repo-name))
   (.setBare false)
   (.call))
  (info "Cloning completed."))

(defn- pull-repo
  "Pull a repository by fetching."
  [repo-name]
  (let [git (Git/open (as-file (repo-path repo-name)))]
    (info "Fetching repository to" (repo-path repo-name))
    (->
     (.fetch git)
     (.call))
    (info "Fetch completed.")))

(defn- repo-exists?
  [repo-name]
  (.exists (as-file (repo-path repo-name))))

(defn- ensure-repo-up-to-date
  "Gets or updates the specified repo from GIT"
  [repo-name]
  (if (repo-exists? repo-name)
    (pull-repo repo-name)
    (do
      (info (str "Repo '" repo-name "' not found - attempting to clone"))
      (clone-repo repo-name))))

(defn- get-head
  "Get the contents of the application config file for head revision"
  [application]
  (info "Attempting to get head for the application " application)
  (let [file-name (str application  ".clj")
        git (Git/open (as-file (repo-path application)))
        repo (.getRepository git)
        commit-id (.resolve repo "HEAD")
        rwalk (RevWalk. repo)
        commit (.parseCommit rwalk commit-id)
        tree (.getTree commit)
        twalk (TreeWalk/forPath repo file-name tree)
        loader (.open repo (.getObjectId twalk 0))]
    (slurp (.openStream loader))))

(defn get-data
   "Fetches the data corresponding to the given application from GIT"
   [application]
   (try
      (ensure-repo-up-to-date application)
      (get-head application)
      (catch InvalidRemoteException e
        (info (str "Can't communicate with remote repo '" application  "': " e))
        nil)
      (catch NullPointerException e
        (info (str "HEAD revision not found in remote repo '" application "': " e))
        nil)
      (catch MissingObjectException e
        (info (str "Missing object for revision HEAD in repo '" application "': " e))
        nil)))


;(prn (get-data "dev"))
