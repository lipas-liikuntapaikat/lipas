(ns lipas.ui.ptv.review-fetch
  "The site form's fetch of a site's PTV sync review
   (:lipas.ui.map.events/fetch-ptv-site-review) as app-db state: which
   request is the site's latest, whether its response may land, and whether
   the site's Save waits for it. The review itself lives at
   lipas.ui.ptv.review-data/site-path.

   Split out of the events (and written as .cljc) so the rules can be
   tested on the JVM.")

(def timeout-ms
  "How long a fetch may take before it counts as failed, which releases
   Save: PTV answers a read in about a second, a slow PTV in several."
  30000)

(defn request-path
  "The site's latest fetch of its review, {:id :edit?}, until it lands."
  [lipas-id]
  [:ptv :review :request lipas-id])

(defn start
  "`db` with the fetch `id` the site's latest: an earlier one's response
   no longer lands. `edit?`: the site is being edited, and its Save waits
   for this fetch."
  [db lipas-id id edit?]
  (assoc-in db (request-path lipas-id) {:id id :edit? (boolean edit?)}))

(defn latest?
  "Whether fetch `id` is the site's latest, the only one whose response
   lands. One started before it (an earlier edit's, or the view's) is
   stale, as is any after the site's fetches were dropped (forget)."
  [db lipas-id id]
  (= id (:id (get-in db (request-path lipas-id)))))

(defn forget
  "`db` without the site's fetch: it landed, or the edit it was for is
   over (saved, cancelled, or a new edit starts). A response still on its
   way is then stale."
  [db lipas-id]
  (update-in db [:ptv :review :request] dissoc lipas-id))

(defn save-waits?
  "Whether the site's Save waits for its review: a fetch started during
   the edit hasn't landed. A fetch for viewing the site never blocks."
  [db lipas-id]
  (boolean (:edit? (get-in db (request-path lipas-id)))))
