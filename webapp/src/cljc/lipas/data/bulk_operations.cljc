(ns lipas.data.bulk-operations
  "Shared definition of the fields the org bulk-edit tool may set across many
  sports sites at once.

  ONE registry, used on both sides so they can't drift:
  - the FRONTEND renders a field card per spec (dispatching the input widget on
    `:type`) and builds the update patch from the specs' `:path`s;
  - the BACKEND uses the same `:path`s as its write whitelist and to know which
    fields are clearable.

  Two kinds of fields:
  - STATIC, type-independent fields (`static-fields`): contact info, a few core
    attributes, and address fields. Fixed set, known at compile time.
  - DYNAMIC properties (`property-fields`): the type-specific `:properties.*`
    attributes. Which ones are offered depends on the selected sites — only the
    properties COMMON to every selected site's type (the intersection) can be
    bulk-set, so these are computed at runtime from the selection.

  A field spec:
    {:field-id     unique keyword, the key under which the FE holds the value
                   and the id kept in the `selected-fields` set
     :path         document path the value is written to (assoc-in / dissoc-in)
     :type         :text | :number | :enum | :enum-coll | :boolean — drives the
                   input widget and (loosely) the expected value shape
     :clearable?   may the field be emptied? false ⇒ required, no clear option
     :group        :contact | :basic | :location | :properties (UI sectioning)
     :label        {:fi .. :se .. :en ..} display label
     :opts         for :enum/:enum-coll — {value {:fi .. :se .. :en ..}} choices
     :high-impact? truthy ⇒ FE asks for confirmation before applying (e.g. status)
     :property?    truthy for dynamic property fields}"
  (:require [lipas.data.admins :as admins]
            [lipas.data.prop-types :as prop-types]
            [lipas.data.status :as status]
            [lipas.data.types :as types]))

(def contact-fields
  "The original bulk-edit surface: public contact info. Kept first so the tool
  reads the same as before for its most common use."
  [{:field-id :email :path [:email] :type :text :clearable? true :group :contact
    :label {:fi "Sähköposti (julkinen)" :se "E-post (offentlig)" :en "Email (public)"}}
   {:field-id :phone-number :path [:phone-number] :type :text :clearable? true :group :contact
    :label {:fi "Puhelinnumero" :se "Telefonnummer" :en "Phone number"}}
   {:field-id :www :path [:www] :type :text :clearable? true :group :contact
    :label {:fi "Web-sivu" :se "Webbsida" :en "Website"}}
   {:field-id :reservations-link :path [:reservations-link] :type :text :clearable? true :group :contact
    :label {:fi "Tilavaraukset" :se "Lokalreservationer" :en "Reservations"}}])

(def basic-fields
  "Core, type-independent attributes. `status` and `admin` are required on a
  site so they can be re-set but not cleared."
  [{:field-id :status :path [:status] :type :enum :clearable? false :group :basic
    :high-impact? true
    :opts status/statuses
    :label {:fi "Liikuntapaikan tila" :se "Idrottsplatsens status" :en "Sports facility status"}}
   {:field-id :admin :path [:admin] :type :enum :clearable? false :group :basic
    :opts admins/all
    :label {:fi "Ylläpitäjä" :se "Underhållare" :en "Administrator"}}
   {:field-id :construction-year :path [:construction-year] :type :number :clearable? true :group :basic
    :label {:fi "Perustamisvuosi" :se "Grundläggningsår" :en "Year of establishment"}}])

(def location-fields
  "Address fields. `:neighborhood` lives one level deeper, under :location :city."
  [{:field-id :postal-code :path [:location :postal-code] :type :text :clearable? true :group :location
    :label {:fi "Postinumero" :se "Postnummer" :en "Postal code"}}
   {:field-id :postal-office :path [:location :postal-office] :type :text :clearable? true :group :location
    :label {:fi "Postitoimipaikka" :se "Postort" :en "Postal office"}}
   {:field-id :neighborhood :path [:location :city :neighborhood] :type :text :clearable? true :group :location
    :label {:fi "Kuntaosa" :se "Kommundel" :en "Neighborhood"}}])

(def static-fields
  "All type-independent bulk-editable fields, in display order."
  (vec (concat contact-fields basic-fields location-fields)))

(def static-field-by-id
  (into {} (map (juxt :field-id identity)) static-fields))

(def static-field-paths
  "field-id -> document path for the static fields (the BE write whitelist)."
  (into {} (map (juxt :field-id :path)) static-fields))

(defn- data-type->input
  "Map a prop-type `:data-type` to the field-registry `:type` (input widget)."
  [data-type]
  (case data-type
    "boolean"   :boolean
    "enum"      :enum
    "enum-coll" :enum-coll
    "numeric"   :number
    ;; "string" and anything unforeseen render as a plain text field
    :text))

(defn- normalize-opts
  "Prop-type options are {value {:label {:fi ..}}}; the enum data maps
  (`status/statuses`, `admins/all`) are already {value {:fi ..}}. Normalize both
  to {value {:fi .. :se .. :en ..}} so the UI renders them uniformly."
  [opts]
  (reduce-kv (fn [m value v] (assoc m value (or (:label v) v))) {} opts))

(defn property-field
  "Build a field spec for a single property key `prop-k` from its prop-type def."
  [prop-k]
  (let [{:keys [name data-type opts]} (get prop-types/all prop-k)]
    (cond-> {:field-id   prop-k
             :path       [:properties prop-k]
             :type       (data-type->input data-type)
             :clearable? true
             :property?  true
             :group      :properties
             :label      name}
      (seq opts) (assoc :opts (normalize-opts opts)))))

(defn property-fields
  "Ordered property field specs common to ALL `type-codes` (their intersection).
  Empty when the selection shares no property (or is empty). Sorted by Finnish
  label for a stable, human-readable order."
  [type-codes]
  (->> (types/common-prop-keys type-codes)
       (map property-field)
       (sort-by (comp :fi :label))
       vec))
