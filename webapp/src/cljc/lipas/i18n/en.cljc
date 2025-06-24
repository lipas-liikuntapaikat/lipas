(ns lipas.i18n.en)

(def translations
  {:loi
   {:category "Category"
    :status "Status"
    :type "Object Type"
    :type-and-category-disclaimer "Category and type must be selected before adding to the map"}
   :utp
   {:read-only-disclaimer "There is currently only an editing view for activities. Log in and go to the editing mode from the pen symbol."
    :add-contact "Add contact information"
    :unit "unit"
    :highlight "Highlight"
    :add-highlight "Add highlight"
    :photo "Photograph"
    :add-photo "Add photograph"
    :video "Video"
    :add-video "Add video"
    :link "Link"
    :length-km "Length km"
    :add-subroute "Add sub-route"
    :delete-route-prompt "Are you sure you want to delete this route?"
    :custom-rule "Custom permit, regulation, instruction"
    :custom-rules "Custom instructions"
    :add-subroute-ok "OK"
    :route-is-made-of-subroutes "The route consists of multiple separate sections"
    :select-route-parts-on-map "Select route parts on the map"
    :finish-route-details "Route completed"}
   :analysis
   {:headline "Analysis Tool (beta)"
    :description "The analysis tool can be used to evaluate the supply and accessibility of physical activity conditions by comparing the distance and travel times to physical activity facilities in relation to other facilities, population, and educational institutions."
    :results "Results"
    :mean "Mean"
    :population-weighted-mean "Population-weighted diversity index"
    :median "Median"
    :mode "Mode"
    :reachability "Reachability"
    :categories "Categories"
    :diversity "Diversity"
    :heatmap "Heatmap"
    :diversity-grid "Result grid"
    :diversity-help1 "The diversity tool can be used to evaluate and compare the diversity of physical activity conditions in the residents' local environment at the grid and area level. The diversity index calculated by the diversity tool describes how diversely different physical activity opportunities the resident can reach within the selected distance along the road and path network (assuming 800 m). The higher the index value, the more diverse physical activity facilities are available in the residents' local environment."
    :diversity-help2 "The tool uses a postal code-based area division by default. The division can also be made based on another existing area division by importing the desired geometry file (Shapefile, GeoJSON or KML)."
    :diversity-help3 "The calculation is done per 250 x 250 m population grid from Statistics Finland. The area-level results indicate the average diversity of physical activity conditions in the residents' local environments (population-weighted average of the diversity index). The calculation distance of the diversity tool is based on the OpenStreetMap road network data and the OSRM tool."
    :analysis-areas "Analysis areas"
    :categories-help "Physical activity facility types belonging to the same category affect the diversity index only once."
    :description2 "The population data used is from Statistics Finland's 250x250m and 1x1km grid data, which shows the population distribution in three age groups (0-14, 15-65, 65-) in each grid."
    :description3 "The calculation of travel times by different modes of transport (walking, cycling, car) is based on open OpenStreetMap data and the OSRM tool."
    :description4 "The name and location data of educational institutions is based on open data from Statistics Finland. The name and location data of early childhood education units is based on data collected and provided by LIKES."
    :add-new "Add new"
    :distance "Distance"
    :travel-time "Travel time"
    :zones "Zones"
    :zone "Zone"
    :schools "Schools"
    :daycare "Early childhood education unit"
    :elementary-school "Elementary school"
    :high-school "High school"
    :elementary-and-high-school "Elementary and high school"
    :special-school "Special school"
    :population "Population"
    :direct "As the crow flies"
    :by-foot "On foot"
    :by-car "By car"
    :by-bicycle "By bicycle"
    :analysis-buffer "Analysis area"
    :filter-types "Filter by type"
    :settings "Settings"
    :settings-map "Objects shown on the map"
    :settings-zones "Distances and travel times"
    :settings-help "The analysis area is determined by the largest distance zone. Travel times are also not calculated outside this area."}

   :sport
   {:description
    "LIPAS is the national database of sports facilities and their conditions in Finland.",
    :headline "Sports Facilities",
    :open-interfaces "Open data and APIs",
    :up-to-date-information
    "Up-to-date information about sports facilities",
    :updating-tools "Tools for data maintainers"
    :analysis-tools "Analysis tools"},
   :confirm
   {:discard-changes? "Do you want to discard all changes?",
    :headline "Confirmation",
    :no "No",
    :delete-confirm "Are you sure you want to delete the row?"
    :resurrect? "Are you sure you want to resurrect this sports facility?",
    :save-basic-data? "Do you want to save general information?",
    :yes "Yes"},
   :lipas.swimming-pool.saunas
   {:accessible? "Accessible",
    :add-sauna "Add sauna",
    :edit-sauna "Edit sauna",
    :headline "Saunas",
    :men? "Men",
    :women? "Women"},
   :slide-structures
   {:concrete "Concrete",
    :hardened-plastic "Hardened plastic",
    :steel "Steel"},
   :lipas.swimming-pool.conditions
   {:open-days-in-year "Open days in year",
    :open-hours-mon "Mondays",
    :headline "Open hours",
    :open-hours-wed "Wednesdays",
    :open-hours-thu "Thursdays",
    :open-hours-fri "Fridays",
    :open-hours-tue "Tuesdays",
    :open-hours-sun "Sundays",
    :daily-open-hours "Daily open hours",
    :open-hours-sat "Saturdays"},
   :lipas.ice-stadium.ventilation
   {:dryer-duty-type "Dryer duty type",
    :dryer-type "Dryer type",
    :headline "Ventilation",
    :heat-pump-type "Heat pump type",
    :heat-recovery-efficiency "Heat recovery efficiency",
    :heat-recovery-type "Heat recovery type"},
   :swim
   {:description
    "Swimming pools portal contains data about indoor  swimming pools energy consumption and related factors.",
    :latest-updates "Latest updates",
    :headline "Swimming pools",
    :basic-data-of-halls
    "General information about building and facilities",
    :updating-basic-data "Updating general information",
    :edit "Report consumption",
    :entering-energy-data "Reporing energy consumption",
    :list "Pools list",
    :visualizations "Comparison"},
   :home-page
   {:description
    "LIPAS system has information on sport facilities, routes and recreational areas and economy. The content is open  data under the CC4.0 International licence.",
    :headline "Front page"},
   :ice-energy
   {:description
    "Up-to-date information can be found from Finhockey association web-site.",
    :energy-calculator "Ice stadium energy concumption calculator",
    :finhockey-link "Browse to Finhockey web-site",
    :headline "Energy Info"},
   :filtering-methods
   {:activated-carbon "Activated carbon",
    :coal "Coal",
    :membrane-filtration "Membrane filtration",
    :multi-layer-filtering "Multi-layer filtering",
    :open-sand "Open sand",
    :other "Other",
    :precipitation "Precipitation",
    :pressure-sand "Pressure sand"},
   :open-data
   {:description "Interface links and instructions",
    :headline "Open Data",
    :rest "REST",
    :wms-wfs "WMS & WFS"},
   :lipas.swimming-pool.pool
   {:accessibility "Accessibility", :outdoor-pool? "Outdoor pool"},
   :pool-types
   {:multipurpose-pool "Multi-purpose pool",
    :whirlpool-bath "Whirlpool bath",
    :main-pool "Main pool",
    :therapy-pool "Therapy pool",
    :fitness-pool "Fitness pool",
    :diving-pool "Diving pool",
    :childrens-pool "Childrens pool",
    :paddling-pool "Paddling pool",
    :cold-pool "Cold pool",
    :other-pool "Other pool",
    :teaching-pool "Teaching pool"},
   :lipas.ice-stadium.envelope
   {:base-floor-structure "Base floor structure",
    :headline "Envelope structure",
    :insulated-ceiling? "Insulated ceiling",
    :insulated-exterior? "Insulated exterior",
    :low-emissivity-coating? "Low emissivity coating"},
   :disclaimer
   {:headline "NOTICE!",
    :test-version
    "This is LIPAS TEST-ENVIRONMENT. Changes made here  don't affect the production system."
    :data-ownership "The LIPAS geographic information system's data repository is owned and managed by the University of Jyväskylä. The University of Jyväskylä reserves the rights to all content, information, and material added to the system and created during the system's development. The user who adds material to the system is responsible for ensuring that the material is accurate and does not infringe on third-party copyrights. The University of Jyväskylä is not liable for claims made by third parties regarding the material. By adding data to the LIPAS geographic information system, the user is considered to have accepted the use of the added material in the system as part of the data repository. The data stored in the LIPAS database is open and freely available under the CC 4.0 Attribution license."},
   :admin
   {:private-foundation "Private / foundation",
    :city-other "City / other",
    :unknown "Unknown",
    :municipal-consortium "Municipal consortium",
    :private-company "Private / company",
    :private-association "Private / association",
    :other "Other",
    :city-technical-services "City / technical services",
    :city-sports "City / sports",
    :state "State",
    :city-education "City / education"},
   :accessibility
   {:lift "Pool lift",
    :low-rise-stairs "Low rise stairs",
    :mobile-lift "Mobile pool lift",
    :slope "Slope"},
   :general
   {:headline "Headline"
    :description "Description",
    :hall "Hall",
    :women "Women",
    :age-anonymized "Age anonymized"
    :total-short "Total",
    :done "Done",
    :updated "Updated",
    :name "Name",
    :reported "Reported",
    :type "Type",
    :last-modified "Last modified",
    :here "here",
    :event "Event",
    :structure "Structure",
    :general-info "General information",
    :comment "Comment",
    :measures "Measures",
    :men "Men"
    :more "More"
    :less "Less"},
   :dryer-duty-types {:automatic "Automatic", :manual "Manual"},
   :swim-energy
   {:description
    "Up-to-date information can be found from UKTY and  SUH web-sites.",
    :headline "Energy info",
    :headline-short "Info",
    :suh-link "Browse to SUH web-site",
    :ukty-link "Browse to UKTY web-site"},
   :time
   {:two-years-ago "2 years ago",
    :date "Date",
    :hour "Hour",
    :this-month "This month",
    :time "Time",
    :less-than-hour-ago "Less than an hour ago",
    :start "Started",
    :this-week "This week",
    :today "Today",
    :month "Month",
    :long-time-ago "Long time ago",
    :year "Year",
    :just-a-moment-ago "Just a moment ago",
    :yesterday "Yesterday",
    :three-years-ago "3 years ago",
    :end "Ended",
    :this-year "This year",
    :last-year "Last year"},
   :ice-resurfacer-fuels
   {:LPG "LPG",
    :electicity "Electricity",
    :gasoline "Gasoline",
    :natural-gas "Natural gas",
    :propane "Propane"},
   :ice-rinks {:headline "Venue details"},
   :month
   {:sep "September",
    :jan "January",
    :jun "June",
    :oct "October",
    :jul "July",
    :apr "April",
    :feb "February",
    :nov "November",
    :may "May",
    :mar "March",
    :dec "December",
    :aug "August"},
   :type
   {:name "Type",
    :main-category "Main category",
    :sub-category "Sub category",
    :type-code "Type code"},
   :duration
   {:hour "hours", :month "months", :years "years", :years-short "y"},
   :size-categories
   {:competition "Competition < 3000 persons",
    :large "Large > 3000 persons",
    :small "Small > 500 persons"},
   :lipas.admin
   {:access-all-sites "You have admin permissions.",
    :confirm-magic-link
    "Are you sure you want to send magic link to {1}?",
    :headline "Admin",
    :magic-link "Magic Link",
    :select-magic-link-template "Select letter",
    :send-magic-link "Send magic link to {1}",
    :users "Users"},
   :lipas.ice-stadium.refrigeration
   {:headline "Refrigeration",
    :refrigerant-solution-amount-l "Refrigerant solution amount (l)",
    :individual-metering? "Individual metering",
    :original? "Original",
    :refrigerant "Refrigerant",
    :refrigerant-solution "Refrigerant solution",
    :condensate-energy-main-targets "Condensate energy main target",
    :power-kw "Power (kW)",
    :condensate-energy-recycling? "Condensate energy recycling",
    :refrigerant-amount-kg "Refrigerant amount (kg)"},
   :lipas.building
   {:headline "Building",
    :total-volume-m3 "Volume m³",
    :staff-count "Staff count",
    :piled? "Piled",
    :heat-sections? "Pool room is divided to heat sections?",
    :total-ice-area-m2 "Ice surface area m²",
    :main-construction-materials "Main construction materials",
    :main-designers "Main designers",
    :total-pool-room-area-m2 "Pool room area m²",
    :heat-source "Heat source",
    :total-surface-area-m2 "Area m²",
    :total-water-area-m2 "Water surface area m²",
    :ceiling-structures "Ceiling structures",
    :supporting-structures "Supporting structures",
    :seating-capacity "Seating capacity"},
   :heat-pump-types
   {:air-source "Air source heat pump",
    :air-water-source "Air-water source heat pump",
    :exhaust-air-source "Exhaust air source heat pump",
    :ground-source "Ground source heat pump",
    :none "None"},
   :search
   {:table-view "Table view",
    :headline "Search",
    :results-count "{1} results",
    :placeholder "Search...",
    :retkikartta-filter "Retkikartta.fi",
    :filters "Filters",
    :search-more "Search more...",
    :page-size "Page size",
    :search "Search",
    :permissions-filter "Show only sites that I can edit",
    :display-closest-first "Display nearest first",
    :list-view "List view",
    :pagination "Results {1}-{2}",
    :school-use-filter "Used by schools",
    :clear-filters "Clear filters"},
   :map.tools
   {:download-backup-tooltip "Download backup"
    :drawing-tooltip "Drawing tool selected",
    :drawing-hole-tooltip "Hole drawing tool selected",
    :edit-tool "Edit tool",
    :importing-tooltip "Import tool selected",
    :deleting-tooltip "Delete tool selected",
    :splitting-tooltip "Split tool selected",
    :simplifying "Simplify tool selected",
    :selecting "Select tool selected",
    :simplify "Simplify",
    :travel-direction-tooltip "Travel direction tool selected"
    :route-part-difficulty-tooltip "Route section difficulty tool selected"},
   :map.tools.simplify
   {:headline "Simplify geometries"}
   :partners {:headline "In association with"},
   :actions
   {:duplicate "Duplicate",
    :resurrect "Resurrect",
    :select-year "Select year",
    :select-owners "Select owners",
    :select-admins "Select administrators",
    :select-tool "Select tool",
    :save-draft "Save draft",
    :redo "Redo",
    :open-main-menu "Open main menu",
    :back-to-listing "Back to list view",
    :filter-surface-materials "Filter surface materials",
    :browse "Browse",
    :select-type "Select types",
    :edit "Edit",
    :filter-construction-year "Filter construction years",
    :submit "Submit",
    :choose-energy "Choose energy",
    :delete "Delete",
    :browse-to-map "Go to map view",
    :save "Save",
    :close "Close",
    :filter-area-m2 "Filter area m²",
    :show-account-menu "Open account menu",
    :fill-required-fields "Please fill all required fields",
    :undo "Undo",
    :browse-to-portal "Enter portal",
    :download-excel "Download",
    :fill-data "Fill",
    :select-statuses "Status",
    :select-cities "Select cities",
    :select-hint "Select...",
    :discard "Discard",
    :more "More...",
    :cancel "Cancel",
    :select-columns "Select fields",
    :add "Add",
    :show-all-years "Show all years",
    :download "Download",
    :select-hall "Select hall",
    :clear-selections "Clear",
    :select "Select",
    :select-types "Select types"
    :select-all "Select all"},
   :dimensions
   {:area-m2 "Area m²",
    :depth-max-m "Depth max m",
    :depth-min-m "Depth min m",
    :length-m "Length m",
    :surface-area-m2 "Surface area m²",
    :volume-m3 "Volume m³",
    :width-m "Width m"},
   :login
   {:headline "Login",
    :login-here "here",
    :login-with-password "Login with password",
    :password "Password",
    :logout "Log out",
    :username "Email / Username",
    :login-help
    "If you are already a LIPAS-user you can login using just your email address.",
    :login "Login",
    :magic-link-help "Order login link",
    :order-magic-link "Order login link",
    :login-with-magic-link "Login with email",
    :bad-credentials "Wrong username or password",
    :magic-link-ordered "Password",
    :username-example "paavo.paivittaja@kunta.fi",
    :forgot-password? "Forgot password?"},
   :lipas.ice-stadium.conditions
   {:open-months "Open months in year",
    :headline "Conditions",
    :ice-resurfacer-fuel "Ice resurfacer fuel",
    :stand-temperature-c "Stand temperature (during match)",
    :ice-average-thickness-mm "Average ice thickness (mm)",
    :air-humidity-min "Air humidity % min",
    :daily-maintenances-weekends "Daily maintenances on weekends",
    :air-humidity-max "Air humidity % max",
    :daily-maintenances-week-days "Daily maintenances on weekdays",
    :maintenance-water-temperature-c "Maintenance water temperature",
    :ice-surface-temperature-c "Ice surface temperature",
    :weekly-maintenances "Weekly maintenances",
    :skating-area-temperature-c
    "Skating area temperature (at 1m height)",
    :daily-open-hours "Daily open hours",
    :average-water-consumption-l
    "Average water consumption (l) / maintenance"},
   :map.demographics
   {:headline "Analysis tool",
    :tooltip "Analysis tool",
    :helper-text
    "Select sports facility on the map.",
    :copyright1 "Statistics of Finland 2019/2020",
    :copyright2 "1x1km and 250x250m population grids",
    :copyright3 "with license"},
   :lipas.swimming-pool.slides
   {:add-slide "Add Slide",
    :edit-slide "Edit slide",
    :headline "Slides"},
   :notifications
   {:get-failed "Couldn't get data.",
    :save-failed "Saving failed",
    :save-success "Saving succeeded"
    :ie "Internet Explorer is not a supported browser. Please use another web browser, e.g. Chrome, Firefox or Edge."
    :thank-you-for-feedback "Thank you for feedback!"},
   :lipas.swimming-pool.energy-saving
   {:filter-rinse-water-heat-recovery?
    "Filter rinse water heat recovery?",
    :headline "Energy saving",
    :shower-water-heat-recovery? "Shower water heat recovery?"},
   :ice-form
   {:headline "Report readings",
    :headline-short "Report readings",
    :select-rink "Select stadium"},
   :restricted {:login-or-register "Please login or register"},
   :lipas.ice-stadium.rinks
   {:add-rink "Add rink",
    :edit-rink "Edit rink",
    :headline "Rinks"
    :rink1-width "Rink 1 width m"
    :rink2-width "Rink 2 width m"
    :rink3-width "Rink 3 width m"
    :rink1-length "Rink 1 length m"
    :rink2-length "Rink 2 length m"
    :rink3-length "Rink 3 length m"
    :rink1-area-m2 "Rink 1 area m²"
    :rink2-area-m2 "Rink 2 area m²"
    :rink3-area-m2 "Rink 3 area m²"},
   :sports-site.elevation-profile
   {:headline "Elevation Profile",
    :distance-from-start-m "Distance from Start (m)",
    :distance-from-start-km "Distance from Start (km)",
    :height-from-sea-level-m "Height from Sea Level (m)",
    :total-ascend "Total Ascent",
    :total-descend "Total Descent"}
   :lipas.sports-site
   {:properties "Properties",
    :delete-tooltip "Delete sports facility...",
    :headline "Sports facility",
    :new-site-of-type "New {1}",
    :address "Address",
    :new-site "New Sports Facility",
    :phone-number "Phone number",
    :admin "Admin",
    :surface-materials "Surface materials",
    :www "Web-site",
    :name "Finnish name",
    :reservations-link "Reservations",
    :construction-year "Construction year",
    :type "Type",
    :delete "Delete {1}",
    :renovation-years "Renovation years",
    :name-localized-se "Swedish name",
    :name-localized-en "English name"
    :status "Status",
    :id "LIPAS-ID",
    :details-in-portal "Click here to see details",
    :comment "More information",
    :ownership "Ownership",
    :name-short "Name",
    :basic-data "General",
    :delete-reason "Reason",
    :event-date "Modified",
    :email-public "Email (public)",
    :add-new "Add Sports Facility",
    :contact "Contact",
    :owner "Owner",
    :marketing-name "Marketing name"
    :no-permission-tab "You do not have permission to edit the information on this tab"
    :add-new-planning "Add a sports or outdoor site in draft mode"
    :planning-site "Draft"
    :creating-planning-site "You are adding a site in draft mode for analysis tools."},
   :status
   {:active "Active",
    :planned "Planned"
    :incorrect-data "Incorrect data",
    :out-of-service-permanently "Permanently out of service",
    :out-of-service-temporarily "Temporarily out of service"},
   :register
   {:headline "Register",
    :link "Sign up here"
    :thank-you-for-registering
    "Thank you for registering! You wil receive an email once we've updated your permissions."},
   :map.address-search
   {:title "Find address", :tooltip "Find address"},
   :ice-comparison {:headline "Compare"},
   :lipas.visitors
   {:headline "Visitors",
    :monthly-visitors-in-year "Monthly visitors in {1}",
    :not-reported "Visitors not reported",
    :not-reported-monthly "No monthly data",
    :spectators-count "Spectators count",
    :total-count "Visitors count"},
   :lipas.energy-stats
   {:headline "Energy consumption in {1}",
    :energy-reported-for
    "Electricity, heat and water consumption reported for {1}",
    :report "Report consumption",
    :disclaimer "*Based on reported consumption in {1}",
    :reported "Reported {1}",
    :cold-mwh "Cold MWh",
    :hall-missing? "Is your data missing from the diagram?",
    :not-reported "Not reported {1}",
    :water-m3 "Water m³",
    :electricity-mwh "Electricity MWh",
    :heat-mwh "Heat MWh",
    :energy-mwh "Energy MWh"},
   :map.basemap
   {:copyright "© National Land Survey",
    :maastokartta "Terrain",
    :ortokuva "Satellite",
    :taustakartta "Default"
    :transparency "Basemap transparency"},
   :map.overlay
   {:tooltip "Other layers"
    :mml-kiinteisto "Property boundaries"
    :light-traffic "Light traffic"
    :retkikartta-snowmobile-tracks "Metsähallitus snowmobile tracks"}
   :lipas.swimming-pool.pools
   {:add-pool "Add pool",
    :edit-pool "Edit pool",
    :headline "Pools",
    :structure "Structure"},
   :condensate-energy-targets
   {:hall-heating "Hall heating",
    :maintenance-water-heating "Maintenance water heating",
    :other-space-heating "Other heating",
    :service-water-heating "Service water heating",
    :snow-melting "Snow melting",
    :track-heating "Track heating"},
   :refrigerants
   {:CO2 "CO2",
    :R134A "R134A",
    :R22 "R22",
    :R404A "R404A",
    :R407A "R407A",
    :R407C "R407C",
    :R717 "R717"},
   :harrastuspassi {:disclaimer "When the option “May be shown in Harrastuspassi.fi” is ticked, the information regarding the sport facility will be transferred automatically to the Harrastuspassi.fi. I agree to update in the Lipas service any changes to information regarding the sport facility. The site administrator is responsible for the accuracy of information and safety of the location. Facility information is shown in Harrastuspassi.fi only if the municipality has a contract with Harrastuspassi.fi service provider."}
   :retkikartta {:disclaimer "When the option “May be shown in Excursionmap.fi” is ticked, the information regarding the open air exercise location will be transferred once in every 24 hours automatically to the Excursionmap.fi service, maintained by Metsähallitus.
I agree to update in the Lipas service any changes to information regarding an open air exercise location.
The site administrator is responsible for the accuracy of information, safety of the location, responses to feedback and possible costs related to private roads."},
   :reset-password
   {:change-password "Change password",
    :enter-new-password "Enter new password",
    :get-new-link "Get new reset link",
    :headline "Forgot password?",
    :helper-text "We will email password reset link to you.",
    :password-helper-text
    "Password must be at least 6 characters long",
    :reset-link-sent "Reset link sent! Please check your email!",
    :reset-success
    "Password has been reset! Please login with the new password."},
   :reports
   {:contacts "Contacts",
    :download-as-excel "Download Excel",
    :select-fields "Select field",
    :selected-fields "Selected fields",
    :shortcuts "Shortcuts",
    :file-format "Format"
    :tooltip "Create Excel from search results"},
   :heat-sources
   {:district-heating "District heating",
    :private-power-station "Private power station"},
   :map.import
   {:headline "Import geometries",
    :geoJSON
    "Upload .json file containing FeatureCollection. Coordinates must be in WGS84 format.",
    :gpx "Coordinates must be in WGS84 format",
    :supported-formats "Supported formats are {1}",
    :replace-existing? "Replace existing geometries",
    :select-encoding "Select encoding",
    :tab-header "Import",
    :kml "Coordinates must be in WGS84 format",
    :shapefile "Import zip file containing .shp .dbf and .prj file.",
    :import-selected "Import selected",
    :tooltip "Import from file",
    :unknown-format "Unkonwn format '{1}'"
    :unknown-error "An unexpected error occurred. Try with another file."
    :no-geoms-of-type "The provided file does not contain any {1} geometries."

    :coords-not-in-finland-wgs84-bounds "The provided file does not contain coordinates in the WGS84 (EPSG:4326) coordinate system. Check that the source material is in the correct coordinate system."},
   :error
   {:email-conflict "Email is already in use",
    :email-not-found "Email address is not registered",
    :invalid-form "Fix fields marked with red",
    :no-data "No data",
    :reset-token-expired "Password reset failed. Link has expired.",
    :unknown "Unknown error occurred. :/",
    :user-not-found "User not found.",
    :username-conflict "Username is already in use"},
   :reminders
   {:description
    "We will email the message to you at the selected time",
    :after-one-month "After one month",
    :placeholder "Remember to check sports-facility \"{1}\" {2}",
    :select-date "Select date",
    :tomorrow "Tomorrow",
    :title "Add reminder",
    :after-six-months "After six months",
    :in-a-year "In a year",
    :message "Message",
    :in-a-week "In a week"},
   :units
   {:days-in-year "days a year",
    :hours-per-day "hours a day",
    :pcs "pcs",
    :percent "%",
    :person "person",
    :times-per-day "times a day",
    :times-per-week "times a wekk"},
   :lipas.energy-consumption
   {:contains-other-buildings?
    "Readings contain also other buildings or spaces",
    :headline "Energy consumption",
    :yearly "Yearly",
    :report "Report readings",
    :electricity "Electricity MWh",
    :headline-year "Energy consumption in {1}",
    :monthly? "I want to report monthly energy consumption",
    :reported-for-year "Energy consumption reported for {1}",
    :monthly "Monthly",
    :operating-hours "Operating hours",
    :not-reported "Energy consumption not reported",
    :not-reported-monthly "No monthly data available",
    :heat "Heat (acquired) MWh",
    :cold "Cold energy (acquired) MWh",
    :comment "Comment",
    :water "Water m³",
    :monthly-readings-in-year "Monthly energy consumption in {1}"},
   :ice
   {:description
    "Ice stadiums portal contains data about ice stadiums  energy consumption and related factors.",
    :large "Grand hall > 3000 persons",
    :competition "Competition hall < 3000 persons",
    :headline "Ice stadiums",
    :video "Video",
    :comparison "Compare venues",
    :size-category "Size category",
    :basic-data-of-halls
    "General information about building and facilities",
    :updating-basic-data "Updating general information",
    :entering-energy-data "Reporing energy consumption",
    :small "Small competition hall > 500 persons",
    :watch "Watch",
    :video-description
    "Pihjalalinna Areena - An Energy Efficient Ice Stadium"},
   :lipas.location
   {:address "Address",
    :city "City",
    :city-code "City code",
    :headline "Location",
    :neighborhood "Neighborhood",
    :postal-code "Postal code",
    :postal-office "Postal office"},
   :lipas.user.permissions
   {:admin? "Admin",
    :all-cities? "Permission to all cities",
    :all-types? "Permission to all types",
    :cities "Access to sports faclities in cities",
    :sports-sites "Access to sports faclities",
    :types "Access to sports faclities of type"
    :activities "Activities"},
   :lipas.user.permissions.roles
   {:roles "Roles"
    :role "Role"
    :context-value-all "All"
    :role-names {:admin "Admin"
                 :city-manager "City Manager"
                 :type-manager "Type Manager"
                 :site-manager "Site Manager"
                 :activities-manager "UTP Manager"
                 :floorball-manager "Floorball Editor"
                 :analysis-user "Analysis tool user"
                 :ptv "PTV Manager"}
    :context-keys {:city-code "Municipality"
                   :type-code "Type"
                   :activity "Activity"
                   :lipas-id "Site"}
    :edit-role {:edit-header "Edit"
                :new-header "Add Role"
                :stop-editing "Stop Editing"
                :add "Add"
                :choose-role "Choose a role first to select which resources the role affects."}
    :permissions-old "(old, read-only)"}
   :help
   {:headline "Help",
    :permissions-help
    "Please contact us in case you need more permissions",
    :permissions-help-body
    "I need permissions to following sports facilities:",
    :permissions-help-subject "I need more permissions"
    :privacy-policy "Privacy policy"
    :manage-content "Manage help content"},
   :ceiling-structures
   {:concrete "Concrete",
    :double-t-beam "Double-T",
    :glass "Glass",
    :hollow-core-slab "Hollow-core slab",
    :solid-rock "Solid rock",
    :steel "Steel",
    :wood "Wood"},
   :data-users
   {:data-user? "Do you use LIPAS-data?",
    :email-body "Tell us",
    :email-subject "We also use LIPAS-data",
    :headline "Data users",
    :tell-us "Tell us"},
   :lipas.swimming-pool.facilities
   {:showers-men-count "Mens showers count",
    :lockers-men-count "Mens lockers count",
    :headline "Other services",
    :platforms-5m-count "5 m platforms count",
    :kiosk? "Kiosk / cafeteria",
    :hydro-neck-massage-spots-count "Neck hydro massage spots count",
    :lockers-unisex-count "Unisex lockers count",
    :platforms-10m-count "10 m platforms count",
    :hydro-massage-spots-count "Other hydro massage spots count",
    :lockers-women-count "Womens lockers count",
    :platforms-7.5m-count "7.5 m platforms count",
    :gym? "Gym",
    :showers-unisex-count "Unisex showers count",
    :platforms-1m-count "1 m platforms count",
    :showers-women-count "Womens showers count",
    :platforms-3m-count "3 m platforms count"},
   :sauna-types
   {:infrared-sauna "Infrared sauna",
    :other-sauna "Other sauna",
    :sauna "Sauna",
    :steam-sauna "Steam sauna"},
   :stats-metrics
   {:investments "Investments",
    :net-costs "Net costs",
    :operating-expenses "Operating expenses",
    :operating-incomes "Operating incomes",
    :subsidies "Granted subsidies"},
   :refrigerant-solutions
   {:CO2 "CO2",
    :H2ONH3 "H2O/NH3",
    :cacl "CaCl",
    :ethanol-water "Ethanol-water",
    :ethylene-glycol "Ethylene glycol",
    :freezium "Freezium",
    :water-glycol "Water-glycol"},
   :user
   {:admin-page-link "Admin page",
    :promo1-link "Show TEAviisari targets I can update"
    :front-page-link "front page",
    :greeting "Hello {1} {2}!",
    :headline "Profile",
    :ice-stadiums-link "Ice stadiums",
    :promo1-topic "NOTICE! AN UPDATE IN THE CLASSIFICATION SYSTEM OF LIPAS SPORTS FACILITIES (11 January 2022) ",
    :promo1-text "View PDF",
    :swimming-pools-link "Swimming pools"
    :promo-headline "Current News"
    :data-ownership "Terms of use "},
   :building-materials
   {:brick "Brick",
    :concrete "Concrete",
    :solid-rock "Solid rock",
    :steel "Steel",
    :wood "Wood"},
   :stats
   {:disclaimer-headline "Data sources"
    :general-disclaimer-1 "Lipas.fi Finland’s sport venues and places, outdoor routes and recreational areas data is open data under license: Attribution 4.0 International (CC BY 4.0). https://creativecommons.org/licenses/by/4.0/deed.en."
    :general-disclaimer-2 "You are free to use, adapt and share Lipas.fi data in any way, as long as the source is mentioned (Lipas.fi data, University of Jyväskylä, date/year of data upload or relevant information)."
    :general-disclaimer-3 "Note, that Lipas.fi data is updated by municipalities, other owners of sport facilities and Lipas.fi administrators at the University of Jyväskylä, Finland. Data accuracy, uniformity or comparability between municipalities is not guaranteed. In the Lipas.fi statistics, all material is based on the data in Lipas database and possible missing information may affect the results."
    :finance-disclaimer "Data on finances of sport and youth sector in municipalities: Statistics Finland open data. The material was downloaded from Statistics Finland's interface service in 2001-2019 with the license CC BY 4.0. Notice, that municipalities are responsible for updating financial information to Statistics Finland’s database. Data uniformity and data comparability between years or between municipalities is not guaranteed."
    :description
    "Statistics of sports facilities and related municipality finances",
    :filter-types "Filter types",
    :length-km-sum "Total route length km",
    :headline "Statistics",
    :select-years "Select years",
    :browse-to "Go to statistics",
    :select-issuer "Select issuer",
    :select-unit "Select unit",
    :bullet3 "Subsidies",
    :bullet4 "Construction years",
    :finance-stats "Finances",
    :select-city "Select city",
    :area-m2-min "Min area m²",
    :filter-cities "Filter cities",
    :select-metrics "Select metrics",
    :area-m2-count "Area m² reported count",
    :show-ranking "Show ranking",
    :age-structure-stats "Construction years",
    :subsidies-count "Subsidies count",
    :area-m2-sum "Total area m²",
    :select-metric "Select metric",
    :bullet2 "Sports facility statistics",
    :area-m2-max "Max area m²",
    :select-grouping "Grouping",
    :select-city-service "Select city service",
    :region "Region",
    :show-comparison "Show comparison",
    :length-km-avg "Average route length km",
    :sports-sites-count "Total count",
    :length-km-min "Min route length km",
    :country-avg "(country average)",
    :length-km-count "Route length reported count",
    :population "Population",
    :sports-stats "Sports faclities",
    :select-cities "Select cities",
    :subsidies "Subsidies",
    :select-interval "Select interval",
    :bullet1 "Economic Figures of Sport and Youth sector",
    :area-m2-avg "Average area m²",
    :age-structure "Construction years",
    :length-km-max "Max route length km",
    :total-amount-1000e "Total amount (€1000)",
    :city-stats "City statistics"},
   :pool-structures
   {:concrete "Concrete",
    :hardened-plastic "Hardened plastic",
    :steel "Steel"},
   :map
   {:retkikartta-checkbox-reminder
    "Remember to tick \"May be shown in ExcursionMap.fi\" later in sports facility properties.",
    :zoom-to-user "Zoom to my location",
    :remove "Remove",
    :modify-polygon "Modify area",
    :draw-polygon "Add area",
    :retkikartta-problems-warning
    "Please fix problems displayed on the map in case this route should be visible also in Retkikartta.fi",
    :edit-later-hint "You can modify geometries later",
    :center-map-to-site "Center map to sports-facility",
    :draw-hole "Add hole",
    :split-linestring "Split",
    :delete-vertices-hint
    "Vertices can be deleted by pressing alt-key and clicking.",
    :travel-direction "Define travel direction"
    :route-part-difficulty "Set route section difficulty"
    :calculate-route-length "Calculate route length",
    :calculate-area "Calculate area",
    :calculate-count "Calculate count automatically"
    :remove-polygon "Remove area",
    :modify-linestring "Modify route",
    :download-gpx "Download GPX",
    :add-to-map "Add to map",
    :bounding-box-filter "Search from map area",
    :remove-linestring "Remove route",
    :draw-geoms "Draw",
    :confirm-remove
    "Are you sure you want to delete selected geometry?",
    :draw "Draw",
    :draw-linestring "Add route",
    :modify "You can move the point on the map",
    :zoom-to-site "Zoom map to sports facility's location",
    :kink
    "Self intersection. Please fix either by re-routing or splitting the segment.",
    :zoom-closer "Please zoom closer"},
   :supporting-structures
   {:brick "Brick",
    :concrete "Concrete",
    :concrete-beam "Concrete beam",
    :concrete-pillar "Concrete pillar",
    :solid-rock "Solid rock",
    :steel "Steel",
    :wood "Wood"},
   :owner
   {:unknown "Unknown",
    :municipal-consortium "Municipal consortium",
    :other "Other",
    :company-ltd "Company ltd",
    :city "City",
    :state "State",
    :registered-association "Registered association",
    :foundation "Foundation",
    :city-main-owner "City main owner"},
   :menu
   {:frontpage "Home",
    :headline "LIPAS",
    :jyu "University of Jyväskylä",
    :main-menu "Main menu"},
   :dryer-types
   {:cooling-coil "Cooling coil", :munters "Munters", :none "None"},
   :physical-units
   {:mm "mm",
    :hour "h",
    :m3 "m³",
    :m "m",
    :temperature-c "Temperature °C",
    :l "l",
    :mwh "MWh",
    :m2 "m²",
    :celsius "°C"},
   :lipas.swimming-pool.water-treatment
   {:activated-carbon? "Activated carbon",
    :filtering-methods "Filtering methods",
    :headline "Water treatment",
    :ozonation? "Ozonation",
    :uv-treatment? "UV-treatment"},
   :statuses {:edited "{1} (edited)"},
   :lipas.user
   {:email "Email",
    :permissions "Permissions",
    :permissions-example "Access to update Jyväskylä ice stadiums.",
    :saved-searches "Saved searches",
    :report-energy-and-visitors
    "Report visitors and energy consumption",
    :permission-to-cities "You have permission to following cities:",
    :password "Password",
    :lastname "Last name",
    :save-report "Save template",
    :sports-sites "My sites",
    :permission-to-all-cities "You have permission to all cities",
    :username "Username",
    :history "History",
    :saved-reports "Saved templates",
    :contact-info "Contact info",
    :permission-to-all-types "You have permission to all types",
    :requested-permissions "Requested permissions",
    :email-example "email@example.com",
    :permission-to-portal-sites
    "You have permission to following sports facilities:",
    :permissions-help "Describe what permissions you wish to have",
    :permission-to-activities "You have permission to the following activities"
    :report-energy-consumption "Report energy consumption",
    :firstname "First name",
    :save-search "Save search",
    :view-basic-info "View basic info",
    :no-permissions
    "You don't have permission to publish changes to  any sites.",
    :username-example "tane12",
    :permission-to-types "You have permission to following types:"},
   :heat-recovery-types
   {:liquid-circulation "Liquid circulation",
    :plate-heat-exchanger "Plate heat exchanger",
    :thermal-wheel "Thermal wheel"}})
