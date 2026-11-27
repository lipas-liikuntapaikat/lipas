(ns lipas.data.ptv-table-sort
  "Sorting the PTV tool's tables, Liikuntapaikat and Palvelut, by the
   column the user clicks: status columns most critical first, counts
   smallest first, text alphabetically; a second click reverses. Ties go
   by name, A→Z either way.")

(defn audit-rank
  "Most critical first: changes requested, approved with a remark to read,
   approved, no verdict on the current text."
  [audit-status]
  (case audit-status
    :changes-requested 1
    :approved-with-feedback 2
    :approved 3
    :none 4
    5))

(defn sync-rank
  "Most critical first (ptv-review/sync-statuses, ptv-services/sync-status):
   blocked, awaiting a choice, ready to sync, up to date, not synced yet
   (nil: a service Lipas hasn't written), another system's service, and
   last a site whose sync is switched off (`off?`)."
  [sync-status off?]
  (if off?
    7
    (case sync-status
      :blocked 1
      :awaiting-choice 2
      :ready 3
      :ok 4
      (:not-synced nil) 5
      6)))

(defn next-sort
  "The sort after a click on column `clicked`: ascending, or the other way
   when the table is sorted by it already."
  [{:keys [col dir]} clicked]
  (if (= col clicked)
    {:col clicked :dir (if (= :asc dir) :desc :asc)}
    {:col clicked :dir :asc}))

(defn- nil-last
  "Compares `a` and `b` with `cmp`, a missing value (nil) after the others."
  [cmp a b]
  (cond
    (and (nil? a) (nil? b)) 0
    (nil? a) 1
    (nil? b) -1
    :else (cmp a b)))

(defn row-comparator
  "A comparator of two rows sorted by column `col` (`dir` :asc or :desc):
   by the value `columns` gives for it ({col value-fn}), then by `name-fn`
   A→Z whichever the direction. A missing value or name goes last either
   way. `compare-text` compares two strings (in the browser, the Finnish
   collator)."
  [{:keys [col dir]} {:keys [columns name-fn compare-text]}]
  (let [value (get columns col)
        sign (if (= :desc dir) -1 1)
        compare-values (fn [a b]
                         (if (and (string? a) (string? b))
                           (compare-text a b)
                           (compare a b)))]
    (fn [a b]
      (let [c (nil-last #(* sign (compare-values %1 %2)) (value a) (value b))]
        (if (zero? c)
          (nil-last compare-text (name-fn a) (name-fn b))
          c)))))
