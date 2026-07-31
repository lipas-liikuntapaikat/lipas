(ns lipas.ui.login.session-expiry-test
  (:require [clojure.test :refer [deftest is testing]]
            [lipas.ui.login.session-expiry :as sut]))

(def ^:private ignored
  ;; Mirrors lipas.ui.login.events/handles-own-401. The real set is passed in
  ;; from there, so this is just a stand-in for the tests.
  #{:lipas.ui.login.events/login-failure
    :lipas.ui.login.events/login-refresh-failure})

(defn- failure
  "The shape cljs-ajax hands to an :on-failure handler."
  [status]
  {:status status
   :status-text "Unauthorized"
   :failure :error
   :response {:error "Not authorized"}})

(deftest ajax-failure-401?-test
  (testing "a cljs-ajax 401 failure"
    (is (true? (sut/ajax-failure-401? (failure 401)))))

  (testing "other statuses are not authentication failures"
    (doseq [status [200 400 403 404 500 0]]
      (is (false? (sut/ajax-failure-401? (failure status)))
          (str "status " status))))

  (testing "a domain map that merely carries :status 401 is not a failure map"
    ;; :failure is what distinguishes an ajax response from arbitrary data, so
    ;; a sports-site-ish map with a :status field can't trip the session logic.
    (is (false? (sut/ajax-failure-401? {:status 401 :name "Uimahalli"}))))

  (testing "non-maps"
    (doseq [x [nil "401" 401 [:status 401]]]
      (is (false? (sut/ajax-failure-401? x)) (pr-str x)))))

(deftest expired?-test
  (let [f (failure 401)]
    (testing "a 401 on an ordinary event, while logged in"
      (is (true? (sut/expired? true ignored [:some.ns/save-failure f]))))

    (testing "the response is found wherever it sits in the event vector"
      ;; :on-failure vectors carry their own arguments, so the response is not
      ;; always last.
      (is (true? (sut/expired? true ignored [:some.ns/failure 123 f])))
      (is (true? (sut/expired? true ignored [:some.ns/failure f :extra]))))

    (testing "not logged in: nothing to expire"
      ;; This is what keeps the login form's own 401 from bouncing the user.
      (is (false? (sut/expired? false ignored [:some.ns/save-failure f]))))

    (testing "events that own their 401 opt out"
      (doseq [event-id ignored]
        (is (false? (sut/expired? true ignored [event-id f])) (str event-id))))

    (testing "403 is an authorization failure, not an expired session"
      (is (false? (sut/expired? true ignored
                                [:some.ns/save-failure (failure 403)]))))

    (testing "an event carrying no response at all"
      (is (false? (sut/expired? true ignored [:some.ns/plain-event])))
      (is (false? (sut/expired? true ignored [:some.ns/plain-event {:foo 1}]))))

    (testing "an empty ignore set still works"
      (is (true? (sut/expired? true #{} [:some.ns/save-failure f]))))))
