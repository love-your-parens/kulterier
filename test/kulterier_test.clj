(ns kulterier-test
  ;; If you add more test files, require them here so that they'll get loaded by kulterier/on-save
  (:require [clojure.test :refer [deftest is]]))

(deftest example-test
  (is (= 4 (+ 2 2))))
