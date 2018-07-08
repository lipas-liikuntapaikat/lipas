(comment
  (e/connect-driver remote-driver)

  (e/go remote-driver "http://localhost:3449")
  (e/wait-visible {:id "account-btn"})

  (e/doto-wait 1 remote-driver
    (e/click  {:id "account-btn"})
    (e/click  {:id "account-menu-item-login"})
    (e/fill   {:id "login-username-input"} "jhdemo")
    (e/fill   {:id "login-password-input"} "jaahalli")
    (e/click  {:id "login-submit-btn"})
    (e/get-title)))

(comment
  (e/fill driver {:id "lst-ib"} "kissa" k/enter)
  (e/submit driver {:tag :input :name "btnK"})
  (e/click driver {:fn/has-text "Images"})
  (e/go driver "http://lipas.cc.jyu.fi"))

(comment
  (def driver-2 (e/create-driver :chrome-headless {:port 4444}))
  @driver-2
  (e/connect-driver driver-2)
  (e/go driver-2 "http://www.google.fi")
  (e/fill driver-2 {:id "lst-ib"} "kissa" k/enter)
  (e/click driver-2 {:fn/has-text "Images"})
  (e/get-title driver-2)
  (e/screenshot driver-2 "kissa.png")
  (e/disconnect-driver driver-2))
