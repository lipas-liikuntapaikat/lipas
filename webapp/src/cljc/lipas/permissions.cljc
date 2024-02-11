(ns lipas.permissions
  (:require
   [clojure.spec.alpha :as s]
   [lipas.schema.core]))

(def default-permissions {:draft? true})

(def draft?
  "All users are allowed to make drafts of any sports-sites in any
  city."
  (constantly true))

(defn- access-to-sports-site? [{:keys [sports-sites]} sports-site]
  (let [lipas-id (-> sports-site :lipas-id)]
    (when lipas-id
      (some #{lipas-id} sports-sites))))

(defn- access-to-city? [{:keys [cities all-cities?]} sports-site]
  (let [city-code (-> sports-site :location :city :city-code)]
    (or all-cities?
        (some #{city-code} cities))))

(defn- access-to-type? [{:keys [types all-types?]} sports-site]
  (let [type-code (-> sports-site :type :type-code)]
    (or all-types?
        (some #{type-code} types))))

(defn publish?
  "Returns `true` if permisssions allow user to publish changes to `sports-site`.

  User can have either explicit or implicit permissions:

  Explicit:
  :admin?       can edit any site
  :sports-sites individual sites user can edit

  Implicit: (access is required to *both* city and type)
  :cities       can edit if site is located in these cities
  :types        can edit if site is of type in these types
  :all-cities?  can edit sites in any city
  :all-types?   can edit sites of any type

  Permissions can be any combination of these keys. Explicit
  permissions overrule implicit ones by short circuiting."
  [permissions sports-site]

  {:pre [(s/valid? :lipas.user/permissions permissions)
         (or (s/valid? :lipas/sports-site-like sports-site)
             (s/explain :lipas/sports-site-like sports-site))]}

  (let [{:keys [admin?]} permissions]

    (boolean
     (or

      ;; Explicitly
      admin?
      (access-to-sports-site? permissions sports-site)

      ;; Implicitly
      (and (access-to-city? permissions sports-site)
           (access-to-type? permissions sports-site))))))

(defn activities?
  [permissions]
  (or (:admin? permissions)
      (some? (seq (:activities permissions)))))
