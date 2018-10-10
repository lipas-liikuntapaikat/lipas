(ns lipas.i18n.en)

(def translations
  {:menu
   {:headline      "LIPAS"
    :headline-test "LIPAS-TEST"
    :main-menu     "Main menu"
    :jyu           "University of Jyväskylä"
    :frontpage     "Home"}

   :restricted
   {:login-or-register "Please login or register"}

   :home-page
   {:headline    "Front page"
    :description "LIPAS system has information on sport facilities,
    routes and recreational areas and economy. The content is open
    data under the CC4.0 International licence."}

   :sport
   {:headline          "Sports sites"
    :description       "LIPAS is the national database of sport facilities
    and their conditions in Finland."
    :legacy-disclaimer "Sports sites are not here yet but they can be
    accessed through existing LIPAS by clicking the link below."

    :up-to-date-information "Up-to-date information about sports facilities"
    :updating-tools         "Tools for data maintainers"
    :open-interfaces        "Open data and APIs"}

   :lipas.sports-site
   {:headline          "Sports sites"
    :id                "LIPAS-ID"
    :name              "Official Name"
    :marketing-name    "Marketing name"
    :owner             "Owner"
    :admin             "Admin"
    :type              "Type"
    :construction-year "Construction year"
    :renovation-years  "Renovation years"
    :phone-number      "Phone number"
    :www               "Web-site"
    :email-public      "Email (public)"
    :comment           "More information"}

   :type
   {:type-code "Type code"
    :name      "Type"}

   :lipas.location
   {:headline      "Location"
    :address       "Address"
    :postal-code   "Postal code"
    :postal-office "Postal office"
    :city          "City"
    :city-code     "City code"
    :neighborhood  "Neighborhood"}

   :reports
   {:headline "Contacts report"
    :contacts "Contacts"}

   :ice
   {:headline    "Ice stadiums"
    :description "Ice stadiums portal contains data about ice stadiums
    energy consumption and related factors."

    :basic-data-of-halls  "General information about building and facilities"
    :entering-energy-data "Reporing energy consumption"
    :updating-basic-data  "Updating general information"

    :size-category "Size category"
    :comparison    "Compare venues"
    :small         "Small competition hall > 500 persons"
    :competition   "Competition hall < 3000 persons"
    :large         "Grand hall > 3000 persons"}

   :ice-rinks
   {:headline "Venue details"}

   :ice-energy
   {:headline       "Energy Info"
    :description    "Up-to-date information can be found from Finhockey
    association web-site."
    :finhockey-link "Browse to Finhockey web-site"}

   :ice-comparison
   {:headline "Compare"}

   :ice-form
   {:headline       "Report readings"
    :headline-short "Report readings"
    :select-rink    "Select stadium"}

   :lipas.energy-consumption
   {:headline                  "Energy consumption"
    :headline-year             "Energy consumption in {1}"
    :electricity               "Electricity MWh"
    :heat                      "Heat (acquired) MWh"
    :cold                      "Cold energy (acquired) MWh"
    :water                     "Water m³"
    :yearly                    "Yearly"
    :monthly                   "Monthly"
    :monthly?                  "I want to report monthly energy consumption"
    :reported-for-year         "Energy consumption reported for {1}"
    :report                    "Report readings"
    :contains-other-buildings? "Readings contain also other buildings or spaces"
    :operating-hours           "Operating hours"
    :monthly-readings-in-year  "Monthly energy consumption in {1}"
    :not-reported-monthly      "No monthly data available"}

   :lipas.energy-stats
   {:headline            "Energy consumption in {1}"
    :disclaimer          "*Based on reported consumption in {1}"
    :energy-mwh          "Energy MWh"
    :electricity-mwh     "Electricity MWh"
    :heat-mwh            "Heat MWh"
    :water-m3            "Water m³"
    :hall-missing?       "Is your data missing from the diagram?"
    :report              "Report consumption"
    :energy-reported-for "Electricity, heat and water consumption reported for {1}"}

   :lipas.visitors
   {:headline                 "Visitors"
    :spectators-count         "Spectators count"
    :total-count              "Visitors count"
    :monthly-visitors-in-year "Monthly visitors in {1}"
    :not-reported-monthly     "No monthly data"}

   :lipas.swimming-pool.conditions
   {:headline          "Open hours"
    :daily-open-hours  "Daily open hours"
    :open-days-in-year "Open days in year"
    :open-hours-mon    "Mondays"
    :open-hours-tue    "Tuesdays"
    :open-hours-wed    "Wednesdays"
    :open-hours-thu    "Thursdays"
    :open-hours-fri    "Fridays"
    :open-hours-sat    "Saturdays"
    :open-hours-sun    "Sundays"}

   :lipas.swimming-pool.energy-saving
   {:headline                    "Energy saving"
    :shower-water-heat-recovery? "Shower water heat recovery?"
    :filter-rinse-water-heat-recovery?
    "Filter rinse water heat recovery?"}

   :swim
   {:headline       "Swimming pools"
    :headline-split "Swimming pools"
    :list           "Pools list"
    :visualizations "Comparison"
    :edit           "Report consumption"
    :description    "Swimming pools portal contains data about indoor
    swimming pools energy consumption and related factors."

    :basic-data-of-halls  "General information about building and facilities"
    :entering-energy-data "Reporing energy consumption"
    :updating-basic-data  "Updating general information"

    :latest-updates "Latest updates"}

   :swim-energy
   {:headline       "Energy info"
    :headline-short "Info"
    :description    "Up-to-date information can be found from UKTY and
    SUH web-sites."
    :ukty-link      "Browse to UKTY web-site"
    :suh-link       "Browse to SUH web-site"}

   :open-data
   {:headline "Open Data"
    :rest     "REST"
    :wms-wfs  "WMS & WFS"}

   :partners
   {:headline "In association with"}

   :help
   {:headline "Help"}

   :user
   {:headline            "Profile"
    :greeting            "Hello {1} {2}!"
    :front-page-link     "front page"
    :admin-page-link     "Admin page"
    :ice-stadiums-link   "Ice stadiums"
    :swimming-pools-link "Swimming pools"}

   :lipas.admin
   {:headline "Admin"
    :users    "Users"}

   :lipas.user
   {:contact-info               "Contact info"
    :email                      "Email"
    :email-example              "email@example.com"
    :username                   "Username"
    :username-example           "tane12"
    :firstname                  "First name"
    :lastname                   "Last name"
    :password                   "Password"
    :permissions                "Permissions"
    :permissions-example        "Access to update Jyväskylä ice stadiums."
    :permissions-help           "Describe what permissions you wish to have"
    :requested-permissions      "Requested permissions"
    :sports-sites               "My sites"
    :no-permissions             "You don't have permission to publish changes to
    any sites."
    :draft-encouragement        "However, you can save drafts to be approved
    by administrators."
    :view-basic-info            "View basic info"
    :report-energy-consumption  "Report energy consumption"
    :report-energy-and-visitors "Report visitors and energy consumption"}

   :lipas.user.permissions
   {:admin?       "Admin"
    :draft?       "Make drafts"
    :sports-sites "Access to sports sites"
    :types        "Access to sports sites of type"
    :cities       "Access to sports sites in cities"}

   :register
   {:headline "Register"}

   :login
   {:headline         "Login"
    :username         "Email / Username"
    :username-example "paavo.paivittaja@kunta.fi"
    :password         "Password"
    :login            "Login"
    :logout           "Log out"
    :bad-credentials  "Wrong username or password"
    :forgot-password? "Forgot password?"}

   :reset-password
   {:headline             "Forgot password?"
    :change-password      "Change password"
    :helper-text          "We will email password reset link to you."
    :reset-link-sent      "Reset link sent! Please check your email!"
    :enter-new-password   "Enter new password"
    :password-helper-text "Password must be at least 6 characters long"
    :reset-success        "Password has been reset! Please login with the new password."
    :get-new-link         "Get new reset link"}

   :lipas.building
   {:headline                    "Building"
    :main-designers              "Main designers"
    :total-surface-area-m2       "Area m²"
    :total-volume-m3             "Volume m³"
    :total-pool-room-area-m2     "Pool room area m²"
    :total-water-area-m2         "Water surface area m²"
    :total-ice-area-m2           "Ice surface area m²"
    :heat-sections?              "Pool room is divided to heat sections?"
    :piled?                      "Piled"
    :heat-source                 "Heat source"
    :main-construction-materials "Main construction materials"
    :supporting-structures       "Supporting structures"
    :ceiling-structures          "Ceiling structures"
    :staff-count                 "Staff count"
    :seating-capacity            "Seating capacity"}

   :lipas.ice-stadium.envelope
   {:headline                "Envelope structure"
    :base-floor-structure    "Base floor structure"
    :insulated-exterior?     "Insulated exterior"
    :insulated-ceiling?      "Insulated ceiling"
    :low-emissivity-coating? "Low emissivity coating"}

   :lipas.ice-stadium.rinks
   {:headline  "Rinks"
    :edit-rink "Edit rink"
    :add-rink  "Add rink"}

   ::lipas.ice-stadium.refrigeration
   {:headline                       "Refrigeration"
    :original?                      "Original"
    :individual-metering?           "Individual metering"
    :power-kw                       "Power (kW)"
    :condensate-energy-recycling?   "Condensate energy recycling"
    :condensate-energy-main-targets "Condensate energy main target"
    :refrigerant                    "Refrigerant"
    :refrigerant-amount-kg          "Refrigerant amount (kg)"
    :refrigerant-solution           "Refrigerant solution"
    :refrigerant-solution-amount-l  "Refrigerant solution amount (l)"}

   :lipas.ice-stadium.conditions
   {:headline                        "Conditions"
    :daily-open-hours                "Daily open hours"
    :open-months                     "Open months in year"
    :air-humidity-min                "Air humidity % min"
    :air-humidity-max                "Air humidity % max"
    :ice-surface-temperature-c       "Ice surface temperature"
    :skating-area-temperature-c      "Skating area temperature (at 1m height)"
    :stand-temperature-c             "Stand temperature (during match)"
    :daily-maintenances-week-days    "Daily maintenances on weekdays"
    :daily-maintenances-weekends     "Daily maintenances on weekends"
    :weekly-maintenances             "Weekly maintenances"
    :average-water-consumption-l     "Average water consumption (l) / maintenance"
    :maintenance-water-temperature-c "Maintenance water temperature"
    :ice-resurfacer-fuel             "Ice resurfacer fuel"
    :ice-average-thickness-mm        "Average ice thickness (mm)"}

   :lipas.ice-stadium.ventilation
   {:headline                 "Ventilation"
    :heat-recovery-type       "Heat recovery type"
    :heat-recovery-efficiency "Heat recovery efficiency"
    :dryer-type               "Dryer type"
    :dryer-duty-type          "Dryer duty type"
    :heat-pump-type           "Heat pump type"}

   :lipas.swimming-pool.water-treatment
   {:headline          "Water treatment"
    :ozonation?        "Ozonation"
    :uv-treatment?     "UV-treatment"
    :activated-carbon? "Activated carbon"
    :filtering-methods "Filtering methods"}

   :lipas.swimming-pool.pool
   {:outdoor-pool? "Outdoor pool"}

   :lipas.swimming-pool.pools
   {:headline  "Pools"
    :add-pool  "Add pool"
    :edit-pool "Edit pool"
    :structure "Structure"}

   :lipas.swimming-pool.slides
   {:headline   "Slides"
    :add-slide  "Add Slide"
    :edit-slide "Edit slide"}

   :lipas.swimming-pool.saunas
   {:headline    "Saunas"
    :add-sauna   "Add sauna"
    :edit-sauna  "Edit sauna"
    :women?      "Women"
    :men?        "Men"
    :accessible? "Accessible"}

   :lipas.swimming-pool.facilities
   {:headline                       "Other services"
    :platforms-1m-count             "1m platforms count"
    :platforms-3m-count             "3m platforms count"
    :platforms-5m-count             "5m platforms count"
    :platforms-7.5m-count           "7.5m platforms count"
    :platforms-10m-count            "10m platforms count"
    :hydro-massage-spots-count      "Other hydro massage spots count"
    :hydro-neck-massage-spots-count "Neck hydro massage spots count"
    :kiosk?                         "Kiosk / cafeteria"
    :gym?                           "Gym"
    :showers-men-count              "Mens showers count"
    :showers-women-count            "Womens showers count"
    :showers-unisex-count           "Unisex showers count"
    :lockers-men-count              "Mens lockers count"
    :lockers-women-count            "Womens lockers count"
    :lockers-unisex-count           "Unisex lockers count"}

   :dimensions
   {:volume-m3       "Volume m³"
    :area-m2         "Area m²"
    :surface-area-m2 "Surface area m²"
    :length-m        "Length m"
    :width-m         "Width m"
    :depth-min-m     "Depth min m"
    :depth-max-m     "Depth max m"}

   :units
   {:times-per-day  "times a day"
    :times-per-week "times a wekk"
    :hours-per-day  "hours a day"
    :days-in-year   "days a year"
    :person         "person"
    :pcs            "pcs"
    :percent        "%"}

   :physical-units
   {:temperature-c "Temperature °C"
    :mwh           "MWh"
    :m             "m"
    :mm            "mm"
    :l             "l"
    :m2            "m²"
    :m3            "m³"
    :celsius       "°C"
    :hour          "h"}

   :month
   {:jan "January"
    :feb "February"
    :mar "March"
    :apr "April"
    :may "May"
    :jun "June"
    :jul "July"
    :aug "August"
    :sep "September"
    :oct "October"
    :nov "November"
    :dec "December"}

   :time
   {:year               "Year"
    :hour               "Hour"
    :month              "Month"
    :start              "Started"
    :end                "Ended"
    :date               "Date"
    :just-a-moment-ago  "Just a moment ago"
    :less-than-hour-ago "Less then hour ago"
    :today              "Today"
    :yesterday          "Yesterday"
    :this-week          "This week"
    :this-month         "This month"
    :this-year          "This year"
    :last-year          "Last year"
    :two-years-ago      "2 years ago"
    :three-years-ago    "3 years ago"
    :long-time-ago      "Long time ago"}

   :duration
   {:hour  "hours"
    :month "months"}

   :actions
   {:add               "Add"
    :edit              "Edit"
    :save              "Save"
    :save-draft        "Save draft"
    :delete            "Delete"
    :discard           "Discard"
    :cancel            "Cancel"
    :close             "Close"
    :select-hall       "Select hall"
    :select-year       "Select year"
    :show-all-years    "Show all years"
    :show-account-menu "Open account menu"
    :open-main-menu    "Open main menu"
    :submit            "Submit"
    :download          "Download"
    :browse-to-portal  "Enter portal"
    :back-to-listing   "Back to list view"}

   :confirm
   {:headline              "Confirmation"
    :no                    "No"
    :yes                   "Yes"
    :discard-changes?      "Do you want to discard all changes?"
    :press-again-to-delete "Press again to delete"}

   :search
   {:headline "Search"}

   :statuses
   {:edited "{1} (edited)"}

   :general
   {:name         "Name"
    :type         "Type"
    :description  "Description"
    :general-info "General information"
    :comment      "Comment"
    :structure    "Structure"
    :hall         "Hall"
    :updated      "Updated"
    :reported     "Reported"}

   :notifications
   {:save-success "Saving succeeded"
    :save-failed  "Saving failed"
    :get-failed   "Couldn't get data"}

   :disclaimer
   {:headline     "NOTICE!"
    :test-version "This is LIPAS TEST-ENVIRONMENT. Changes made here
    don't affect the production system."}

   :error
   {:unknown             "Unknonwn error occurred. :/"
    :user-not-found      "User not found"
    :email-not-found     "Email address is not registered"
    :reset-token-expired "Password reset failed. Link has expired."
    :invalid-form        "Fix fields marked with red"}})
