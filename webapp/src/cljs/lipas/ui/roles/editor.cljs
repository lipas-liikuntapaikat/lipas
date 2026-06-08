(ns lipas.ui.roles.editor
  "Shared, state-agnostic role-spec editor: pick a role + fill its context keys.

  This is the one primitive the admin user-management screen (assign a role
  instance to a user) and the org role-template catalog (define a reusable
  capability for an org) genuinely share. It dispatches nothing itself — callers
  drive it with `:spec` + the `:on-role-change`/`:on-context-change` callbacks and
  supply the role vocabulary via `:roles`. The org case passes
  `:hide-context-keys #{:org-id}` because org-scoped roles get their org-id
  injected at projection time, never typed by the admin."
  (:require ["@mui/material/Autocomplete" :refer [createFilterOptions]]
            ["@mui/material/Stack$default" :as Stack]
            ["@mui/material/Typography$default" :as Typography]
            [clojure.string :as str]
            [lipas.roles :as roles]
            [lipas.ui.components.autocompletes :as ac]
            [re-frame.core :as rf]
            [reagent.core :as r]))

(defn privilege-description
  "Human, translated description for a privilege key. Privilege keys are
  namespaced (`:site/create-edit`); the i18n catalog stores them with `/`→`.`
  (tongue drops a namespaced leaf's namespace). Falls back to the raw key so a
  new privilege never renders blank."
  [tr privilege]
  (let [munged (str/replace (subs (str privilege) 1) "/" ".")
        s (tr (keyword "lipas.user.permissions.roles.privilege-descriptions" munged))]
    (if (str/starts-with? s "{Missing") (subs (str privilege) 1) s)))

;; ---------------------------------------------------------------------------
;; Option-list subs — pure derivations over the canonical sports-sites data
;; (relocated here so the editor is not coupled to admin state).
;; ---------------------------------------------------------------------------

(defn ->list-entry
  [locale [k v]]
  {:value k
   :label (str (get-in v [:name locale])
               " "
               k
               (when (not= "active" (:status v))
                 " POISTUNUT"))})

(rf/reg-sub ::types-list
  :<- [:lipas.ui.sports-sites.subs/all-types]
  (fn [types [_ locale]]
    (->> types
         (map (partial ->list-entry locale))
         (sort-by :label))))

(rf/reg-sub ::cities-list
  :<- [:lipas.ui.sports-sites.subs/cities-by-city-code]
  (fn [cities [_ locale]]
    (->> cities
         (map (partial ->list-entry locale))
         (sort-by :label))))

(rf/reg-sub ::sites-list
  :<- [:lipas.ui.sports-sites.subs/latest-sports-site-revs]
  (fn [sites _]
    (->> sites
         (map (fn [[lipas-id s]] {:value lipas-id :label (:name s)}))
         (sort-by :label))))

(rf/reg-sub ::activities-list
  :<- [:lipas.ui.sports-sites.activities.subs/data]
  (fn [activities [_ locale]]
    (->> activities
         (map (fn [[k m]] {:value k :label (get-in m [:label locale])}))
         (sort-by :label))))

;; ---------------------------------------------------------------------------
;; Presentational context-key selectors: {:value :on-change :required :tr}
;; `value` is the current coll of selected codes; `on-change` gets a vector.
;; ---------------------------------------------------------------------------

(def filter-ac (createFilterOptions))

(r/defc site-select [{:keys [tr required value on-change]}]
  (let [sites @(rf/subscribe [::sites-list])]
    [ac/autocomplete2
     {:options sites
      :label (str (tr :lipas.user.permissions.roles.context-keys/lipas-id)
                  (when required " *"))
      :value (to-array (or value []))
      :onChange (fn [_e v] (on-change (mapv ac/safe-value v)))
      :multiple true
      :selectOnFocus true
      :clearOnBlur true
      :handleHomeEndKeys true
      :freeSolo true
      :filterOptions (fn [options params]
                       ;; Allow inputting paikka-id numbers directly (the options
                       ;; only hold the first N sites), show "Add x" otherwise.
                       (let [filtered (filter-ac options params)
                             input-value (js/parseInt (.-inputValue params))
                             input-value (when (pos? input-value) input-value)
                             is-existing (.some options (fn [x] (= input-value (:value x))))]
                         (when (and input-value (not is-existing))
                           (.push filtered {:value input-value
                                            :label (str "Paikka-id \"" input-value "\"")}))
                         filtered))}]))

(r/defc type-code-select [{:keys [tr required value on-change]}]
  (let [types @(rf/subscribe [::types-list (tr)])]
    [ac/autocomplete2
     {:options types
      :label (str (tr :lipas.user.permissions/types) (when required " *"))
      :value (to-array (or value []))
      :onChange (fn [_e v] (on-change (mapv ac/safe-value v)))
      :multiple true}]))

(r/defc city-code-select [{:keys [tr required value on-change]}]
  (let [cities @(rf/subscribe [::cities-list (tr)])]
    [ac/autocomplete2
     {:options cities
      :label (str (tr :lipas.user.permissions/cities) (when required " *"))
      :value (to-array (or value []))
      :onChange (fn [_e v] (on-change (mapv ac/safe-value v)))
      :multiple true}]))

(r/defc activity-select [{:keys [tr required value on-change]}]
  (let [activities @(rf/subscribe [::activities-list (tr)])]
    [ac/autocomplete2
     {:options activities
      :label (str (tr :lipas.user.permissions/activities) (when required " *"))
      :value (to-array (or value []))
      :onChange (fn [_e v] (on-change (mapv ac/safe-value v)))
      :multiple true}]))

;; NOTE: no :org-id context editor here on purpose. Org-scoped roles
;; (org-admin/org-user/org-editor) are never hand-assigned onto an account — they
;; come only from org membership (projected at login). The catalog editor that
;; builds org-editor templates passes `:hide-context-keys #{:org-id}` (the org-id
;; is injected at projection), so no caller ever needs an org-id selector.
(r/defc context-key-edit [{:keys [k] :as props}]
  (case k
    :lipas-id  [site-select props]
    :type-code [type-code-select props]
    :city-code [city-code-select props]
    :activity  [activity-select props]
    nil))

;; ---------------------------------------------------------------------------
;; The primitive: role select + dynamic context-key inputs.
;; ---------------------------------------------------------------------------

(r/defc role-spec-editor
  [{:keys [spec roles hide-context-keys role-read-only? on-role-change on-context-change tr]}]
  (let [hide    (or hide-context-keys #{})
        role-kw (when-let [r (:role spec)] (keyword r))
        rdef    (get roles/roles role-kw)
        req     (remove hide (:required-context-keys rdef))
        opt     (remove hide (:optional-context-keys rdef))]
    [:> Stack {:direction "column" :sx #js {:gap 1}}
     [ac/autocomplete2
      {:options (for [r roles]
                  {:value r
                   :label (tr (keyword :lipas.user.permissions.roles.role-names r))})
       :readOnly (boolean role-read-only?)
       :label (tr :lipas.user.permissions.roles/role)
       :value role-kw
       :onChange (fn [_e v] (on-role-change (ac/safe-value v)))}]

     (when-not role-kw
       [:> Typography (tr :lipas.user.permissions.roles.edit-role/choose-role)])

     ;; Make the role's grants explicit so the admin doesn't have to remember
     ;; what each role confers (data-driven from the role registry).
     (when-let [privs (and role-kw (seq (sort (:privileges rdef))))]
       [:> Stack {:direction "column" :sx #js {:gap 0}}
        [:> Typography {:variant "caption" :color "text.secondary"}
         (tr :lipas.user.permissions.roles/privileges-label)]
        (into [:ul {:style #js {:margin "2px 0 4px 0" :paddingLeft "18px"}}]
              (for [p privs]
                [:li {:key (str p)}
                 [:> Typography {:variant "caption" :color "text.secondary"}
                  (privilege-description tr p)]]))])

     (for [k req]
       [context-key-edit
        {:key k :k k :required true :tr tr
         :value (get spec k)
         :on-change (fn [vals] (on-context-change k vals))}])

     (for [k opt]
       [context-key-edit
        {:key k :k k :tr tr
         :value (get spec k)
         :on-change (fn [vals] (on-context-change k vals))}])]))
