;; PTV Palvelut table scenarios for Utajärvi (local DB <-> PTV test env).
;; Plan: docs/wip/ptv-services-table-browser-scenarios.md
;; (load-file "dev/user/ptv_services_scenarios.clj")
;; (require '[user.ptv-services-scenarios :as ps])
;; (ps/run-all!)   ; p1-p11 set up
;; (ps/report)     ; each scenario service as the table computes it
;; (ps/state :p1)  ; one service: PTV, Lipas's mirror, review rows, audit, links
;; (ps/cleanup!)   ; archives p8 and p10, undoes F1, puts p5's English back
;;
;; Every text a scenario writes carries the marker "(e2e...)". The baseline
;; functions strip the markers from the live texts and write the result with
;; Lipas, so PTV and Lipas's mirror agree again, whatever an earlier run left.
(ns user.ptv-services-scenarios
  (:require [clojure.string :as str]
            [integrant.repl.state :as state]
            [lipas.backend.db.db :as db]
            [lipas.backend.db.ptv-service :as ptv-service-db]
            [lipas.backend.org :as backend-org]
            [lipas.backend.ptv.audit :as ptv-audit]
            [lipas.backend.ptv.core :as ptv-core]
            [lipas.backend.ptv.integration :as ptvi]
            [lipas.data.ptv :as ptv-data]
            [lipas.data.ptv-services :as ptv-services]))

(def org "3d1759a2-e47a-4947-9a31-cab1c1e2512b")

(def svc
  {:p1 "8ee111d8-a61c-4d05-bc77-a5fe13c38d0a" ; Jäähallit
   :p2 "5f74a478-e79e-42cf-992b-c99731d93bf3" ; Kuntoilukeskukset ja liikuntasalit
   :p3 "0ebaa2ae-5c1a-4a26-901a-d43110458257" ; Pallokentät
   :p4 "ea2e945a-21b8-45d3-a8f6-db0758042113" ; Maauimalat ja uimarannat
   :p5 "62afc974-f12e-421f-a72d-e3ae9da813a2" ; Lipas capture -testi C
   :p6 "b37555b6-f906-4e61-8f57-0318006f68c6" ; PTV Test3
   :p7 "10d38d54-ac95-4e6b-85b9-3eb42f7ba35e" ; Lipas capture -testi D
   :p9 "0d7771c4-6090-4755-9448-ea4c4771d503" ; Puistojumppa
   :p11 "5a17b31e-0a50-49d8-9549-9a93cd088a16"}) ; Retkeilyn palvelut

(def other-system-source-id "e2e-other-system-1")

;; s1 Utajärven pallokenttä: its location is connected to p8
(def p8-site 615340)
;; s13 Utajärven urheilukentän ulkokuntoilupaikka: F1 links it to p9
(def f1-site 605450)

;; p5's English version, which C2 removes
(def p5-en {:name "Lipas capture test C" :summary "C summary" :description "C description."})

(defn- sys [] state/system)
(defn- dbc [] (:lipas/db (sys)))
(defn- ptv [] (:lipas/ptv (sys)))

(defn- admin []
  (db/get-user-by-username (dbc) {:username "admin"}))

(defn- id-of [k-or-id]
  (if (keyword? k-or-id) (get svc k-or-id) k-or-id))

(defn- live [k-or-id]
  (ptvi/get-service (ptv) org (id-of k-or-id)))

(def ^:private langs {"fi" :fi "sv" :se "en" :en})

(defn- texts-of
  "{field {locale text}} of a PTV service's names and the texts Lipas writes."
  [service]
  (let [descs (fn [t] (into {} (keep (fn [{:keys [type language value]}]
                                       (when (= t type)
                                         (when-let [l (langs language)] [l value]))))
                            (:serviceDescriptions service)))]
    {:name (ptv-data/service-names service)
     :summary (descs "Summary")
     :description (descs "Description")
     :user-instruction (descs "UserInstruction")}))

(defn ->ui-service
  "A PTV service as the tool's ::subs/services gives it (see there)."
  [service]
  (let [t (texts-of service)]
    {:label (ptv-data/select-service-name (:serviceNames service))
     :service-id (:id service)
     :source-id (:sourceId service)
     :management (ptv-data/service-management (:sourceId service))
     :name (:name t)
     :language-versions (into [] (keep ptv-data/lang->locale)
                              (ptv-data/service-language-versions service))
     :ptv-entries (select-keys service [:serviceNames :serviceDescriptions :requirements
                                        :publishingStatus :generalDescriptionId
                                        :targetGroups :ontologyTerms :serviceClasses])
     :publishing-status (:publishingStatus service)
     :summary (:summary t)
     :description (:description t)
     :user-instruction (:user-instruction t)
     :service-modified (:modified service)
     :service-channels (->> service :serviceChannels (map :serviceChannel))
     :city-codes (->> service :areas
                      (mapcat (fn [{:keys [type municipalities]}]
                                (when (= "Municipality" type) municipalities)))
                      (map (comp parse-long :code))
                      vec)}))

(defn- org-languages []
  (or (:supported-languages (ptvi/get-org-ptv-config-with-fallback (ptv) org))
      ptv-data/fallback-languages))

(defn- lipas-org-id []
  (:id (backend-org/get-org-by-ptv-org-id (dbc) org)))

(defn- mirror [service]
  (when-let [source-id (:sourceId service)]
    (ptv-service-db/get-current (dbc) (lipas-org-id) source-id)))

(defn lipas-write!
  "Writes `edits` ({field {locale text}}, plus :remove-languages for a
   native) over the service's live texts as the tool's \"Synkronoi nyt\"
   does: the body is ptv-services/service-state's :data, sent through
   upsert-ptv-service!, so PTV and Lipas's mirror agree afterwards."
  ([k-or-id] (lipas-write! k-or-id {}))
  ([k-or-id edits]
   (let [service (->ui-service (live k-or-id))
         {:keys [data]} (ptv-services/service-state
                          {:org-id org :service service :descriptions edits
                           :org-languages (org-languages)})]
     (ptv-core/upsert-ptv-service! (dbc) (ptv) (admin) data))))

(defn ptv-edit!
  "What an edit in PTV's own UI does: `f` changes the live service's texts
   ({field {locale text}}), written by PTV id without touching Lipas's
   mirror. native-patch-body stamps Lipas's adopted sourceId into the body,
   which would turn a service Lipas created into an adopted one, so the
   live sourceId goes back in (or none, for a service without one)."
  [k-or-id f]
  (let [id (id-of k-or-id)
        service (live id)
        body (ptv-data/native-patch-body org id service (f (texts-of service)) [])
        body (if (str/blank? (:sourceId service))
               (dissoc body :sourceId)
               (assoc body :sourceId (:sourceId service)))]
    (ptvi/update-service-by-id (ptv) org id body)))

(defn archive!
  "Archives a service: a PUT of its own texts with publishingStatus
   \"Deleted\". A status-only PUT is refused for services (PTV wants the
   target groups, ontology terms and service classes with it), and so is
   any PUT but a \"Modified\" one while PTV holds a draft of the service."
  [service-id]
  (let [service (live service-id)
        body (-> (ptv-data/native-patch-body org service-id service (texts-of service) [])
                 (assoc :publishingStatus "Deleted"))
        body (if (str/blank? (:sourceId service))
               (dissoc body :sourceId)
               (assoc body :sourceId (:sourceId service)))]
    (ptvi/update-service-by-id (ptv) org service-id body)))

(defn- strip-marks [s]
  (some-> s (str/replace #"\s*\(e2e[^)]*\)" "") not-empty))

(defn- baseline!
  "Strips the scenario markers from the live texts and writes the result
   with Lipas."
  [k]
  (let [t (texts-of (live k))
        clean (into {} (for [[field by-locale] t]
                         [field (into {} (for [[l v] by-locale] [l (strip-marks v)]))]))]
    (lipas-write! k (cond-> (select-keys clean [:summary :description :user-instruction])
                      ;; a native's names go as edits too; a Lipas-created
                      ;; service's only by :name :fi (the pencil)
                      true (assoc :name (:name clean))))))

(defn- mark [s tag]
  (str (strip-marks s) " (e2e " tag ")"))

;;; Scenario setups ;;;

(defn p1-baseline! [] (baseline! :p1))

(defn p2-ptv-summary! []
  (baseline! :p2)
  (ptv-edit! :p2 #(update-in % [:summary :fi] mark "PTV")))

(defn p3-ptv-summary! []
  (baseline! :p3)
  (ptv-edit! :p3 #(update-in % [:summary :fi] mark "PTV")))

(defn p4-ptv-name! []
  (baseline! :p4)
  (ptv-edit! :p4 #(update-in % [:name :fi] mark "PTV")))

(defn p5-baseline! []
  (let [service (live :p5)]
    (if (some #{"en"} (ptv-data/service-language-versions service))
      (baseline! :p5)
      ;; C2 removed the English version: write it back
      (lipas-write! :p5 {:name (assoc (:name (texts-of service)) :en (:name p5-en))
                         :summary {:en (:summary p5-en)}
                         :description {:en (:description p5-en)}}))))

(defn- find-other-system []
  (->> (:itemList (ptvi/get-org-services (ptv) org))
       (filter #(= other-system-source-id (:sourceId %)))
       first))

(defn- site-channel-id [lipas-id]
  (-> (db/get-sports-site (dbc) lipas-id) :ptv :service-channel-ids first))

(defn p8-other-system!
  "A service another system maintains: created with a sourceId that isn't
   Lipas's, and connected to s1's location so the table lists it."
  []
  (let [existing (find-other-system)
        id (or (:id existing)
               (:id (ptvi/create-service
                      (ptv)
                      (-> (ptv-data/->ptv-service
                            {:org-id org
                             :city-codes [889]
                             :source-id other-system-source-id
                             :sub-category-id 2100
                             :languages ["fi"]
                             :summary {:fi "Toisen järjestelmän ylläpitämä testipalvelu (e2e)."}
                             :description {:fi "Palvelu on luotu Lipaksen selaintestejä varten. Sen sourceId ei ole Lipaksen, joten Lipas näyttää sen vain luettavana."}})
                          (assoc :serviceNames [{:type "Name" :language "fi" :value "e2e toinen järjestelmä"}])))))]
    (ptvi/update-service-connections (ptv) org id #(conj % (site-channel-id p8-site)))
    id))

(defn p11-baseline! [] (baseline! :p11))

(def all [#'p1-baseline! #'p2-ptv-summary! #'p3-ptv-summary! #'p4-ptv-name!
          #'p5-baseline! #'p8-other-system! #'p11-baseline!])

(defn run-all!
  ([] (run-all! all))
  ([scenarios]
   (vec (for [s scenarios]
          (try [(:name (meta s)) :ok (let [r (s)] (if (map? r) (:id r) r))]
               (catch Exception e
                 [(:name (meta s)) :failed (ex-message e) (some-> e ex-data :resp :body)]))))))

;;; Verification ;;;

(defn- saved-sites
  "lipas-ids of Utajärvi's sites whose saved :ptv :service-ids hold
   `service-id`, from the search index the tool loads them from."
  [service-id]
  (->> (ptv-core/get-ptv-integration-candidates (:lipas/search (sys))
                                                {:city-codes [889] :owners ["city" "city-main-owner"]})
       (filter #(some #{service-id} (get-in % [:ptv :service-ids])))
       (map :lipas-id)
       set))

(defn state
  "Where a service stands: live in PTV, in Lipas's mirror, the review rows
   left with no edits, its audit verdicts and the sites linked to it."
  [k-or-id]
  (let [service (live k-or-id)
        ui (->ui-service service)
        m (mirror service)
        base (some-> m :document (select-keys [:name :summary :description :user-instruction
                                               :languages :last-sync]))
        channels (map :serviceChannel (:serviceChannels service))
        audit (get (ptv-audit/current-service-audits (dbc) (lipas-org-id)) (str (:id service)))
        st (ptv-services/service-state {:org-id org :service ui :org-languages (org-languages)
                                        :base base :audit audit})]
    {:ptv (merge (select-keys service [:publishingStatus :sourceId :languages :modified])
                 (texts-of service)
                 {:channels (count channels)})
     :mirror (some-> base (update :last-sync str))
     :sync-status (:sync-status st)
     :review (mapv (juxt :key :origin) (:rows (:review st)))
     :audit (some-> audit (select-keys [:summary :description :user-instruction])
                    (update-vals :status))
     :sites (saved-sites (:id service))}))

(defn report
  "Each scenario service: its name, sync status, review rows, channels and
   sites."
  []
  (vec (for [[k id] (sort-by key (assoc svc :p8 (:id (find-other-system))))
             :when id
             :let [{:keys [ptv sync-status review sites]} (try (state id) (catch Exception e {:ptv {:error (ex-message e)}}))]]
         [k (get-in ptv [:name :fi]) (:publishingStatus ptv) sync-status review
          (:channels ptv) sites])))

;;; Cleanup ;;;

(defn cleanup!
  "Archives p8 and the service E1 created, unlinks F1's site from p9 in
   PTV, and puts p5's English back. F1's link in Lipas is removed in the
   tool (save and sync), as it was made."
  []
  (let [p10 (->> (:itemList (ptvi/get-org-services (ptv) org))
                 (filter #(= (ptv-data/->service-source-id org 4800) (:sourceId %)))
                 first)]
    [(when-let [s (find-other-system)] [:p8 (:publishingStatus (archive! (:id s)))])
     (when p10 [:p10 (:publishingStatus (archive! (:id p10)))])
     [:p5 (:id (p5-baseline!))]]))
