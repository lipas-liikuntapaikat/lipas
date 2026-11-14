# Backdated closures: remove `invalidate-since!`, add `:decommission-year`

Status: **decisions made, plan next** (2026-11-14). §1–§6 numbers come from
the local prod copy (2026-11-01). §10 has the prod rerun (2026-11-14), which
supersedes them. §7 records the decisions.

## 1. Why this came up

We want a new Tilastot tab that shows which kinds of sites have been taken
permanently out of service, and where and when. Statuses have been collected
since 2019. The goal is to build the report from a single Elasticsearch query,
the way the Rakennusvuodet tab does. That needs a reliable closure year for
every closed site. Looking for one turned up the problems below. The report
would undercount until they are fixed.

## 2. The timestamp model

Revision tables (`sports_site`, `loi`, `org`, `ptv_*`) have two timestamps:

- `created_at`: when the record was written to the database.
- `event_date`: when the event actually happened in the world.

This makes it possible to backfill historical events. The `*_current` views
order by `event_date DESC, created_at DESC` and filter only on the row status
(draft, and archived for orgs), never on the document's `:status`.

Revisions are **full snapshots, not deltas**. A backfilled snapshot is
therefore overridden by any snapshot with a later `event_date`. The
event-sourcing-friendly way to correct history is to append a **corrected
copy**: same `event_date`, newer `created_at`. At an equal `event_date` the
newer copy wins. Migrations already do this. Every revision in these batches
shares `(lipas_id, event_date)` with an older revision:

| Batch day | Revisions | Same-`event_date` copies |
|---|---|---|
| 2022-10-20 | 469 | 469 |
| 2023-06-28 | 7,053 | 7,052 |
| 2024-12-28 | 799 | 799 |
| 2025-10-05 | 561 | 561 |

## 3. How a closure is saved today

1. The removal dialog (`sports_sites/views.cljs:1145`) asks for a reason. For
   "Poistettu käytöstä pysyvästi" it also asks for a year.
2. `::delete` (`sports_sites/events.cljs:301`) sets `:event-date` to now for
   the current year, or otherwise to `->end-of-year`
   (`utils.cljs:68`) = `"YYYY-12-31T23:59:59.999Z"`.
3. `upsert-sports-site!` (`db/db.clj:151`) calls `invalidate-revs-since-this!`
   for out-of-service-permanently. That runs `invalidate-since!`
   (`sql/sports_site.sql:199`):

   ```sql
   UPDATE sports_site
   SET document = jsonb_set(document, '{status}', '"incorrect-data"')
   WHERE lipas_id = :lipas_id AND event_date > :event_date
   ```

   It then inserts the closure revision.
4. For PTV-integrated sites, `save-sports-site!` writes a second revision from
   the PTV sync, dated by the server clock (`ptv/core.clj:607`, `:743`).

The invalidation was added in c72d6443b (2019-02-11, "Added fix for edge-case
when 'deleting' sports-site"). Its intent: when a closure is backdated to, say,
2017 and the site has been edited since, those later edits must not leave the
site looking active.

## 4. What goes wrong

### 4.1 Backdated closures end up as "Väärä tieto"

The invalidation turns the later revisions into `incorrect-data` **in place**.
They still have later `event_date`s than the backdated closure, so they stay
current. The site the user just closed now shows as "Väärä tieto". Search
filters hide that status from non-admins, so for them the site effectively
disappears. ES shows the closure right after the save, but the next full
reindex reads from `sports_site_current` and flips it.

Example: **72992**. It was active (event 2011), then edited on 2020-08-12, then
closed on 2020-08-12 with the year 2018. The 2020-08-12 edit was rewritten to
`incorrect-data`, and it is the current revision.

827 revisions on 558 sites were rewritten. The first broken site is from
2019-02-11, the day the invalidation was committed.

| Sites | Current status | Latest saved revision | Meaning |
|---|---|---|---|
| **531** | incorrect-data | out-of-service-permanently | Still broken. The last thing anyone did was close the site. |
| **21** | incorrect-data | incorrect-data | Re-saved afterwards as "Väärä tieto": a confirmed error, or a later save carrying the wrong status forward. Needs a human look. |
| 6 | active (3) / closed (3) | — | Resolved by later edits. |

Of the 531, 158 have a closure year of 2019 or later; 373 are earlier. Later
revisions per affected site: 1 (346 sites), 2 (174), 3 (26), 4 (8), 5–7 (4).

The 21 to review: 74018, 81934, 84175, 92403, 92511, 93068, 96410, 501560,
504305, 506465, 508585, 508646, 509332, 511867, 523857, 523900, 524085,
524337, 525189, 529959, 612960.

### 4.2 The assumption behind it is wrong

"Väärä tieto" is meant for human errors: *this data is not valid and must not
be counted*. The invalidation assumes that everything recorded after the
closure year was invalid, too. It wasn't. Those revisions were real edits of a
real site. They only lack the closed status.

### 4.3 It has bitten before

Migration `fix_ptv_archived_stale_revisions` repaired 7 prod sites (Raahe and
Utajärvi, Jan–Oct 2026). On closure, the user's revision is dated by the
browser clock and the PTV sync's by the server clock. With the browser clock
ahead, saving the sync revision ran the invalidation and re-marked it
`incorrect-data`. `clamp-future-event-date` (`core.clj:1711`) prevents that now,
but the invalidation rule itself remained.

### 4.4 The dialog's year applies to every status

`::delete` backdates to the selected year whatever the status is. The year
picker is only visible for permanent closure, but the selected year stays set
if the user then switches to another reason. Without the invalidation, the
backdated revision is simply hidden behind later ones, so the change doesn't
take:

- **611037**: marked "Väärä tieto" on 2023-08-17 with the event date
  2013-12-31. It is still active.
- **516115**: marked temporarily closed on 2025-10-10 with the event date
  2024-12-31. It is still active.

### 4.5 The closure year can't be read from the current revision

For 30 of 3,804 closed sites, the current revision's `event_date` is in a
different year than the closure. For example, a batch on 2026-06-10 wrote new
revisions dated 2026-06-10 onto about 20 closed sites. With corrected copies
(§6), the current revision's date is by design the date of the last later
edit. The closure year needs its own field.

### 4.6 Incremental syncs filter on `event_date`

- Legacy `deleted-sports-places?since=` (`api/v1/core.clj:132`) filters on
  `event-date >= since`.
- Legacy `modifiedAfter` (`api/v1/search.clj:94`) filters on `lastModified`,
  which falls back to `event-date` (`api/v1/sports_place.clj:155`).

Every backfilled event is therefore invisible to a client that syncs
incrementally. 1,202 current closures carry a Dec 31 date from the year
picker, so they were recorded after the date they claim. Syncs should filter
on the record's `created_at`. The search index has no `created-at`; only the
analytics index does (`search.clj:300`).

### 4.7 LOIs have the same backdating, without the invalidation

`loi/events.cljs:55` backdates a LOI closure to `->end-of-year`. No LOI has
been backdated yet (0 rows with the Dec 31 date), but a backdated LOI closure
would be hidden behind later revisions. `loi_current` also differs from the
other views: it joins on `max(event_date)`, with no `created_at` tie-breaker
and no draft filter.

### 4.8 "Muokattu viimeksi" shows `event_date`

The site view (`sports_sites/views.cljs:173`) labels `:event-date` as "last
modified". A site closed today with the year 2018 shows 31.12.2018. Under the
model in §2 the value is right and the label is wrong. Minor.

## 5. Checked and fine

- **The `*_current` views don't need to change.** `event_date` ordering is
  load-bearing: the corrected-copy batches in §2 depend on it. Ordering by
  `created_at` instead would change the current revision of the 531 sites plus
  19 others.
- **Robot copies with an older `event_date`** (613481, 616969, 616970): a
  robot-authored copy of an earlier revision was written after the user's next
  change. Ordering kept the user's change current. This works as designed.
- **Year bucketing in `sports_site_by_year`**: the database time zone is UTC,
  so the Dec 31 `23:59:59.999Z` dates fall in the intended year. In Helsinki
  time they are already Jan 1, so any year bucketing in Helsinki time (ES
  date histograms, client code) puts them a year late.

## 6. Direction so far

1. **Remove `invalidate-since!` completely**, both the SQL query and
   `invalidate-revs-since-this!`.
2. **Keep `event_date` as when it happened.** A closure in a past year stays
   backdated.
3. **Rebase instead of overwrite.** When a closure is inserted before existing
   later revisions, append a corrected copy of each later revision in the same
   transaction. Each copy keeps its original's `event_date`, gets a new
   `created_at`, and carries the closed status and `:decommission-year`.
   Nothing is changed in place. The current view then shows the site closed,
   and `sports_site_by_year` shows it closed in the intervening years too.
4. **Add an explicit `:decommission-year`** (an integer, shaped like
   `:construction-year`). Add it to the sports-site schema, the strict ES
   mapping and the v2 API output. Write it from the dialog. Remove it on
   reactivation (`::resurrect`). It travels with later edits because the
   whole document is copied.
5. **Fix the dialog** so that the year is used only for permanent closure.
6. **Treat LOIs the same way.**
7. **Repair with a migration:**
   - Run the same rebase for the 531 sites, and for whichever of the 21
     survive review. The 827 rewritten originals stay as they are, as an audit
     trail. Their original status was overwritten and can't be recovered, and
     the copies supersede them.
   - Fill in `:decommission-year` on the current revision of every closed site,
     in place. It only adds a field. The year comes from the revision that
     started the current closure: UTC year for the Dec 31 dates, Helsinki year
     otherwise.
   - Full search reindex.

### Rejected

- **Save closures with `event_date` = now and keep the year only in
  `:decommission-year`.** It breaks the model in §2, because `event_date`
  would duplicate `created_at` for closures. It would also make
  `sports_site_by_year` show closed sites as active until the year the
  closure was recorded.
- **Order the `*_current` views by `created_at`.** See §5.

## 7. Decisions (2026-11-14)

1. **Rebase rule: the closure wins.** Every later-dated revision gets a
   copy carrying the closed status and `:decommission-year`, whatever its own
   status, "Väärä tieto" included. Only the status and the year change; every
   other field stays as it was in that revision.
   - The closure is the newest decision: every later-dated revision was
     recorded before it, and the dialog starts from the current revision, so
     the closer saw what they were overriding.
   - Skipping "Väärä tieto" revisions was considered first and dropped: a
     correct closure would then silently not take effect, and an ordinary user
     couldn't fix it. A wrong closure under this rule is visible and can be
     fixed the normal way (mark "Väärä tieto" today; a revision dated now
     wins).
   - Revisions recorded *after* the closure are newer decisions and are not
     touched.
   Background: in 2019 the reasoning was that edits to a site that no longer
   exists must be invalid. That was too naive.
2. **Closures only.** Prod was checked for other backdated saves (§10).
3. **`:decommission-year` is required** on permanent closure (400 without
   it). Backfill: where nothing better is known, the year of the `event_date`
   of the revision that set the closed status.
4. **The 20 review sites**: the product owner does a first sweep and hands
   the unclear ones to the project team.
5. **Sync APIs → `created_at`** (§4.6): separate follow-up PR. Note that the
   rebase alone doesn't make backdated closures visible to incremental syncs:
   the current revision is then a copy that keeps the later edit's past
   `event_date`, and `deleted-sports-places` filters on the current ES
   document's `event-date`.
6. **Prod rerun** done read-only on 2026-11-14 (§10).

Still open: the author of the rebase copies (closing user in the live path,
`robot@lipas.fi` in the repair migration, as proposed).

## 8. Downstream: the stats tab

Once the closure year is explicit and the 531 are repaired, the whole report
is one `_search`:

```clojure
{:size        5000
 :_source     [:lipas-id :name :type.type-code :location.city.city-code
               :construction-year :decommission-year]
 :query       {:bool {:filter [#_"selected cities and types"]}}
 :aggs        {:stock {:filter {:term {:status "active"}}
                       :aggs   {:by-type {:terms {:field :type.type-code :size 200}}
                                :by-city {:terms {:field :location.city.city-code :size 400}}}}}
 :post_filter {:bool {:filter [{:term {:status "out-of-service-permanently"}}
                               {:range {:decommission-year {:gte 2019}}}]}}}
```

The hits (about 3,400 today, growing about 500 a year) feed the charts and the
table. The aggregation gives the active-site counts for "share of stock". It
fits the search guard's limits (`size` ≤ 10,000, aggregation size ≤ 2,000).
Things to handle in the view:

- Recording catches up slowly, so recent years are incomplete.
- Cleanup campaigns cause spikes. One user recorded 283 of the 893 closures
  entered in 2020.
- Raw counts follow stock size. Share of stock reorders the types: outdoor ice
  13–16% removed since 2019, swimming spots 5%.

## 9. Queries

Run on prod inside `BEGIN; SET TRANSACTION READ ONLY; … ROLLBACK;`.

Revisions rewritten by the invalidation:

```sql
SELECT count(*) AS revisions, count(DISTINCT s.lipas_id) AS sites
FROM sports_site s
WHERE s.status = 'published'
  AND s.document->>'status' = 'incorrect-data'
  AND EXISTS (
    SELECT 1 FROM sports_site o
    WHERE o.lipas_id = s.lipas_id
      AND o.status = 'published'
      AND o.document->>'status' = 'out-of-service-permanently'
      AND o.created_at > s.created_at
      AND o.event_date < s.event_date);
```

Classify the affected sites (531 / 21 / 6 in §4.1):

```sql
WITH mutated AS (
  SELECT DISTINCT s.lipas_id
  FROM sports_site s
  WHERE s.status = 'published'
    AND s.document->>'status' = 'incorrect-data'
    AND EXISTS (
      SELECT 1 FROM sports_site o
      WHERE o.lipas_id = s.lipas_id
        AND o.status = 'published'
        AND o.document->>'status' = 'out-of-service-permanently'
        AND o.created_at > s.created_at
        AND o.event_date < s.event_date)),
latest_saved AS (
  SELECT DISTINCT ON (lipas_id) lipas_id, document->>'status' AS status
  FROM sports_site
  WHERE status <> 'draft'
  ORDER BY lipas_id, created_at DESC, event_date DESC)
SELECT c.document->>'status' AS current_status,
       l.status AS latest_saved_status,
       count(*)
FROM mutated m
JOIN sports_site_current c USING (lipas_id)
JOIN latest_saved l USING (lipas_id)
GROUP BY 1, 2
ORDER BY 3 DESC;
```

Current closures dated with the year picker (§4.6):

```sql
SELECT count(*)
FROM sports_site_current
WHERE document->>'status' = 'out-of-service-permanently'
  AND to_char(event_date AT TIME ZONE 'UTC', 'MM-DD HH24:MI:SS') = '12-31 23:59:59';
```

Sites whose current revision isn't the latest saved one (§5, the 531 + 19):

```sql
WITH latest_saved AS (
  SELECT DISTINCT ON (lipas_id) lipas_id, id, document->>'status' AS status
  FROM sports_site
  WHERE status <> 'draft'
  ORDER BY lipas_id, created_at DESC, event_date DESC)
SELECT c.document->>'status' AS current_status,
       l.status AS latest_saved_status,
       count(*)
FROM sports_site_current c
JOIN latest_saved l USING (lipas_id)
WHERE c.id <> l.id
GROUP BY 1, 2
ORDER BY 3 DESC;
```

## 10. Prod rerun (2026-11-14, read-only)

Prod data up to 2026-11-13. Every query ran inside `SET TRANSACTION READ ONLY`
with rollback.

| | Local copy (§4) | Prod |
|---|---|---|
| Revisions rewritten by the invalidation | 827 | **882** |
| Sites affected | 558 | **594** |
| Still broken (latest save is the closure) | 531 | **568** |
| Latest save is "Väärä tieto" | 21 | **20** |
| Resolved by later edits | 6 | 6 |
| Current closures dated Dec 31 by the year picker | 1,202 | **1,291** of 4,143 |

The invalidation is still active. Sites hit, by the year the closure was
recorded: 2019: 54, 2020: 205, 2021: 85, 2022: 43, 2023: 49, 2024: 42,
2025: 60, 2026: 56.

### 10.1 Were any of the 882 a person's "Väärä tieto"?

The original statuses were overwritten, so the database can't prove it either
way. For each rewritten revision, the nearest earlier revision that was *not*
rewritten had this status:

| Nearest unrewritten earlier revision | Revisions | Sites |
|---|---|---|
| active | 464 | 322 |
| none: the site's whole recorded history is later than the closure year | 404 | 262 |
| out-of-service-temporarily | 9 | 7 |
| out-of-service-permanently | 5 | 5 |

None is "Väärä tieto", so nothing suggests a person set any of them. The
analytics index can't help: it is rebuilt from the database and carries the
rewritten statuses too. **The repair treats all 882 as invalidation
artefacts.**

### 10.2 The 20 sites whose latest save is "Väärä tieto"

**15 are the same case as the 568.** After the closure, only robot copies were
written. The elevation job (`jobs/dispatcher.clj`, `"elevation"`) reads the
current revision after every save and writes it back as `robot@lipas.fi` with
the same `event_date`. After a backdated closure, the current revision is the
rewritten one, so the robot copy carries "Väärä tieto" forward (508585's copy
comes from the 2024-12-28 migration batch instead). Sites: 92403, 92511,
93068, 96410, 506465, 508585, 508646, 510869, 510870, 511867, 523857, 523900,
524085, 524337, 525189.

**5 have a person's save after the closure.** In every case the person saw
the "Väärä tieto" the invalidation had created, and **none changed the
status**:

| lipas-id | Name | Closed (year) | Saved | Changed vs. what they saw |
|---|---|---|---|---|
| 529959 | Koivukylän koulun luistelukenttä | 2020 | 2022-07-04 | location; comment "poistettu luistelukäytöstä pysyvästi" |
| 516406 | Kiikelin laavu | 2024 | 2026-09-25 | owner-org-id |
| 102533 | Hämeenkosken ilma-aserata | 2006 | 2026-05-12 | search-meta only |
| 74018 | Seipäjoen laavu | 2015 | 2020-10-06 | nothing (the closer re-saved right after closing) |
| 509332 | Socklotin koulun liikuntasali | 2016 | 2020-01-28 | nothing (the closer re-saved right after closing) |

Only 74018 and 509332 are ambiguous: an unchanged re-save is what a deliberate
"Väärä tieto" from the dialog would look like, too.

### 10.3 Other backdated saves (§7.2)

Revisions that were already hidden behind a later-dated revision the moment
they were saved:

- **Energy-report snapshots** (962 Dec 31 revisions plus some Jan 1 ones,
  2018–2020, types 2510/2520/3110/3130). Each describes one past year. Being
  hidden is correct.
- **Robot copies** with the same `event_date` as an older revision. Working
  as designed.
- **Status changes through the removal dialog that never took effect**
  (§4.4). These 9 sites are still wrong:
  - "Väärä tieto", still shown as active: 509995, 611037, 612295, 86296.
  - "Väärä tieto", still shown as planning: 619922, 619939, 621444, 621517,
    621579 (four of them in 2026).

  Five more were since fixed by later edits.
- **Browser clock ahead**: 613080 (2026-08-07) has an edit hidden behind a
  revision dated 10 minutes in the future. `clamp-future-event-date`
  (cad4ebb6c, 2026-10-11) prevents that now.

No other path backdates closures or status changes. Closure-only scope holds.

## 11. Implementation (branch `fix/backdated-closures`, 2026-11-14)

**Save path**
- `invalidate-since!` and `invalidate-revs-since-this!` are gone.
- `db/upsert-sports-site!` → `rebase-onto-closure!`: for a published
  permanent closure, one closed copy (status + `:decommission-year`) of the
  winning revision at each later `event_date`, authored by the closer, in the
  same transaction. One copy per `event_date` because rows written in one
  transaction share `created_at` and two copies would tie. Returns the new
  current revision, so PTV sync, jobs, ES and the UI's app-db get what
  `sports_site_current` now returns.
- `core/upsert-sports-site!` (the user-facing and bulk chokepoint) →
  `check-decommission-year`: 400 `:decommission-year-required` on a closure
  without the year; the year is dropped from any other status. Trusted
  paths (`upsert-sports-site!*`: PTV sync, elevation job, takeover, imports)
  don't check.
- Bulk edit setting status to closed fills the year from the revision's
  `event_date` (closed now). An already closed site keeps its year.
- LOIs: same rebase in `db/upsert-loi!`; `core/upsert-loi!` indexes the
  current LOI from `loi_current` (it indexed the submitted one). The
  `save-loi` response shape is unchanged.

**Schema / index / API**: `:decommission-year` in `lipas.schema.common`, the
site and LOI schemas, the strict ES mapping (`integer`) and the v2 output.

**UI**: the removal dialog uses the year only for permanent closure (fixes
§4.4), sends `:decommission-year`; reactivation drops it; the site view shows
"Poistettu käytöstä vuonna". Same for the LOI dialog. The edit form can't set
the closed status (its selector offers only active/temporarily
closed/planned/planning), so the dialogs, bulk and the API are the only
writers.

**Migrations**
- `20261114120001-loi-same-event-date-revisions`: the LOI primary key was
  `(id, event_date)`, which made same-`event_date` copies impossible. Now
  `(id, event_date, created_at)`, and `loi_current` breaks ties by
  `created_at` like `sports_site_current` (it would have returned every tied
  row). Nothing relied on the old key (importers date LOIs now, no FKs).
- `20261114120100-repair-backdated-closures` (Clojure): rebase sites whose
  newest revision saved by a person is a closure but whose current revision
  isn't closed (robot@lipas.fi as author); log the sites where a person saved
  after the closure for review; backfill `:decommission-year` in place on
  every current closed site and LOI (year of the revision that started the
  current closure; UTC for the Dec 31 marker, Finnish time otherwise).
  `compute-plan` is a pure-read dry run.

**Migratus gotcha (found in the dry run).** Migratus hands a Clojure migration
its transaction's connection as `:conn`; `:db` is the plain spec, *outside* the
transaction. Migrations that write through `:db` (e.g.
`fix_ptv_archived_stale_revisions`) are not atomic despite
`:transaction? true`. And clojure.java.jdbc on `{:connection conn}` wraps
every insert in its own begin/COMMIT unless the spec says it is already in a
transaction (`:level 1`). The repair uses `:conn` for both libraries, with
`:level 1`, and a test proves a rolled-back run leaves nothing behind.

**Local dry run** (prod copy, data to 2026-10-30; one connection, rolled back,
verified):

| | Before | After |
|---|---|---|
| Closed sites (current) | 3,804 | 4,348 (+544 rebased, 782 copies) |
| "Väärä tieto" (current) | 3,510 | 2,966 |
| Broken closures (current incorrect-data, latest save closed) | 531 | 0 |
| Closed sites without a year | 3,804 | 0 |
| Closed LOIs without a year | 42 | 0 |

Left for review locally: 74018, 81934, 84175, 501560, 504305, 509332, 529959,
612960. Runtime 81 s, most of it the rebase (~72 s: `sports_site` has no
general index on `lipas_id`, so every per-site query scans the table).

Closure years after the repair (current closed sites): 2017: 198, 2018: 187,
2019: 436, 2020: 743, 2021: 416, 2022: 461, 2023: 289, 2024: 578,
2025: 442, 2026 (to Oct): 153.

**Independent review (2026-11-14), fixed:**
- *Strict mapping vs. the deploy window.* Between the migration and the
  reindex, every save of a document carrying `:decommission-year` would have
  failed in the live strict index (after its DB commit, as a 500). Startup
  now adds new fields to existing site indices (`search/put-added-fields!`,
  `added-sports-site-fields`); ES allows adding fields to a strict mapping.
- *Copies landed on the document's `:event-date`, not the row's column.*
  94 revisions on the prod copy disagree; a copy at the wrong date wouldn't
  outrank its original. Copies now take the column's date (the document's
  string when it's the same instant).
- *Backfill year for re-closed sites.* Revisions the invalidation rewrote
  counted as "open", so a site closed again after its first closure was
  hidden got the second closure's year (3 sites locally: 505927 → 2017,
  515948 → 2016, 528556 → 2023 now, as first chosen). Closure starts are now
  computed in Clojure (`closure-started-at`, 2 s instead of 26 s).
- The migration fails if any current closed site or LOI is left without a
  year (it couldn't be saved any more).
- An unauthorized closure without a year gets 403, not 400.
- `core/upsert-loi!` and the dev seed with UUID ids / generated closed LOIs.
- Tests: the save endpoint end to end (response, DB, search index, 400/403,
  LOI indexing, a pre-existing strict index).

**Known, not changed:** any closed-status save dated before later revisions
rebases, not only a new closure decision. The UI has no such path; an API
client posting a stale closed document after a reactivation would overwrite
the reactivation with closed copies (the old invalidation would have marked
it incorrect-data instead). The save response is the new current revision,
so its `:event-date` is the latest later revision's, not the submitted one.

**Deploy**
1. Deploy; migrations run (LOI key + view, then the repair, ~1.5–3 min).
   Startup adds `decommission-year` to the live site indices' mappings.
2. Full reindex of sites and LOIs (the repaired sites change status; the
   new field gets indexed for the backfilled sites).
3. Product owner reviews the sites the migration logs for review, plus the 9
   dialog-year sites in §10.3.

**Behaviour change for API clients:** `POST /api/sports-sites` with
`out-of-service-permanently` and no `:decommission-year` now gets a 400.

**Follow-ups:** v1 sync filters → `created_at` (§4.6); index
`sports_site (lipas_id, event_date)`; the "Muokattu viimeksi" label (§4.8);
the stats tab (§8).

**Browser smoke test (2026-11-14, local, headless Playwright)** after running
the two new migrations on the dev DB, a reindex, and seeding test sites
620078 and 620079 and LOI b12c2b75-b67b-4d87-8162-e686be6de40a. All passed:
- Removal dialog on 620078, permanently closed, year 2018: saved; the closure
  plus one closed copy of the 2021 revision; the site view shows "Poistettu
  käytöstä vuonna 2018" with the 2021 content; search agrees.
- Editing that closed site (comment): saved, year kept.
- Reactivation ("Palauta"): active, year gone in DB and search.
- 620079, the §4.4 bug: permanent + 2016, then switched to "Väärä tieto":
  saved dated now (not 2016-12-31) and took effect, no year.
- LOI removal dialog, 2018: closure plus a copy at the 2021 revision's
  date (needs the new LOI key); `loi_current` one row; search agrees.
- Repaired 72992 shows closed 2018; backfilled 73087 shows 2016 (closure
  start, not its 2020 edit).
- Console: no errors. Pre-existing warning: the edit form's status selector
  has no option for a closed site's own status.
