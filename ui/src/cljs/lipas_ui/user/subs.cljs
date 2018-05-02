(ns lipas-ui.user.subs
  (:require [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::logged-in?
 (fn [db _]
   (:logged-in? db)))

(re-frame/reg-sub
 ::user-data
 (fn [db _]
   (-> db :user :login)))
