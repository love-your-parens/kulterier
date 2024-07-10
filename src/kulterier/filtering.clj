(ns kulterier.filtering
  (:require [kulterier.util :as util]
            [kulterier.scraper :as scraper]))


(def timezone (java.time.ZoneId/of "Europe/Warsaw"))


(defn query-param->vec [param]
  (when param
    (if (coll? param) param (vector param))))


(def query-param->set
  (comp set query-param->vec))


(defn in-set
  "Applies a whitelist, i.e. filters `data` by a set of accepted `values`.
  To extract values used for comparison, `->value` will be called on each `data` item."
  [data values ->value]
  (if (empty? values) data
      (let [value-set (set values)]
        (filter #(value-set (->value %)) data))))


(defn get-event-types
  "Selects all distinct event types present in the data."
  [event-data]
  (map (juxt name util/event-type-name) ; key,name
       (let [get-type (comp :type second)]
         (reduce (fn [ts event]
                   (if-let [t (get-type event)]
                     (conj ts t) ts))
                 #{}
                 event-data))))


(defn get-event-venues
  "Selects all distinct event venues present in the data."
  [event-data]
  (map (juxt identity identity) ; key,name
       (let [get-venue (comp :place second)]
         (reduce (fn [ts event]
                   (if-let [t (get-venue event)]
                     (conj ts t) ts))
                 #{}
                 event-data))))


(defn in-day-range
  "Selects for events located within a range of `day-span` of the current date.
  Values for comparison will be produced from items using `->value`.
  If such a value is not a Temporal, it's exempt from comparison.
  NOTE Only designed to support single-date/time events.
  In other words: it's only effective for timetable events."
  [data day-span ->value]
  {:pre [(int? day-span)
         (>= day-span 0) ; no backtracking
         ]}
  (let [comparable? #(instance? java.time.ZonedDateTime %)
        ->same-zone (fn [^java.time.ZonedDateTime zdt]
                      (.withZoneSameInstant zdt timezone))
        current-datetime (->same-zone (java.time.ZonedDateTime/now))
        left-date (java.time.LocalDate/from current-datetime)
        right-date (.plusDays left-date day-span)]
    (filter (fn [item]
              (let [v (->value item)]
                (or (not (comparable? v)) ; free pass
                    (let [localised (->same-zone v)
                          date (java.time.LocalDate/from localised)]
                      (and (>= (.compareTo date left-date) 0)
                           (<= (.compareTo date right-date) 0))))))
            data)))

(comment
  (let [events (scraper/get-events)
        f (fn [data] (in-day-range data 1 #(-> % second :date)))]
    [(count (:timetable events))
     (count (f (:timetable events)))
     (count (:permanent events))
     (count (f (:permanent events)))]))


(defn in-optional-day-range
  "Same as `in-day-range`, but allows for
  falsy `day-span` to indicate a catch-all."
  [data day-span ->value]
  (if (not day-span) data
      (in-day-range data day-span ->value)))
