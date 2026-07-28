(ns lipas.schema.assistant
  "Closed vocabulary of UI actions the AI assistant may propose.

   The model never produces re-frame event vectors: it calls an action
   tool, the backend resolves and validates the args against these
   schemas, and the widget renders the surviving actions as buttons the
   user clicks. The frontend translates each action type to concrete
   dispatches (lipas.ui.assistant.events/run-action) — an action type
   missing from that translator is simply inert."
  (:require [lipas.schema.sports-sites :as sports-sites-schema]
            [lipas.schema.sports-sites.location :as location-schema]
            [lipas.schema.sports-sites.types :as types-schema]
            [malli.core :as m]))

(def views
  "User-facing views the assistant can navigate to. Descriptions feed
   the navigate_to_view tool declaration; the frontend maps ids to
   reitit route names."
  {"front-page" "Etusivu — service news and general info"
   "map" "Liikuntapaikat — map view where sports sites are searched, viewed and edited"
   "stats" "Tilastot — statistics section front page"
   "stats-sport" "Tilastot: liikuntapaikat — sports facility statistics per municipality"
   "stats-age-structure" "Tilastot: rakennusvuodet — facility construction year / age structure"
   "stats-city" "Tilastot: kuntien vertailu — compare municipalities"
   "stats-finance" "Tilastot: talous — municipal sports finance figures"
   "stats-subsidies" "Tilastot: avustukset — state subsidies for sports facilities"
   "profile" "Omat tiedot — user's own profile and permissions"})

(def label
  "Button text shown to the user, in the user's language."
  (m/schema [:string {:min 1 :max 60}]))

(def action
  (m/schema
    [:multi {:dispatch :type}
     ["apply-search"
      [:and
       [:map {:closed true}
        [:type [:= "apply-search"]]
        [:label label]
        [:search-text {:optional true} [:string {:min 1 :max 200}]]
        [:city-codes {:optional true} [:vector #'location-schema/city-code]]
        [:type-codes {:optional true} [:vector #'types-schema/active-type-code]]
        [:only-editable {:optional true} :boolean]]
       [:fn {:error/message "apply-search needs at least one of search-text, city-codes, type-codes, only-editable"}
        (fn [{:keys [search-text city-codes type-codes only-editable]}]
          (boolean (or search-text (seq city-codes) (seq type-codes) only-editable)))]]]

     ["show-site"
      [:map {:closed true}
       [:type [:= "show-site"]]
       [:label label]
       [:lipas-id #'sports-sites-schema/lipas-id]]]

     ["pan-to-location"
      [:map {:closed true}
       [:type [:= "pan-to-location"]]
       [:label label]
       [:location [:string {:min 2 :max 100}]]]]

    ;; Exact-coordinate variant for tool-derived points (e.g. geometry
    ;; problem locations). Bounds ~cover Finland so the model can't pan
    ;; the user into the void; safe to run during editing.
     ["pan-to-coordinates"
      [:map {:closed true}
       [:type [:= "pan-to-coordinates"]]
       [:label label]
       [:lon [:double {:min 19.0 :max 32.0}]]
       [:lat [:double {:min 59.0 :max 70.5}]]
       [:zoom {:optional true} [:int {:min 5 :max 18}]]]]

     ["navigate-to-view"
      [:map {:closed true}
       [:type [:= "navigate-to-view"]]
       [:label label]
       [:view (into [:enum] (sort (keys views)))]]]]))

(defn valid? [a] (m/validate action a))

(defn explain [a] (m/explain action a))
