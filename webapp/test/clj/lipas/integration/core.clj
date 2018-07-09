(ns lipas.integration.core
  (:require [clojure.test :refer [deftest use-fixtures]]
            [etaoin.api :as e]
            [taoensso.timbre :as timbre]))

(timbre/set-level! :info)

(def ^:dynamic *driver*)

(defn remote-driver []
  (e/create-driver :chrome-headless
                   {:host      "headless-chrome"
                    :port      4444
                    :log-level :info}))

(defn fixture-driver
  "Executes a test running a driver. Bounds a driver
   with the global *driver* variable."
  [f]
  (let [driver (remote-driver)]
    (binding [*driver* driver]
      (e/connect-driver driver)
      (f)
      (e/disconnect-driver driver))))

(use-fixtures
  :each ;; start and stop driver for each test
  fixture-driver)

(deftest ^:integration login-test
  (doto *driver*
    (e/go "http://proxy")
    (e/wait-visible {:id "account-btn"})
    (e/click  {:id "account-btn"})
    (e/click  {:id "account-menu-item-login"})
    (e/fill   {:id "login-username-input"} "jhdemo")
    (e/fill   {:id "login-password-input"} "jaahalli")
    (e/click  {:id "login-submit-btn"})))
