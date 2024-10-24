(ns lipas.ui.components
  "Remove this namespace, it is inconvenient to jump to the
  definitions through an extra ns.
  This also prevents DCE because all the components are
  being referred here."
  (:require [lipas.ui.components.autocompletes :as autocompletes]
            [lipas.ui.components.buttons :as buttons]
            [lipas.ui.components.checkboxes :as checkboxes]
            [lipas.ui.components.dialogs :as dialogs]
            [lipas.ui.components.forms :as forms]
            [lipas.ui.components.layouts :as layouts]
            [lipas.ui.components.misc :as misc]
            [lipas.ui.components.notifications :as notifications]
            [lipas.ui.components.selects :as selects]
            [lipas.ui.components.tables :as tables]
            [lipas.ui.components.text-fields :as text-fields]))

;;; Components ;;;

(def email-button buttons/email-button)
(def download-button buttons/download-button)
(def login-button buttons/login-button)
(def register-button buttons/register-button)
(def save-button buttons/save-button)
(def discard-button buttons/discard-button)
(def locator-button buttons/locator-button)
(def text-field text-fields/text-field-controlled)
(def select selects/select)
(def multi-select selects/multi-select)
(def years-selector selects/years-selector)
(def year-selector selects/year-selector)
(def year-selector2 autocompletes/year-selector)
(def number-selector selects/number-selector)
(def region-selector selects/region-selector)
(def type-selector selects/type-selector)
(def type-selector-single selects/type-selector-single)
(def type-category-selector selects/type-category-selector)
(def city-selector-single selects/city-selector-single)
(def admin-selector selects/admin-selector)
(def admin-selector-single selects/admin-selector-single)
(def owner-selector selects/owner-selector)
(def owner-selector-single selects/owner-selector-single)
(def surface-material-selector selects/surface-material-selector)
(def search-results-column-selector selects/search-results-column-selector)
(def status-selector selects/status-selector)
(def status-selector-single selects/status-selector-single)
(def date-picker selects/date-picker)
(def autocomplete autocompletes/autocomplete)
(def table tables/table)
(def table-v2 tables/table-v2)
(def form-table tables/form-table)
(def dialog dialogs/dialog)
(def full-screen-dialog dialogs/full-screen-dialog)
(def confirmation-dialog dialogs/confirmation-dialog)
(def form forms/form)
(def checkbox checkboxes/checkbox)
(def notification notifications/notification)
(def floating-container layouts/floating-container)
(def form-card layouts/card)
(def expansion-panel layouts/expansion-panel)
(def icon-text misc/icon-text)
(def li misc/li)
(def sub-heading misc/sub-heading)
(def switch checkboxes/switch)
