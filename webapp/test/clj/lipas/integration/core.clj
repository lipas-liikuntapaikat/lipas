(ns lipas.integration.core
  (:require [clojure.test :refer [deftest use-fixtures]]
            [etaoin.api :as e]
            [taoensso.timbre :as timbre]))

(timbre/set-level! :info)

(def ^:dynamic *driver*)

(defn remote-driver []
  (e/chrome-headless ))

(def driver-opts
  {:host      "headless-chrome"
   :port      4444
   :log-level :info})

(defn fixture-driver
  "Executes a test running a driver. Bounds a driver
   with the global *driver* variable."
  [f]
  (e/with-chrome-headless driver-opts driver
    (binding [*driver* driver]
      (f))))

(use-fixtures
  :each ;; start and stop driver for each test
  fixture-driver)

(deftest ^:integration login-test
  (e/doto-wait 1 *driver*
    (e/go "http://proxy")
    (e/click  {:id "account-btn"})
    (e/click  {:id "account-menu-item-login"})
    (e/fill   {:id "login-username-input"} "jhdemo")
    (e/fill   {:id "login-password-input"} "jaahalli")
    (e/click  {:id "login-submit-btn"})))
