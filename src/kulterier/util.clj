(ns kulterier.util)


(defn event-type-name
  [event-type-key]
  (case event-type-key
    :museum "muzeum"
    :movie "kino"
    :theatre "teatr"
    "inne"))
