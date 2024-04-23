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
                     :lang "pl-PL"
                     :icon "/img/glider.png"
                     :description (str settings/app-name " Description")
                     :image "https://clojure.org/images/clojure-logo-120b.png"})
       (update :base/head (fn [head]
                            (concat [[:link {:rel "stylesheet" :href (css-path)}]
                                     [:link {:rel "stylesheet" :href "/css/switzer.css"}]
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
   [:.mx-auto.w-full.py-2.md:p-6
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
                 "max-w-[40vw]"]}
    (when thumbnail
      [:a {:href url}
       [:img {:src thumbnail
              :alt title
              :class ["inline" "min-w-[80px]" "w-[90px]" "max-h-[90px]"
                      "mr-2" "md:mr-4" "object-cover" "object-top"
                      "border-l-2" "pl-0.5" "border-black"]}]])
    (if url (text-link title url) title)]))

(defn event-type-tag
  [event-type-key]
  [:span.font-light.text-slate-600
   (str "#" (case event-type-key
              :museum "muzeum"
              :movie "kino"
              "inne"))])

(comment
  (event-type-tag :whatever)
  (event-type-tag :movie)
  )

(defn title-row
  [colspan title url thumbnail]
  [:tr
   [:td {:colspan colspan
         :class ["md:hidden"
                 "max-w-[90vw]"
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
                      "mr-3" "md:mr-4" "object-cover" "object-top"
                      "border-l-2" "pl-0.5" "border-black"]}]])
    (if url (text-link title url) title)]])

(defn description-row
  [colspan event-description event-id]
  [:tr {:class ["event-description"]
        :data-event-id event-id}
   (let [description (str event-description)
         not-blank? (not (.isBlank description))]
     [:td {:colspan colspan :class ["md:px-2.5" "pb-3" "md:pb-0"]}
      (when not-blank?
        [:<>
         [:div.float-left.text-xl.font-mono
          {:class "-mt-1"}
          "↳"]
         [:p.ml-4.mr-1.text-justify
          description]])])])

(defn timetable-events-table
  [events]
  (let [colspan (-> events first second count)
        grouped (group-by (fn [[_ event]]
                            (.format (:date event)
                                     (java.time.format.DateTimeFormatter/ofPattern "Y/MM/dd")))
                          (sort-by (comp :date second) events))]
    [:table {:class ["m-auto" "-mt-2" "max-w-[95%]" "md:max-w-[1024px]"]}
     [:tbody
      (for [[g e] grouped]
        (into [:<>
               [:tr [:td {:class ["text-xl" "font-light" "text-left" "pt-1"]
                          :colspan colspan}
                     [:p {:class ["border-b border-dashed border-neutral-400 py-2"]}
                      (format "» %s" g)]]]]
              (for [[i d] e]
                (let [summary-row [(title-cell (:name d) (:url d) (:thumbnail d))
                                   [:td.text-left.md:p-2 (:place d)]
                                   [:td.md:text-center.md:p-2.font-semibold
                                    (when-let [d ^java.time.ZonedDateTime (:date d)]
                                      (.format d (java.time.format.DateTimeFormatter/ofPattern
                                                  "Y/MM/dd HH:mm")))]
                                   [:td.text-right.md:p-2 (event-type-tag (:type d))]]
                      colspan (count summary-row)]
                  [:<>
                   (into [:tr {:class "event-breakdown"
                               :data-event-id i}]
                         summary-row)
                   (title-row colspan (:name d) (:url d) (:thumbnail d))
                   (description-row colspan (:description d) i)]))))]
     [:tfoot]]))

(defn permanent-events-table
  [events]
  [:table {:class ["mx-auto" "my-4" "max-w-[95%]" "md:max-w-[1024px]"]}
   [:tbody
    (for [[i d] events]
      (let [summary-row [(title-cell (:name d) (:url d) (:thumbnail d))
                         [:td.text-left.md:p-2 (:place d)]
                         [:td.text-right.md:p-2 (event-type-tag (:type d))]]
            colspan (count summary-row)]
        [:<>
         (into [:tr {:class "event-breakdown"
                     :data-event-id i}]
               summary-row)
         (title-row colspan (:name d) (:url d) (:thumbnail d))
         (description-row colspan (:description d) i)]))]
   [:tfoot]])


(defn temporary-events-table
  [events]
  (let [sorted-events (sort-by (comp second :date second) events)]
    [:table {:class ["mx-auto" "my-4" "max-w-[95%]" "md:max-w-[1024px]"]}
     [:tbody
      (for [[i d] sorted-events]
        (let [summary-row [(title-cell (:name d) (:url d) (:thumbnail d))
                           [:td.text-left.md:p-2 (:place d)]
                           [:td.text-center.md:text-center.px-1.md:p-2.font-semibold
                            (-> d :date first)]
                           [:td.text-center.md:text-center.px-1.md:p-2.font-semibold
                            (-> d :date second)]
                           [:td.text-right.md:p-2 (event-type-tag (:type d))]]
              colspan (count summary-row)]
          [:<>
           (into [:tr {:class "event-breakdown"
                       :data-event-id i}]
                 summary-row)
           (title-row colspan (:name d) (:url d) (:thumbnail d))
           (description-row colspan (:description d) i)]))]
     [:tfoot]]))

(defn event-tab
  [key label selected?]
  (let [keyname (name key)]
    [:label.m-2 {:class ["font-bold" "cursor-pointer" "transition-all" "ease-in-out" "underline-offset-4"
                         "hover:scale-110" "hover:underline" "hover:mx-3" "has-[:checked]:underline"]}
     [:input {:class ["mx-1" "text-slate-700" "focus:ring-slate-600" "border-gray-400"]
              :type "radio" :name "category"
              :role "tab" :aria-controls "event-panel"
              :hx-get (str "/events/" keyname)
              :hx-push-url (str "/" keyname)
              :value keyname
              :aria-selected (str selected?)
              :checked (when selected? "1")}]
     label]))

(defn event-tab-list
  [selected-tab]
  [:<>
   [:fieldset {:role "tablist" :class ["flex" "justify-center" "items-center" "my-4"]}
    (event-tab :permanent "Stałe" (= selected-tab :permanent))
    [:div "•"]
    (event-tab :temporary "Tymczasowe" (= selected-tab :temporary))
    [:div "•"]
    (event-tab :timetable "Pojedyncze" (= selected-tab :timetable))]])

(defn event-tab-panel
  [content]
  [:div {:aria-role "tabpanel"}
   content])

(defn event-tab-container
  [selected-tab tab-content]
  [:<>
   (event-tab-list selected-tab)
   (event-tab-panel tab-content)])
