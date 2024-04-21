(ns kulterier.ui
  (:require [cheshire.core :as cheshire]
            [clojure.java.io :as io]
            [kulterier.settings :as settings]
            [com.biffweb :as biff]
            [ring.middleware.anti-forgery :as csrf]
            [ring.util.response :as ring-response]
            [rum.core :as rum]))

(defn css-path []
  (if-some [last-modified (some-> (io/resource "public/css/main.css")
                                  ring-response/resource-data
                                  :last-modified
                                  (.getTime))]
    (str "/css/main.css?t=" last-modified)
    "/css/main.css"))

(defn js-path []
  (if-some [last-modified (some-> (io/resource "public/js/main.js")
                                  ring-response/resource-data
                                  :last-modified
                                  (.getTime))]
    (str "/js/main.js?t=" last-modified)
    "/js/main.js"))

(defn base [{:keys [::recaptcha] :as ctx} & body]
  (apply
   biff/base-html
   (-> ctx
       (merge #:base{:title settings/app-name
                     :lang "en-US"
                     :icon "/img/glider.png"
                     :description (str settings/app-name " Description")
                     :image "https://clojure.org/images/clojure-logo-120b.png"})
       (update :base/head (fn [head]
                            (concat [[:link {:rel "stylesheet" :href (css-path)}]
                                     [:script {:src (js-path)}]
                                     [:script {:src "https://unpkg.com/htmx.org@1.9.10"}]
                                     [:script {:src "https://unpkg.com/htmx.org/dist/ext/ws.js"}]
                                     [:script {:src "https://unpkg.com/hyperscript.org@0.9.8"}]
                                     (when recaptcha
                                       [:script {:src "https://www.google.com/recaptcha/api.js"
                                                 :async "async" :defer "defer"}])]
                                    head))))
   body))

(defn page [ctx & body]
  (base
   ctx
   [:.flex-grow]
   [:.mx-auto.w-full.md:p-6
    (when (bound? #'csrf/*anti-forgery-token*)
      {:hx-headers (cheshire/generate-string
                    {:x-csrf-token csrf/*anti-forgery-token*})})
    body]
   [:.flex-grow]
   [:.flex-grow]))

(defn on-error [{:keys [status ex] :as ctx}]
  {:status status
   :headers {"content-type" "text/html"}
   :body (rum/render-static-markup
          (page
           ctx
           [:h1.text-lg.font-bold
            (if (= status 404)
              "Page not found."
              "Something went wrong.")]))})

(defn generic-events-table
  [events]
  (let [e events]
    [:table
     [:tbody
      (for [[i d] e]
        (let [top-row [[:td [:a {:href (:url d)}
                             [:img {:src (:thumbnail d)
                                    :class ["min-w-[80px]" "w-[130px]" "max-h-[130px]"
                                            "object-cover" "object-top"]}]]]
                       [:td.p-2 [:a {:href (:url d)} (:name d)]]
                       [:td.p-2 (:place d)]
                       [:td.p-2 (:type d)]
                       [:td.p-2 (:date d)]
                       [:td.p-2 (:date-type d)]]]
          [:<>
           (into [:tr {:class "event-breakdown"
                       :data-event-id i}] top-row)
           [:tr {:class "event-description"
                 :data-event-id i}
            [:td {:colspan (count top-row)
                  :class [".max-h-1"]}
             (:description d)]]]))]
     [:tfoot]]))

(defn text-link
  ([url] (text-link url url))
  ([text url]
   [:a.hover:underline {:href url
                        :title text}
    text]))

(defn title-cell
  ([title url thumbnail]
   [:td {:class ["hidden"
                 "md:table-cell"
                 "md:p-2"
                 "font-bold"
                 "text-lg"
                 "whitespace-nowrap"
                 "text-ellipsis"
                 "overflow-hidden"
                 "max-w-[50vw]"]}
    (when thumbnail
      [:a {:href url}
       [:img {:src thumbnail
              :alt title
              :class ["inline" "min-w-[80px]" "w-[90px]" "max-h-[90px]"
                      "mr-2" "md:mr-4" "object-cover" "object-top"
                      "border-l-2" "pl-0.5" "border-black"]}]])
    (if url (text-link title url) title)]))

(defn title-row
  [colspan title url thumbnail]
  [:tr
   [:td {:colspan colspan
         :class ["md:hidden"
                 "font-bold"
                 "text-lg"
                 "whitespace-nowrap"
                 "text-ellipsis"
                 "overflow-hidden"]}
    (when thumbnail
      [:a {:href url}
       [:img {:src thumbnail
              :alt title
              :class ["inline" "min-w-[80px]" "w-[90px]" "max-h-[90px]"
                      "mr-2" "md:mr-4" "object-cover" "object-top"
                      "border-l-2" "pl-0.5" "border-black"]}]])
    (if url (text-link title url) title)]])

(defn timetable-events-table
  [events]
  (let [e (sort-by (comp :date second) events)]
    [:table {:class ["m-auto" "max-w-[95%]" "md:max-w-[75%]"]}
     [:tbody
      (for [[i d] e]
        (let [summary-row [(title-cell (:name d) (:url d) (:thumbnail d))
                       [:td.text-left.md:p-2 (:place d)]
                       [:td.text-center.md:text-left.md:p-2 (:type d)]
                       [:td.text-right.md:p-2 (when-let [d ^java.time.ZonedDateTime (:date d)]
                                     (.format d (java.time.format.DateTimeFormatter/ofPattern
                                                 "Y/MM/dd HH:mm")))]]]
          [:<>
           (into [:tr {:class "event-breakdown"
                       :data-event-id i}] summary-row)
           (title-row (count summary-row) (:name d) (:url d) (:thumbnail d))
           [:tr {:class "event-description"
                 :data-event-id i}
            (let [description (str (:description d))]
              (when-not (.isBlank description)
                [:td.px-2.pb-3 {:colspan (count summary-row)}
                 [:div.float-left.text-xl.-mt-1.font-mono "↳"]
                 [:p.ml-4.mr-1 description]]))]]))]
     [:tfoot]]))

(comment
  (let [d ^java.time.ZonedDateTime (java.time.ZonedDateTime/now)]
    (.format d (java.time.format.DateTimeFormatter/ofPattern "Y/MM/dd H:m")))
  )
