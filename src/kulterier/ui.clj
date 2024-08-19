(ns kulterier.ui
  (:require [cheshire.core :as cheshire]
            [clojure.java.io :as io]
            [clojure.set :as sets]
            [kulterier.settings :as settings]
            [com.biffweb :as biff]
            [ring.middleware.anti-forgery :as csrf]
            [ring.util.response :as ring-response]
            [rum.core :as rum]
            [kulterier.svg :as svg]
            [kulterier.filtering :as fltr]
            [kulterier.util :as util]
            [clojure.string :as s]
            [hickory.core :as hkc]
            [hickory.select :as hks]))


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


(defn hiccup->jsoup
  "Converts a Hiccup node into a Jsoup object.
  This is handy because Jsoup objects can be used by tools like Enlive or Hickory
  in a manner similar to browser DOM, allowing for easy selection and traversal.
  NOTE Inherently expensive as it involves string conversion!"
  [hiccup]
  (first (hkc/parse-fragment (rum/render-static-markup hiccup))))


(def hiccup->hickory
  "Converts Hiccup to Hickory. Expensive!
  See also: `hiccup->jsoup`"
  (comp hkc/as-hickory hiccup->jsoup))

(comment (hiccup->hickory [:div.myclass.abc]))


(defn tainted
  "Checks whether a hiccup node - usually an input element - has been _tainted_,
  meaning it deviates from its original state, e.g.: a checkbox toggled by the user.
  Any node containing tainted nodes is also tainted."
  [node]
  (when (seq node)
    (first (hks/select (hks/class "tainted")
                       (hkc/as-hickory (hiccup->jsoup node))))))

(comment
  (tainted [:div
            [:span.innocent]
            [:span.not-me]
            [:span.oh-no {:class "tainted"}]]))


(defn taint
  "Tags a hiccup node as tainted (see: `tainted`).
  NOTE Not idempotent. May produce redundancy."
  [node]
  (when (seq node)
    (let [[tag & content] node
          tag-name (name tag)]
      (into [(keyword (str tag-name ".tainted"))]
            content))))

(comment
  (taint [:div "what's all this then?"]) ;=> [:div.tainted "what's all this then?"]
  (taint [:div.tainted "This will be redundant"]) ;=> [:div.tainted.tainted "This will be redundant"]
  (taint [:div {:class "tainted"} "Also redundant"]) ;=> [:div.tainted {:class "tainted"} "Also redundant"]
  )


(defn base [{:keys [::recaptcha biff/base-url] :as ctx} & body]
  (apply
   biff/base-html
   (-> ctx
       (merge #:base{:title settings/app-name
                     :lang "pl-PL"
                     :icon "/img/kulterier-emblem-sm.png"
                     :description (str settings/app-name " - pies na kulturę")
                     :image (str base-url "/img/kulterier-emblem-sm.png")})
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


(defn fixed-load-indicator
  "Universal in-progress (ajax et al) indicator."
  [& classes]
  [:div.htmx-indicator
   {:class (into ["text-right" "fixed" "top-0" "w-screen" "transition"
                  "pointer-events-none"]
                 classes)}
   [:img {:src "/img/rings.svg"
          :width 40 :height 40
          :class ["inline" "align-middle" "bg-gray-800"
                  "rounded-full" "m-6"]
          :alt "Animated progress indicator"}]])


(defn go-to-top
  "Intended to appear below the fold and scroll to top when clicked."
  []
  [:div#back-to-top
   {:class ["fixed" "h-[60px]" "w-[60px]" "bottom-0" "right-0"
            "mr-5" "md:mr-10" "mb-10" "flex" "justify-center" "items-center"
            "bg-slate-800" "text-slate-300" "dark:bg-slate-400" "dark:text-slate-900"
            "rounded-full" "text-4xl" "font-semibold" "cursor-pointer"
            "transition-all" "opacity-0" "pointer-events-none"
            "hover:scale-110"]}
   [:div {:title "Wróć na górę"} "↑"]])


(defn screen-overlay-popup []
  [:div {:id "overlay-popup"
         :class ["fixed" "h-full" "w-full" "bg-neutral-900" "text-gray-200" "opacity-95"
                 "overflow-scroll"
                 "transition-all" "z-100" "-left-[100vw]" "invisible"]}
   [:input {:type "button"
            :id "overlay-popup--close"
            :class ["w-8" "h-8" "text-lg" "flex" "justify-center" "items-center" "m-6"
                    "cursor-pointer" "transition-transform" "hover:scale-110"
                    "float-right" "font-black" "bg-white" "rounded-full" "text-black"]
            :onclick "hideOverlayPopup()"
            :title "Zamknij"
            :value "X"}]
   [:div.content.m-10]])


(defn page [ctx & body]
  (base
   ctx
   (fixed-load-indicator "global-load-indicator")
   (go-to-top)
   [:.flex-grow]
   [:.mx-auto.w-full.py-2.md:p-6
    (when (bound? #'csrf/*anti-forgery-token*)
      {:hx-headers (cheshire/generate-string
                    {:x-csrf-token csrf/*anti-forgery-token*})})
    body]
   [:.flex-grow]
   [:.flex-grow]
   (screen-overlay-popup)))


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
  ([text-node url & {:as params}]
   [:a.hover:underline
    (merge params {:href url
                   :title (when (string? text-node) text-node)})
    (or text-node url)]))


(defn filter-popup
  [& contents]
  (into [:div#filter-popup
         {:class ["group/popup"
                  "invisible"
                  "fixed" "z-10" "-top-[100vh]" "left-0" "max-h-full" "w-full"
                  "overflow-scroll" "p-10" "bg-slate-950" "text-neutral-100"
                  "text-sm" "md:text-base"
                  "transition-all"]}
         [:input {:type "button"
                  :id "filter-popup--close"
                  :class ["w-8" "h-8" "text-lg" "flex" "justify-center" "items-center" "mt-1"
                          "cursor-pointer" "transition-transform" "hover:scale-110"
                          "float-right" "font-black" "bg-white" "rounded-full" "text-black"]
                  :onclick "hideFilterPopup()"
                  :title "Zamknij"
                  :value "X"}]
         [:p.font-black {:class ["text-3xl" "md:text-4xl"]}
          "Czego szukamy?"]]
        contents))


(defn filter-popup-toggle
  [text]
  [:p {:class ["text-right" "m-auto" "-mt-1" "-md:mt-4" "mb-2" "md:mb-4"
               "max-w-[90%]" "md:max-w-[1024px]" "text-xs" "md:text-sm"
               "font-bold"]}
   [:label
    {:class ["cursor-pointer" "inline-block" "transition-transform" "hover:scale-110"]}
    (svg/magnifying-glass
     :class ["w-[14px]" "md:w-[20px]" "h-[14px]" "md:h-[20px]"
             "fill-slate-900" "dark:fill-slate-200"
             "inline" "mr-1" "md:mr-2"])
    [:input {:type "button" :class ["cursor-pointer"]
             :onclick "showFilterPopup()"
             :value (str text)}]]])


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
                      "border-l-2" "pl-0.5" "border-slate-700"
                      "transition-all"
                      "hover:brightness-200"
                      "dark:border-slate-400"]}]])
    (if url (text-link title url) title)]))


(defn event-type-tag
  [event-type-key]
  [:span.font-light
   {:class ["text-slate-600" "dark:text-slate-300"]}
   (str "#" (util/event-type-name event-type-key))])

(comment
  (event-type-tag :whatever)
  (event-type-tag :movie))


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


(defn truncate-text
  "Attempts to truncate the provided text to a maximum length of `length`.
  Avoids slicing words if possible.
  Sufficiently short texts are left as-is."
  [text length]
  (if (> (count text) length)
    (let [limit (max 0 (s/last-index-of text " " (- length 3)))]
      (str (subs text 0 limit) "..."))
    text))


(defn timetable-events-table
  [events]
  (let [grouped (group-by (fn [[_ event]]
                            (.format
                             (:date event)
                             (java.time.format.DateTimeFormatter/ofPattern "Y/MM/dd")))
                          events)
        groups-asc (sort (keys grouped))]
    (for [g groups-asc :let [events (sort-by (comp :date second)
                                             (get grouped g []))]]
      [:<>
       ;; Sticky group marker.
       [:div {:class ["max-w-[95%]" "md:w-[1024px]" "m-auto" "mb-2"
                      "sticky" "top-0" "flex" "transition-colors"]}
        [:div {:class ["bg-gray-100" "dark:bg-gray-700"]}
         [:span.font-light.mr-2 "»"]
         [:span {:class ["font-black" "text-xl" "border-b" "px-1" "pb-0.5"
                         "border-gray-600" "dark:border-slate-200"]} g]]
        [:div {:class ["grow" "bg-gradient-to-r" "to-transparent"
                       "from-gray-100" "dark:from-gray-700"]}]]
       ;; Actual group content.
       [:table {:class ["m-auto" "max-w-[95%]" "md:w-[1024px]"]}
        [:tbody
         (for [[i d] events]
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
              ;; Keep descriptions shorter in this particular table.
              (description-row colspan
                               (truncate-text (:description d) 400)
                               i)]))]
        [:tfoot]]])))


(defn permanent-events-table
  [events]
  [:table {:class ["mx-auto" "my-5" "max-w-[95%]" "md:max-w-[1024px]"]}
   [:tbody
    (for [[i d] (sort-by (fn [[_ d]] (str
                                      (if (empty? (:description d)) 1 0)
                                      (:place d))) events)]
      (let [summary-row [(title-cell (:name d) (:url d) (:thumbnail d))
                         [:td.text-left.md:p-2 (:place d)]
                         [:td.text-right.md:p-2 (event-type-tag (:type d))]]
            colspan (count summary-row)]
        [:<>
         (into [:tr {:class "event-breakdown"
                     :data-event-id i}]
               summary-row)
         (title-row colspan (:name d) (:url d) (:thumbnail d))
         (description-row colspan (truncate-text (:description d) 400) i)]))]
   [:tfoot]])


(defn temporary-events-table
  [events]
  (let [sorted-events (sort-by (comp second :date second) events)]
    [:table {:class ["mx-auto" "my-5" "max-w-[95%]" "md:max-w-[1024px]"]}
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
           (description-row colspan (truncate-text (:description d) 400) i)]))]
     [:tfoot]]))


(defn event-tab-url
  "Produces a pair of URLs for any given event key.
  First item represents the URL of the AJAX content-source.
  The second item represents the navigation URL."
  [key]
  (let [k (name key)]
    [(str "/events/" k)
     (str "/" k)]))


(defn event-tab
  [key label selected?]
  (let [[ajax-url navigation-url] (event-tab-url key)]
    [:label.m-2 {:class ["font-bold" "cursor-pointer" "transition-all" "ease-in-out" "underline-offset-4"
                         "text-sm" "md:text-base"
                         "hover:scale-110" "hover:underline" "hover:mx-3" "has-[:checked]:underline"]}
     [:input.category-tab {:class ["mx-1" "dark:px-100"
                                   "text-slate-700" "dark:text-slate-900" "focus:ring-slate-600"
                                   "border-gray-400" "dark:border-gray-900" "dark:bg-gray-300"]
                           :type "radio" :name "category"
                           :role "tab" :aria-controls "event-panel"
                           :hx-get ajax-url
                           :hx-push-url navigation-url
                           :hx-indicator ".global-load-indicator"
                           :aria-selected (str selected?)
                           :checked (when selected? "1")}]
     label]))


(defn event-tab-list
  [selected-tab]
  [:<>
   [:fieldset {:role "tablist"
               :hx-sync "this:abort"
               :class ["flex" "justify-center" "items-center" "my-6"]}
    (event-tab :permanent "Stałe" (= selected-tab :permanent))
    [:div "•"]
    (event-tab :temporary "Tymczasowe" (= selected-tab :temporary))
    [:div "•"]
    (event-tab :timetable "Pojedyncze" (= selected-tab :timetable))]])


(defn filter-title
  [title]
  [:div.font-bold {:class ["text-lg" "md:text-xl"]} title ":"])


(defn checklist-filter
  "Produces a generic multiple-selection form widget, designed in the context of content filtering.
  If no values are provided, then the result will be `nil`.
  `title` is the text prefacing the widget.
  `property-key` is a keyword corresponding to the appropriate HTTP request parameter.
  `vals-and-labels` represents all recognised input value / input label pairs.
  `params` are the HTTP request parameters, used to resolve the initial state of the widget.
  If the initial state is altered, the DOM node will be `tainted`."
  [title property-key vals-and-labels params]
  (when (> (count vals-and-labels) 1)
    (let [known-keys (set (map first vals-and-labels))
          requested-keys (set (let [ks (get params property-key)]
                                (when ks (if (coll? ks) ks (vector ks)))))
          selected (sets/intersection known-keys requested-keys)
          select-all (or (empty? selected)
                         (= selected known-keys))
          container (cond-> [:fieldset {:class ["disabled:opacity-50"]
                                        :onchange "requestSubmit()"}]
                      (not select-all) taint)]
      (conj container
            (filter-title title)
            [:label.all {:onchange "this.parentElement
                                    .querySelectorAll('.individual > input[type=checkbox]')
                                    .forEach(el => el.checked = null)"
                         :class ["mx-1" "hover:underline" "cursor-pointer" "block" "whitespace-nowrap"]}
             [:input.select-all {:type "checkbox" :checked select-all :disabled select-all
                                 :class ["mr-1" "rounded-full" "text-slate-700" "focus:ring-slate-600"
                                         "border-gray-900" "bg-gray-300"]}]
             "Wszystkie"]
            (next
             (interleave
              (repeat [:span.mx-1 "•"])
              (for [[v l] (sort-by second vals-and-labels)]
                [:label.individual
                 {:onchange "this.parentElement.querySelector('.all > input[type=checkbox]').checked = null"
                  :class ["hover:underline" "cursor-pointer" "inline-block" "whitespace-nowrap"]}
                 [:input {:type "checkbox" :name property-key :value v
                          :checked (and (not select-all) (selected v))
                          :class ["mx-1" "rounded" "text-slate-700" "focus:ring-slate-600"
                                  "border-gray-900" "bg-gray-300"]}]
                 [:span.capitalize l]])))))))


(defn radio-filter
  [title property-key values-and-labels-map params]
  (let [requested (get params property-key)
        selected (get (->> values-and-labels-map
                           keys
                           (filter some?)
                           set)
                      requested)
        container (cond-> [:fieldset {:class ["disabled:opacity-50"]
                                      :onchange "requestSubmit()"}]
                    selected taint)
        radio (fn [value label & {:keys [default?]}]
                [:label
                 {:class "m-1 cursor-pointer hover:underline inline-block whitespace-nowrap"}
                 [:input {:type "radio" :name property-key :value value
                          :checked (if selected
                                     (= selected value)
                                     default?)
                          :class [(when default? "select-all")
                                  "text-slate-700" "focus:ring-slate-600"
                                  "border-gray-900" "bg-gray-300"
                                  "mr-1" "cursor-pointer"]}]
                 label])]
    [:div
     (into container
           (concat [(filter-title title)
                    (radio nil "Dowolny" :default? true)]
                   (for [[value label] values-and-labels-map]
                     [:<> [:span.mx-1 "•"]
                      (radio value (or label value))])))]))


(defn event-tab-filters
  "Produces a GUI element containing filters specific to the active event-tab.
  This consists of two objects:
  - a popup element, which contains the actual filters
  - an activator/toggle element with which to bring up the filter popup
  Filtering criteria is derived from event data.
  The state is derived from `params` and comes from HTTP query parameters etc."
  [params selected-tab event-data]
  (let [[ajax-url navigation-url] (event-tab-url selected-tab)
        types (fltr/get-event-types event-data)
        venues (fltr/get-event-venues event-data)]
    [:<>
     (filter-popup
      (let [fieldsets [(when (= selected-tab :timetable)
                         [:div.my-2 (radio-filter "Czas wydarzenia" :event-timespan
                                                  {"0" "Dziś"
                                                   "2" "Najbliższe 3 dni"
                                                   "6" "Tydzień"
                                                   "13" "Dwa tygodnie"
                                                   "30" "Miesiąc"}
                                                  params)])
                       [:div.my-2 (checklist-filter "Rodzaj wydarzenia" :event-type types params)]
                       [:div.my-2 (checklist-filter "Miejsce wydarzenia" :event-venue venues params)]]
            taint (first (keep tainted fieldsets))]
        (into [:form.flex.flex-col
               {:hx-get ajax-url :hx-trigger "submit delay:1000" :hx-indicator ".global-load-indicator"}
               (when taint
                 [:div {:class ["mb-2"]}
                  [:label {:class "inline-block border-2 border-neutral-200 rounded
                                   hover:bg-white hover:text-neutral-900 hover:scale-105
                                   p-1 pl-2 pr-3 cursor-pointer font-bold transition-transform"}
                   [:input.mr-2 {:type "button" :onclick "resetFilters(this.form)" :value "⨯"}]
                   "Wszystkiego!"]])]
              fieldsets)))
     (filter-popup-toggle "Węsz uważniej...")]))


(defn footer []
  [:footer.text-xs.m-auto.mt-20.text-center.font-light
   [:div {:class ["inline-block" "align-middle" "border" "rounded-full"
                  "border-slate-900" "dark:border-slate-100"
                  "h-[66px]" "w-[66px]"  "overflow-hidden"
                  "hover:animate-spin"]}
    (svg/kulterier-logo :height 60 :class ["m-auto"  "pl-1"] :alt "")]
   [:div {:class ["text-center" "m-auto" "ml-1" "my-2" "py-1" "px-1" "align-middle"
                  "border-t"  "border-b" "border-slate-900" "dark:border-slate-100"
                  "border-dotted" "inline-block"]}
    [:p
     {:class ["my-0.5"]}
     "Treserem i opiekunem Kulteriera jest "
     (text-link "Konrad Wątor" "https://wator.it"
                :target "_blank"
                :class ["whitespace-nowrap" "text-slate-600" "dark:text-slate-300"])
     "."]
    [:hr {:class ["m-auto" "mt-2" "mb-1" "align-middle" "w-3" "border-t" "border-dotted"
                  "border-slate-900" "dark:border-slate-100"]}]
    [:p {:class ["my-0"]}
     [:label.cursor-pointer.mx-2 {:alt "Lista zmian"}
      (svg/changelog-icon :height 15
                          :class ["stroke-[3px]" "stroke-slate-900" "dark:stroke-white"
                                  "inline-block" "mr-1" "align-text-bottom"])
      [:input
       {:type "button" :onclick "showOverlayPopup('/changelog')"
        :class ["cursor-pointer" "hover:underline"]
        :value "Zmiany" :hx-target "#overlay-popup > .content" :hx-get "/changelog"}]]
     [:a.mx-2
      (text-link [:<> [:span.align-middle.mr-1
                       {:aria-hidden "true"}
                       [:img
                        {:class "inline dark:hidden h-3 w-auto align-baseline",
                         :height "18",
                         :src "/img/third-party/github-mark.svg",
                         :width "18"}]
                       [:img
                        {:class "hidden dark:inline h-3 w-auto align-baseline",
                         :height "18",
                         :src "/img/third-party/github-mark-white.svg",
                         :width "18"}]]
                  "Źródło"]
                 "https://github.com/love-your-parens/kulterier"
                 :target "_blank")]]]])
