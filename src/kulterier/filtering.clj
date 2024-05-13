(ns kulterier.filtering
  (:require [kulterier.util :as util]))


(defn query-param->vec [param]
  (when param
    (if (coll? param) param (vector param))))


(def query-param->set
  (comp set query-param->vec))


(defn in-set [data values ->value]
  (if (empty? values) data
      (filter #(values (->value %)) data)))


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
