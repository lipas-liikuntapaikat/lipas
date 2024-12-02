(ns lipas.roles
  (:require [clojure.set :as set]))

;; :doc here is just a developer comment, translations are elsewhere (if we even need
;; translations for privileges)
;; Use namespaced keys for easier searching in the codebase
(def privileges
  {;; Add this to all roles that have ANY edit permission
   :site/save-api {:doc "Oikeus kutsua save-sports-site rajapintaa, jota käytetään kaikkiin muutoksiin"}

   :site/create-edit {:doc "Oikeus lisätä ja muokata liikuntapaikkoja"}
   ;; TODO: Not yet checked anywhere
   :site/view {:doc "Oikeus nähdä liikuntapaikat ja niihin liittyvät perustiedot ja lisätiedot"}
   :site/edit-any-status {:doc "Oikeus muokata liikuntapaikkoja jotka esim. poistettu pysyvästi käytöstä"}

   :activity/view {:doc "Nähdä UTP tiedot"}
   :activity/edit {:doc "Oikeus muokata UTP tietoja"}

   :loi/create-edit {:doc "Oikeus luoda ja muokata Muita kohteita"}
   :loi/view {:doc "Oikeus nähdä Muita kohteita"}

   :floorball/view {:doc "Oikeus nähdä Salibandy perus tiedot"}
   :floorball/view-extended {:doc "Oikeus nähdä Salibandy erityiset lisätiedot"}
   :floorball/edit {:doc "Oikeus muokata Salibandy lisätietoja (olosuhteet välilehti muokattavissa)"}

   :analysis-tool/use {:doc "Oikeus käyttää analyysityökaluja

                            Antaa myös oikeuden luoda paikkoja vedos-tilassa"}

   :users/manage {:doc "Käyttäjien hallinta (admin)"}

   :ptv/manage {:doc "Oikeus nähdä PTV dialogi ja PTV välilehti paikoilla"}})

(def basic #{:site/create-edit
             :site/save-api
             :activity/view
             :analysis-tool/use})

(def roles
  {:admin
   ;; all privileges
   {:sort 0
    :assignable true
    :privileges (set (keys privileges))
    ;; This is kind of duplicated from specs, not sure if needed or
    ;; if the UI can introspect the spec.
    ;; These are also used to get the ORDER of UI fields for edit.
    :required-context-keys []
    :optional-context-keys []}

   ;; Unsigned users, basis for everyone
   ;; Can't be assigned to users on the user-management
   :default
   {:assignable false
    :privileges #{:site/view
                  ;; New feature currently hidden,
                  ;; will later be enabled for everyone.
                  ;; :loi/view
                  :floorball/view}
    :required-context-keys []
    :optional-context-keys []}

   ;; Basic privileges
   :type-manager
   {:sort 10
    :assignable true
    :privileges basic
    :required-context-keys [:type-code]
    :optional-context-keys [:city-code]}

   :city-manager
   {:sort 11
    :assignable true
    :privileges basic
    :required-context-keys [:city-code]
    :optional-context-keys [:type-code]}

   :site-manager
   {:sort 12
    :assignable true
    :privileges basic
    :required-context-keys [:lipas-id]
    :optional-context-keys []}

   :activities-manager
   {:sort 20
    :assignable true
    :privileges #{:activity/view
                  :activity/edit
                  :site/save-api

                  :loi/view ;; Temporarily only enabled here
                  :loi/create-edit}
    :required-context-keys [:activity]
    :optional-context-keys [:city-code :type-code]}

   :floorball-manager
   {:sort 30
    :assignable true
    :privileges #{:floorball/view :floorball/view-extended :floorball/edit :site/save-api}
    :required-context-keys []
    :optional-context-keys [:type-code]}

   :analysis-user
   {:sort 40
    :assignable true
    :privileges #{:analysis-tool/use}
    :required-context-keys []
    :optional-context-keys []}

   :ptv-user
   {:sort 50
    :assignable true
    :privileges #{:ptv/manage}
    :required-context-keys []
    :optional-context-keys []}})

(defn role-sort-fn
  [{:keys [role]}]
  (:sort (get roles role)))

(defn get-privileges
  "Get set of privileges for given list of roles"
  [active-roles]
  (set (mapcat (fn [role-k]
                 (:privileges (get roles role-k)))
               active-roles)))

(defn select-role
  "Check if the role is active with given role-context.

  When a role is missing (or has a nil value) for one of
  the conditions, it is always active.
  ::any value can be used to check if there are any
  role-context where the privilege is given,
  e.g. to check for :create privilege before
  city-code or type-code is selected (available
  values for those are then filtered using privileges
  in the create form.)"
  [role-context role]
  (when (and (or (nil? (:city-code role))
                 (= ::any (:city-code role-context))
                 (contains? (:city-code role) (:city-code role-context)))
             (or (nil? (:type-code role))
                 (= ::any (:type-code role-context))
                 (contains? (:type-code role) (:type-code role-context)))
             ;; Check if user has permission to touch any of the activity types
             ;; the site has (usually just one, but could be many types in theory)
             (or (nil? (:activity role))
                 (= ::any (:activity role-context))
                 (seq (set/intersection (:activity role) (:activity role-context))))
             (or (nil? (:lipas-id role))
                 (= ::any (:lipas-id role-context))
                 (contains? (:lipas-id role) (:lipas-id role-context))))
    (:role role)))

(defn check-privilege
  "Check if given user has the asked privilege
  which is active for defined context.

  Context:
  - :city-code
  - :type-code
  - :lipas-id
  - :activity

  In FE, you should always (if possible) use `::user-data` sub to get the user,
  because that sub applies the dev/overrides from the dev tool."
  [user {:as role-context} required-privilege]
  (let [active-roles (->> (:permissions user)
                          (:roles)
                          (keep (fn [role] (select-role role-context role)))
                          (into #{:default}))
        privileges (get-privileges active-roles)
        overrides (:dev/overrides user)]
    (if (contains? overrides required-privilege)
      (get overrides required-privilege)
      (contains? privileges required-privilege))))

(defn site-roles-context
  "Create role-context for given site"
  [site]
  {:lipas-id  (:lipas-id site)
   :type-code (-> site :type :type-code)
   :city-code (-> site :location :city :city-code)
   ;; Sites SHOULD usually just have one activity type
   :activity (some->> (keys (:activities site))
                      (map name)
                      set)})

(defn check-role
  "Check if user has the given role USUALLY
  this should not be used to grant privileges.
  Admin role is ocassionally checked for tracking etc. non
  authorization purposes."
  [user role]
  (->> user
       :permissions
       :roles
       (some (fn [x]
               (= role (:role x))))))

(defn conform-roles [roles]
  ;; Doesn't seem to work due to multi-spec?
  ;; :type is conformed, but coll-of :into #{} doesn't work
  ;; (st/conform! :lipas.user.permissions/roles roles st/json-transformer)
  (mapv (fn [role]
          (cond-> (update role :role keyword)
            (:type-code role) (update :type-code set)
            (:city-code role) (update :city-code set)
            (:lipas-id role) (update :lipas-id set)
            (:activity role) (update :activity set)))
        roles))

(defn- es-add-terms [query [k v]]
  (if (seq v)
    (let [t {:terms {k v}}]
      (cond
        ;; Already 2 terms queries -> add to the bool query
        (:bool query) (update-in query [:bool :must] conj t)
        ;; 1->2 terms queries, wrap in bool
        (:terms query) {:bool {:must [query t]}}
        ;; 1 terms query
        :else t))
    query))

(defn wrap-es-query-site-has-privilege
  "Wraps a ES site search query with bool query to only returns
  results where user has :site/create-edit role."
  [query user required-privilege]
  (let [;; If user can edit any sites (like admin) we don't need to add any ES queries
        ;; to filter the results.
        edit-all-sites? (check-privilege user {} required-privilege)
        ;; Select user roles that would give the create-edit privilege to some sites.
        ;; The the role-context keys are applied later into the ES query itself.
        ctx {:type-code ::any
             :city-code ::any
             :lipas-id ::any}
        affecting-roles (->> user :permissions :roles
                             (filter (fn [role]
                                       (and (contains? (:privileges (get roles (:role role))) required-privilege)
                                            (select-role ctx role)))))]

    (cond
      ;; Admin etc. -> no reason to filter the sites
      edit-all-sites?
      query

      (seq affecting-roles)
      ;; Combine wrapped query AND new roles query
      {:bool {:must [query
                     ;; role1 OR role2 OR ...
                     {:bool {:should (mapv (fn [{:keys [city-code type-code lipas-id] :as _role}]
                                             ;; query for each role is role-context1 AND role-context2
                                             ;; using sets/collections for a term checks if the
                                             ;; document matches any value for the term collection.
                                             (reduce es-add-terms
                                                     {}
                                                     [[:location.city.city-code city-code]
                                                      [:type.type-code type-code]
                                                      [:lipas-id lipas-id]]))
                                           affecting-roles)}}]}}

      ;; User doesn't have edit roles? No filtering
      ;; The checkbox shouldn't be visible
      :else query)))
