(ns lipas.ui.components
  (:require
   [lipas.ui.components.selects :as selects]
   [lipas.ui.components.tables :as tables]
   [lipas.ui.components.buttons :as buttons]
   [lipas.ui.components.autocompletes :as autocompletes]
   [lipas.ui.components.dialogs :as dialogs]
   [lipas.ui.components.text-fields :as text-fields]
   [lipas.ui.components.forms :as forms]
   [lipas.ui.components.checkboxes :as checkboxes]
   [lipas.ui.components.notifications :as notifications]
   [lipas.ui.components.layouts :as layouts]
   [lipas.ui.components.misc :as misc]))

;;; Components ;;;

(def email-button buttons/email-button)
(def download-button buttons/download-button)
(def login-button buttons/login-button)
(def register-button buttons/register-button)
(def edit-button buttons/edit-button)
(def save-button buttons/save-button)
(def publish-button buttons/publish-button)
(def discard-button buttons/discard-button)
(def delete-button buttons/delete-button)
(def confirming-delete-button buttons/confirming-delete-button)
(def text-field text-fields/text-field-controlled)
(def select selects/select)
(def multi-select selects/multi-select)
(def year-selector selects/year-selector)
(def number-selector selects/number-selector)
(def region-selector selects/region-selector)
(def type-selector selects/type-selector)
(def type-selector-single selects/type-selector-single)
(def type-category-selector selects/type-category-selector)
(def city-selector selects/city-selector)
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
(def table-form tables/table-form)
(def info-table tables/info-table)
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
(def edit-actions-list misc/edit-actions-list)
(def li misc/li)
(def sub-heading misc/sub-heading)
