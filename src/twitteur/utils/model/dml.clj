(ns twitteur.utils.model.dml
  "A functional DSL for writing Domain Model transactions")

(defn- merge-clauses
  [m clauses]
  (reduce
    (fn [m clause]
      (cond
        (string? clause) (assoc m :doc clause)
        (map? clause) (merge m clause)
        :else
        (throw (ex-info "Unidentified DML clause"
                 {:clause clause}))))
    m clauses))

(defn scalar
  [name type & clauses]
  (merge-clauses
    {:attribute/name name
     :attribute/ref-typed? false
     :attribute.scalar/type type}
    clauses))

(defn- ref-attr
  [name type many? & clauses]
  (merge-clauses
    {:attribute/name name
     :attribute/ref-typed? true
     :attribute.ref-typed/many? many?
     :attribute.ref-typed/type {:entity-type/name type}}
    clauses))

(defn to-one
  [name type & clauses]
  (apply ref-attr name type false clauses))

(defn to-many
  [name type & clauses]
  (apply ref-attr name type true clauses))
