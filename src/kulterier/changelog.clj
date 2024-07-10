(ns kulterier.changelog
  (:require [com.biffweb :as biff]
            [kulterier.ui :as ui]
            [kulterier.settings :as settings]
            [markdown.core :as md]))


;; Note: only read/process the changelog once per app run.
;; Extremely unlikely for the changelog to shift without an app restart.
(def *changelog
  (delay (biff/unsafe
          (md/md-to-html-string
           (slurp "CHANGELOG.md")))))


(defn changelog-page
  [& opts]
  (ui/page
   {:base/title (str "Changelog - " settings/app-name)}
   [:section#changelog @*changelog]))


(def module
  {:routes ["/changelog" {:get changelog-page}]})
