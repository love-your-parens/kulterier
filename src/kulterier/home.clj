(ns kulterier.home
  (:require [clj-http.client :as http]
            [com.biffweb :as biff]
            [kulterier.middleware :as mid]
            [kulterier.ui :as ui]
            [kulterier.settings :as settings]
            [rum.core :as rum]
            [xtdb.api :as xt]
            [kulterier.scraper :as scraper]))

(defn- filter-events-by-request-params
  [event-data params]
  (let [requested-types (set (map keyword
                                  (let [ts (:event-type params)]
                                    (when ts (if (coll? ts) ts (vector ts))))))]
    (if (empty? requested-types) event-data
        (filter #(requested-types (-> % second :type))
                event-data))))

(defn permanent-events [{:keys [params]}]
  (let [event-data (:permanent (scraper/get-events))]
    [:<>
    (ui/event-tab-list :permanent)
    ;; (ui/event-tab-filters params :permanent event-data)
    (ui/event-tab-panel (ui/permanent-events-table event-data))]))

(defn temporary-events [{:keys [params]}]
  (let [event-data (:temporary (scraper/get-events))]
    [:<>
    (ui/event-tab-list :temporary)
    ;; (ui/event-tab-filters params :temporary event-data)
    (ui/event-tab-panel (ui/temporary-events-table event-data))]))

(defn timetable-events [{:keys [params]}]
  (let [event-data (:timetable (scraper/get-events))
        filtered-data (filter-events-by-request-params event-data params)]
    [:<>
     (ui/event-tab-list :timetable)
     (ui/event-tab-filters params :timetable event-data)
     (ui/event-tab-panel (ui/timetable-events-table filtered-data))]))

(defn home-page [content-uri]
  (fn [{:keys [recaptcha/site-key params] :as ctx}]
    (ui/page
     ctx
     [:header.text-center.mb-6
      (ui/kulterier-logo :width 160 :height 160
                         :class ["m-auto" "text-center"])
      [:div.inline-block
       [:h1 {:class ["text-5xl font-black"]} "Kulterier"]
       [:h2.text-2xl.font-light.lowercase "Na tropie kultury"]]]
     [:section#content {:hx-trigger "load delay:100ms"
                        :hx-get content-uri
                        :hx-target "this"
                        :hx-swap "innerHTML"}
      [:p {:class ["text-center" "text-md" "font-bold" "m-0"]}
       [:img.inline {:src "/img/rings.svg"
                     :width 24
                     :class ["bg-gray-800" "mx-1" "rounded-full"
                             "dark:bg-transparent" "align-middle"]
                     :alt "Animated progress indicator"}]
       [:span.inline-block.animate-bounce.align-middle "Węszę..."]]]
     (ui/footer))))

(def module
  {:routes [["" {:middleware [mid/wrap-redirect-signed-in]}
             ["/" {:get (home-page "/events/temporary")}]
             ["/permanent" {:get (home-page "/events/permanent")}]
             ["/temporary" {:get (home-page "/events/temporary")}]
             ["/timetable" {:get (home-page "/events/timetable")}]]
            ["/events"
             ["/permanent" {:get permanent-events}]
             ["/temporary" {:get temporary-events}]
             ["/timetable" {:get timetable-events}]]]})
