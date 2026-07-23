(ns lipas.backend.assistant-test
  "The UI-action layer of the assistant: the closed action vocabulary
   (lipas.schema.assistant) and the tool handlers that resolve and
   validate model output into client actions. The Gemini loop itself is
   not tested here — these are the deterministic parts around it."
  (:require [clojure.test :refer [deftest is testing]]
            [lipas.backend.assistant :as assistant]
            [lipas.schema.assistant :as assistant-schema]
            [malli.core :as m]))

(def run-tool* #'assistant/run-tool*)
(def describe-roles #'assistant/describe-roles)
(def sanitize-answer-links #'assistant/sanitize-answer-links)

(deftest context-schema-test
  (testing "widget snapshot with an open PTV dialog validates"
    (is (m/validate assistant/context-schema
                    {:locale "fi"
                     :route ":lipas.ui.routes.map/map"
                     :view "PTV export dialog 'Vie Palvelutietovarantoon' (covers the map view)"
                     :ptv {:open? true
                           :org "Utajärven kunta"
                           :tab "Käyttöönotto (ohjattu vienti PTV:hen)"
                           :wizard-step "2. Luo PTV-palvelut"}})))
  (testing "junk keys are refused — the map is closed at every level"
    (is (not (m/validate assistant/context-schema
                         {:ptv {:open? true :password "hunter2"}})))
    (is (not (m/validate assistant/context-schema
                         {:evil "payload"}))))
  (testing "live map-editor state validates"
    (is (m/validate assistant/context-schema
                    {:locale "fi"
                     :edit-mode? true
                     :edit {:new-site? true
                            :sub-mode "editing"
                            :geometry-type "LineString"
                            :segments 2
                            :vertices 340
                            :length-km 5.4
                            :self-intersections 2
                            :problem-locations [{:lon 25.701 :lat 62.241}]
                            :invalid-fields ["location.address" "name"]}}))
    (is (not (m/validate assistant/context-schema
                         {:edit {:geoms {:type "FeatureCollection"}}}))
        "raw geometries don't belong in the snapshot")))

(deftest describe-roles-test
  (testing "roles get official names and code→name resolved scopes"
    (is (= [{:role "Kuntakäyttäjä (city-manager)"
             :cities ["320 Kemijärvi"]}
            {:role "Paikkakäyttäjä (site-manager)"
             :sites [75100]}]
           (describe-roles {:permissions {:roles [{:role "city-manager" :city-code #{320}}
                                                  {:role :site-manager :lipas-id #{75100}}]}})))))

(deftest sanitize-answer-links-test
  (let [sources [{:deep-link "?ohje=tallentajille/kohteen-kopiointi"}]]
    (testing "retrieved ?ohje= links survive"
      (is (= "Katso [ohje](?ohje=tallentajille/kohteen-kopiointi)."
             (sanitize-answer-links
              "Katso [ohje](?ohje=tallentajille/kohteen-kopiointi)." sources))))
    (testing "invented ?ohje= links degrade to plain text"
      (is (= "Katso Käyttöoikeudet."
             (sanitize-answer-links
              "Katso [Käyttöoikeudet](?ohje=peruskasitteet/kayttooikeudet)." sources))))
    (testing "fabricated tool-call schemes degrade to plain text"
      (is (= "Avaa profiili"
             (sanitize-answer-links "[Avaa profiili](navigate_to_view:profile)" sources))))
    (testing "normal web and mail links survive"
      (is (= "[video](https://youtu.be/x?t=10) ja [posti](mailto:a@b.fi)"
             (sanitize-answer-links
              "[video](https://youtu.be/x?t=10) ja [posti](mailto:a@b.fi)" sources))))))

(deftest action-schema-test
  (testing "valid actions"
    (is (assistant-schema/valid? {:type "apply-search"
                                  :label "Hae: uimahallit Äänekoski"
                                  :city-codes [992]
                                  :type-codes [3110]}))
    (is (assistant-schema/valid? {:type "apply-search"
                                  :label "Omat kohteet"
                                  :only-editable true}))
    (is (assistant-schema/valid? {:type "show-site"
                                  :label "Näytä kartalla"
                                  :lipas-id 75100}))
    (is (assistant-schema/valid? {:type "pan-to-location"
                                  :label "Siirry: Nallikari"
                                  :location "Nallikari, Oulu"}))
    (is (assistant-schema/valid? {:type "navigate-to-view"
                                  :label "Avaa tilastot"
                                  :view "stats-finance"}))
    (is (assistant-schema/valid? {:type "pan-to-coordinates"
                                  :label "Näytä ongelmakohta 1"
                                  :lon 25.701
                                  :lat 62.241
                                  :zoom 16})))

  (testing "pan-to-coordinates is bounded to ~Finland"
    (is (not (assistant-schema/valid? {:type "pan-to-coordinates" :label "x"
                                       :lon 5.0 :lat 40.0})))
    (is (not (assistant-schema/valid? {:type "pan-to-coordinates" :label "x"
                                       :lon 25.0 :lat 62.0 :zoom 99}))))

  (testing "apply-search requires at least one filter"
    (is (not (assistant-schema/valid? {:type "apply-search" :label "Hae"}))))

  (testing "codes come from the canonical enums"
    (is (not (assistant-schema/valid? {:type "apply-search" :label "Hae"
                                       :city-codes [99999]})))
    (is (not (assistant-schema/valid? {:type "show-site" :label "Näytä"
                                       :lipas-id 0}))))

  (testing "views are a closed set"
    (is (not (assistant-schema/valid? {:type "navigate-to-view" :label "Avaa"
                                       :view "admin"}))))

  (testing "maps are closed — junk keys don't pass through to the widget"
    (is (not (assistant-schema/valid? {:type "navigate-to-view" :label "Avaa"
                                       :view "stats" :extra "smuggled"}))))

  (testing "every view in the registry maps to a valid action"
    (doseq [view (keys assistant-schema/views)]
      (is (assistant-schema/valid? {:type "navigate-to-view" :label "x" :view view})
          (str "view " view)))))

(deftest apply-search-tool-test
  (testing "city names resolve to city codes server-side"
    (let [result (run-tool* {} "apply_search" {:label "Hae: Äänekoski"
                                               :city-names ["Äänekoski"]})]
      (is (= {:type "apply-search" :label "Hae: Äänekoski" :city-codes [992]}
             (:client-action result)))))

  (testing "unknown municipality is an error the model can act on, not an action"
    (let [result (run-tool* {} "apply_search" {:label "Hae"
                                               :city-names ["Ääneskoski"]})]
      (is (nil? (:client-action result)))
      (is (re-find #"Ääneskoski" (:error result)))))

  (testing "unknown type codes are rejected with a hint"
    (let [result (run-tool* {} "apply_search" {:label "Hae" :type-codes [9999]})]
      (is (nil? (:client-action result)))
      (is (re-find #"9999" (:error result)))))

  (testing "only-editable produces a filterless editable search"
    (is (= {:type "apply-search" :label "Omat kohteet" :only-editable true}
           (:client-action (run-tool* {} "apply_search" {:label "Omat kohteet"
                                                         :only-editable true}))))))

(deftest navigation-tool-test
  (testing "known view"
    (is (= {:type "navigate-to-view" :label "Avaa tilastot" :view "stats"}
           (:client-action (run-tool* {} "navigate_to_view" {:label "Avaa tilastot"
                                                             :view "stats"})))))
  (testing "unknown view is refused by the schema"
    (is (some? (:error (run-tool* {} "navigate_to_view" {:label "Avaa"
                                                         :view "adminland"}))))))

(deftest empty-model-response-test
  ;; Gemini sometimes treats a proposed button as the whole answer and
  ;; returns no text in its final turn. Stubbing the one nondeterministic
  ;; external call is the only way to pin this deterministically.
  (testing "actions + empty final text → button-referencing text, not an apology"
    (let [calls (atom 0)
          fake (fn [_ _ _]
                 (if (= 1 (swap! calls inc))
                   {:candidates [{:content {:parts [{:functionCall
                                                     {:name "apply_search"
                                                      :args {:label "Hae Äänekosken liikuntapaikat"
                                                             :city-names ["Äänekoski"]}}}]}}]}
                   {:candidates [{:content {:parts []}}]}))
          result (with-redefs-fn {(var assistant/gemini-chat) fake}
                   #(assistant/answer! {:db nil :search nil :user {}
                                        :message "test" :history [] :context {}}))]
      (is (= "Voit jatkaa painamalla alla olevaa painiketta." (:answer-md result)))
      (is (= [{:type "apply-search" :label "Hae Äänekosken liikuntapaikat" :city-codes [992]}]
             (:actions result)))))

  (testing "nothing at all → the apology"
    (let [fake (fn [_ _ _] {:candidates [{:content {:parts []}}]})
          result (with-redefs-fn {(var assistant/gemini-chat) fake}
                   #(assistant/answer! {:db nil :search nil :user {}
                                        :message "test" :history [] :context {}}))]
      (is (re-find #"Pahoittelut" (:answer-md result)))
      (is (empty? (:actions result))))))

(deftest pan-tool-test
  (is (= {:type "pan-to-location" :label "Siirry" :location "Äänekoski"}
         (:client-action (run-tool* {} "pan_map_to_location" {:label "Siirry"
                                                              :location "Äänekoski"}))))
  (testing "too-short location is refused"
    (is (some? (:error (run-tool* {} "pan_map_to_location" {:label "Siirry"
                                                            :location "x"}))))))

(deftest zoom-to-coordinates-tool-test
  (is (= {:type "pan-to-coordinates" :label "Näytä ongelmakohta 1"
          :lon 25.701 :lat 62.241 :zoom 16}
         (:client-action (run-tool* {} "zoom_map_to_coordinates"
                                    {:label "Näytä ongelmakohta 1"
                                     :lon 25.701 :lat 62.241 :zoom 16}))))
  (testing "zoom is optional"
    (is (some? (:client-action (run-tool* {} "zoom_map_to_coordinates"
                                          {:label "Näytä" :lon 25.0 :lat 62.0})))))
  (testing "coordinates outside Finland are refused — the model cannot pan the user into the void"
    (is (some? (:error (run-tool* {} "zoom_map_to_coordinates"
                                  {:label "x" :lon 5.0 :lat 40.0}))))))
