(ns kulterier.changelog
  (:require [com.biffweb :as biff]
            [kulterier.ui :as ui]
            [kulterier.settings :as settings]
            [markdown-to-hiccup.core :as mth]))

;; Note: only read/process the changelog once per app run.
;; Extremely unlikely for the changelog to shift without an app restart.
(def *changelog
  (delay (let [[_ _ [_ _ h1] & rest] (-> "CHANGELOG.md"
                                         mth/file->hiccup
                                         mth/component)]
           (cons [:h1.text-2xl.font-black.my-4 h1]
                 rest))))

(defn changelog-page
  [& opts]
  (ui/page
   {:base/title (str "Changelog - " settings/app-name)}
   [:section#changelog @*changelog]))

(def module
  {:routes ["/changelog" {:get changelog-page}]})
