# Site-level image links

Sports sites can have a collection of **image links** attached to them. LIPAS
stores only the metadata — URL, alt-text, copyright, caption — per image. The
image files themselves live in an **external image bank** owned by the
facility's city or organisation.

The first adopter is the city of Loimaa (2026). The feature is generic and
works for any sports site.

## What LIPAS stores

For each image attached to a sports site:

| Field          | Type                      | Description                                   |
|----------------|---------------------------|-----------------------------------------------|
| `:url`         | string (http/https URL)   | Direct URL to the image file in the bank.     |
| `:alt-text`    | localized string (fi/se/en) | Accessibility description.                  |
| `:copyright`   | localized string          | Photographer, source, license, date.          |
| `:description` | localized string          | Free-form caption.                            |

Schema: `src/cljc/lipas/schema/sports_sites/images.cljc`.

## What LIPAS does *not* do

- LIPAS does not upload, host, cache, or redistribute the image files.
- LIPAS does not validate that a URL points to a real image at save time.
- LIPAS does not attempt to verify copyright claims or image rights.

If a link later breaks (404) or the image disappears from the bank, that is a
matter for the image-bank operator. The LIPAS team can remove links on
request when they are unlawful or otherwise clearly inappropriate, but
day-to-day upkeep is the responsibility of whoever added them.

## Licensing

The **metadata** (URL, alt-text, copyright text, caption) is available under
**Creative Commons Attribution 4.0 International (CC BY 4.0)**. Consumers of
the LIPAS API may reuse it freely with attribution.

The **image files** themselves are not covered by this license. They are
subject to whatever terms the image bank sets. LIPAS consumers who render the
images in a UI are responsible for respecting those terms.

## Authorization

Editing image links requires the `:site/edit-images` privilege. Two roles
currently grant it:

- `:admin` — can edit anything.
- `:images-manager` — a narrow role scoped by `:city-code` (required) and
  optionally `:type-code`. This role **cannot** save changes to any other
  field of a sports site. The backend enforces this by diffing the incoming
  revision against the persisted one and rejecting the save if any non-image
  field differs (see `check-permissions!` in `src/clj/lipas/backend/core.clj`).

Other roles that already grant `:site/save-api` (e.g. `:city-manager`,
`:activities-manager`) can also edit images, because image edits flow through
the regular save endpoint.

### Assigning the role

Grant `:images-manager` from the admin role UI. Required context:

- `city-code` — e.g. `430` for Loimaa.

Optional:

- `type-code` — further narrow to specific sports-site types.

## Public API

Image metadata is exposed in Public API **v2** under `document.images` for
each sports site. It is **not** backfilled into v1 (v1 is frozen for this
change — no schema additions after the v2 cutover).

OpenAPI spec (auto-generated): `/v2/openapi.json`.

## UI

In the sports-site edit view, a tab labeled *"Kuvalinkit" / "Bildlänkar" /
"Image links"* appears when either:

- the user has `:site/edit-images` for the site, OR
- the site already has at least one image link (so anyone can view them).

The tab shows a table of existing images with inline preview on row hover.
Add/edit opens a dialog for URL, alt-text, copyright and caption. The dialog
displays the CC BY 4.0 notice and a live preview of the URL if it loads.

## Rollout notes

- The Elasticsearch mapping for `:images` is `{:enabled false}`; values
  round-trip through `_source` but are not indexed for search. If at some
  point you want to search over image alt-text or caption, this will need a
  mapping change and a reindex.
- Adding `:images` to the sports-site ES mapping is **not backwards
  compatible with a pre-existing strict index**; existing deployments need
  the index recreated or the mapping updated before deployment so that saves
  with `images` populated are accepted.
