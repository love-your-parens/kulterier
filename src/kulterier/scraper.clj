(ns kulterier.scraper (:require [bottom-of-the-barrel.core :as source]
                                [clojure.core.match :as m :refer [match] ]))

(def cache-expiry (* 60 60 24))

(defn fetch
  "Retrieves events (exhibitions) from all registered sources.
  Uses cache by default, unless prompted otherwise."
  ([] (fetch false))
  ([refresh?]
   (binding [source/*cache-expiry* (if refresh? 0 cache-expiry)]
     (source/fetch-all-with-cache!))))

(comment
  (fetch)
  (fetch true))

(defn index
  "Adds numeric indices to a coll."
  [data]
  (partition 2 (interleave (range) data)))

(comment
  (index [:a :b :c]) ;=> '((0 :a) (1 :b) (2 :c))
  )

(defn match-date-type
  "Matches an appropriate type-key to a date record."
  [date]
  (let [local-date? (partial instance? java.time.LocalDate)
        date-time? (fn [x] (or (instance? java.time.LocalDateTime x)
                               (instance? java.time.ZonedDateTime x)))
        [a b] date]
     (match [a b]
        [nil nil] :permanent
        [(_ :guard local-date?) nil] :permanent
        [(_ :guard local-date?) (_ :guard local-date?)] :temporary
        [(_ :guard date-time?) _] :timetable
        :else :permanent)
     ))

(comment
  (let [dates [[(java.time.LocalDate/now) nil]
               [(java.time.LocalDate/now) (java.time.LocalDate/now)]
               []
               [nil]
               [(java.time.ZonedDateTime/of (java.time.LocalDateTime/now) (java.time.ZoneId/of "Europe/Warsaw"))]
               (repeat 5 (java.time.ZonedDateTime/of (java.time.LocalDateTime/now) (java.time.ZoneId/of "Europe/Warsaw")))
               [1 2 3 4 5 6 7]]]
    (map match-date-type dates))
)

(defn append-date-type
  [event]
  (assoc event :date-type
           (match-date-type (:date event))))

(defn group-by-date-type
  [events]
  (group-by (fn [e] (match-date-type (:date e)))
            events))

(defn expand-timetable-events
  "Expands timetable events on a per-date basis.
  An event with X dates will produce X events with 1 date each.
  Note: :date of timetable events is inherently a coll by definition."
  [events]
  (let [now ^java.time.ZonedDateTime (java.time.ZonedDateTime/now)]
    (reduce (fn [expanded-events e]
              (into expanded-events
                    ;; Drop past events
                    (keep #(when (<= (.compareTo now %) 0)
                             (assoc e :date %))
                          (:date e))))
            []
            events)))

(defn expand-grouped-events
  "Applies expansion to each distinct event-group, retaining the groupings."
  [event-groups]
  (assoc event-groups :timetable
         (expand-timetable-events (:timetable event-groups))))

(defn get-events
    "Retrieves all events from remote sources (using cache),
      and tranforms them by applying all local middleware."
    []
    (->> (fetch)
         group-by-date-type
         expand-grouped-events
         (map (fn [[k vs]] [k (index vs)])) ;indexation
         (into {})))

(comment
  (get-events)
  )
