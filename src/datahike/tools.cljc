(ns datahike.tools
  (:require
   [clojure.spec.alpha :as s]
   [taoensso.timbre :as log]))

(defn combine-hashes [x y]
  #?(:clj  (clojure.lang.Util/hashCombine x y)
     :cljs (hash-combine x y)))

#?(:clj
   (defn- -case-tree [queries variants]
     (if queries
       (let [v1 (take (/ (count variants) 2) variants)
             v2 (drop (/ (count variants) 2) variants)]
         (list 'if (first queries)
               (-case-tree (next queries) v1)
               (-case-tree (next queries) v2)))
       (first variants))))

#?(:clj
   (defmacro case-tree [qs vs]
     (-case-tree qs vs)))

(defn get-time []
  #?(:clj (java.util.Date.)
     :cljs (js/Date.)))

(s/def ::message-structure (s/+ (s/or :string string?
                                      :variable symbol? ; db.cljc has lots of vars in messages
                                      :function-call list? ; db.cljc line 1010
                                      :vector vector?))) ; db.cljc line 1154
(s/def ::data-structure (s/or :map map?
                              :function-call list? ; db.cljc line 717
                              :variable symbol?)) ; query_v3.cljc line 560
(s/def ::fragments (s/cat :messages ::message-structure
                          :data ::data-structure))
(s/fdef raise
        :args ::fragments
        :ret any?)

(defmacro raise
  "Logging an error and throwing an exception with message and structured data.
   Arguments:
   - Any number of strings that describe the error
   - Last argument is a map of data that helps understanding the source of the error"
  [& fragments]
  (let [msgs (butlast fragments)
        data (last fragments)]
    (list `(log/log! :error :p ~fragments ~{:?line (:line (meta &form))})
          `(throw #?(:clj  (ex-info (str ~@(map (fn [m#] (if (string? m#) m# (list 'pr-str m#))) msgs)) ~data)
                     :cljs (error (str ~@(map (fn [m#] (if (string? m#) m# (list 'pr-str m#))) msgs)) ~data))))))
