(ns twitteur.utils.model.dml
  "A lightweight functional DSL for writing Domain Model transactions.")

(defn- merge-clauses
  "Incorporate DSL clauses into the map `m`. A DSL clause may be:
  - a String, which will result in a :twitteur.schema/doc property
  - a Map, which will be merged into the returned Map."
  [m clauses]
  (reduce
    (fn [m clause]
      (cond
        (string? clause) (assoc m :twitteur.schema/doc clause)
        (map? clause) (merge m clause)
        :else
        (throw (ex-info "Unidentified DML clause"
                 {::clause clause}))))
    m clauses))

;; ------------------------------------------------------------------------------
;; Entity Type DSL

(defn entity-type
  [ent-type-name & clauses]
  (merge-clauses
    {:twitteur.entity-type/name ent-type-name}
    clauses))

;; ------------------------------------------------------------------------------
;; Attribute DSL

(defn scalar
  [name type & clauses]
  (merge-clauses
    {:twitteur.attribute/name name
     :twitteur.attribute/ref-typed? false
     :twitteur.attribute.scalar/type type}
    clauses))

(defn- ref-attr
  [name type many? & clauses]
  (merge-clauses
    {:twitteur.attribute/name name
     :twitteur.attribute/ref-typed? true
     :twitteur.attribute.ref-typed/many? many?
     :twitteur.attribute.ref-typed/type {:twitteur.entity-type/name type}}
    clauses))

(defn to-one
  [name type & clauses]
  (apply ref-attr name type false clauses))

(defn to-many
  [name type & clauses]
  (apply ref-attr name type true clauses))

(defn unique-id
  "Marks this Attribute as uniquely identifying the Entities that have it (via `:twitteur.attribute/unique-identity`)."
  []
  {:twitteur.attribute/unique-identity true})

(defn private
  "Marks this Attribute as not being publicly visible on Twitteur (via `:twitteur.attribute.security/private?`)."
  []
  {:twitteur.attribute.security/private? true})

(defn derived
  "Marks this Attribute as being computed from other Attributes (via `:twitteur.attribute/derived?`)."
  []
  {:twitteur.attribute/derived? true})


