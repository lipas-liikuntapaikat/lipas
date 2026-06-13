(ns lipas.backend.bulk-operations.handler
  "DEPRECATED / removed.

  Bulk contact update is now an org-only operation. Its routes live under
  `POST /actions/org-sites-for-bulk` and `POST /actions/mass-update-org-sites`
  in `lipas.backend.handler`, gated by `:site/create-edit` for the org (admits
  admin + org-editor members). The logic is in
  `lipas.backend.bulk-operations.core`.")
