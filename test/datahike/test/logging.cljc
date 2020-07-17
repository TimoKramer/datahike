(ns datahike.test.logging
   (:require [clojure.spec.alpha :as s]
             [clojure.spec.test.alpha :as stest]
             [clojure.string :as string]
             [clojure.test :refer :all]
             [clojure.test.check.generators :as gen]
             [taoensso.timbre :as log]))

(def logs-atom (atom []))

(defn appender
  [{:keys [vargs ?ns-str] :as logging-data}]
  (swap! logs-atom conj {:vargs   vargs
                         :?ns-str ?ns-str}))

(def filter-str (str *ns*))

(defn logs
  []
  (->> @logs-atom
       (filter (fn [{:keys [?ns-str]}]
                 (= ?ns-str filter-str)))
       (mapv :vargs)))

(defn start-test
  []
  (log/merge-config! {:appenders {:hijack-appender {:enabled? true
                                                    :fn appender}}})
  (reset! logs-atom []))

(use-fixtures :each (fn [f]
                      (start-test)
                      (f)
                      (log/merge-config! {:appenders {:hijack-appender {:enabled? false}}
                                          :middleware []})))

(defn logtest
  [msgs data]
  (start-test)
  (log/fatal msgs data)
  (mapv (partial zipmap [:msgs :data])
        (logs)))

(s/fdef
  logtest
  :args (s/and (s/cat :msgs string?
                      :data map?)
               (fn [{s0 :msgs s1 :data}]
                 (not (string/includes? s0 s1))))
  :ret vector?
  :fn (fn [{{msgs :msgs
             data :data} :args
            logs :ret}]
        (and (->> logs
                  (map :msgs)
                  (every? (partial = msgs)))
             (->> logs
                  (map :data)
                  (every? (partial = data))))))

(deftest gen-spec-testing
  (testing "regex remove"
    (is (-> 'logtest
            stest/check
            stest/summarize-results
            (dissoc :total)
            (dissoc :check-passed)
            empty?))))

(comment
  (logtest "foo" {:bar "baz"})
  'logtest
  (println (stest/check 'logtest))
  (-> 'logtest
      stest/check
      stest/summarize-results
      (dissoc :total)
      (dissoc :check-passed)
      empty?))
