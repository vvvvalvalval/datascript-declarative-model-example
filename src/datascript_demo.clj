(ns datascript-demo
  "A quick Tour of DataScript features."
  (:require [datascript.core :as dt]))



;;;; Declaring the (minimal required) schema of our DB

(def db-schema
  {:person/name {:db/unique :db.unique/identity}            ;; Persons are uniquely identified by thier :person/name
   :movie/title {:db/unique :db.unique/identity}            ;; Movies are uniquely identified by thier :movie/title
   :movie/director {:db/valueType :db.type/ref              ;; a Movie has one director
                    :db/cardinality :db.cardinality/one}
   :movie/actors {:db/valueType :db.type/ref                ;; a Movie has several Actors
                  :db/cardinality :db.cardinality/many}})

;; NOTE:
;; 1. In the schema, we only need to declare the attributes that have special behaviour;
;;    for instance, we didn't need to declare :movie/release-year or :person/gender.
;; 2. We only need to declare attributes, there's no notion of table / collection / entity-type.



;;;; Creating an empty db

(def empty-db (dt/db (dt/create-conn db-schema)))



;;;; Writing data

(def db
  (dt/db-with empty-db
    ;; This data structure below is a DataScript 'write', or 'transaction request'.
    ;; It is composed of maps, each map grouping several facts about an entity.
    ;; Notice how several facts about an entity can be spread over several maps and
    ;; will be automatically reconciled: for example, facts about the movie "The Good, the Bad and The Ugly"
    ;; are specified in 3 different maps.
    [{:movie/title "Star Wars: a New Hope"
      :movie/release-year 1977
      :movie/director {:person/name "George Lucas"}
      :movie/actors [{:person/name "Carrie Fisher"}
                     {:person/name "Harrison Ford"
                      :person/gender :gender/male}]}
     {:person/name "George Lucas"
      :person/gender :gender/male}
     {:person/name "Carrie Fisher"
      :person/gender :gender/female}
     {:movie/title "The Good, the Bad and The Ugly"
      :movie/release-year 1966}
     {:movie/title "The Good, the Bad and The Ugly"
      :movie/director {:person/name "Sergio Leone"}}
     {:movie/title "Gran Torino"
      :movie/release-year 2008}
     {:person/name "Clint Eastwood"
      :movie/_director [{:movie/title "Gran Torino"}]
      :movie/_actors [{:movie/title "The Good, the Bad and The Ugly"}]}
     ]))



;;;; Querying

(comment
  ;; Let's see the whole contents of the db
  db
  => #datascript/DB{:schema {:person/name {:db/unique :db.unique/identity},
                             :movie/title {:db/unique :db.unique/identity},
                             :movie/director {:db/valueType :db.type/ref, :db/cardinality :db.cardinality/one},
                             :movie/actors {:db/valueType :db.type/ref, :db/cardinality :db.cardinality/many}},
                    :datoms [[1 :movie/actors 3 536870913]
                             [1 :movie/actors 4 536870913]
                             [1 :movie/director 2 536870913]
                             [1 :movie/release-year 1977 536870913]
                             [1 :movie/title "Star Wars: a New Hope" 536870913]
                             [2 :person/gender :gender/male 536870913]
                             [2 :person/name "George Lucas" 536870913]
                             [3 :person/gender :gender/female 536870913]
                             [3 :person/name "Carrie Fisher" 536870913]
                             [4 :person/gender :gender/male 536870913]
                             [4 :person/name "Harrison Ford" 536870913]
                             [5 :movie/actors 8 536870913]
                             [5 :movie/director 6 536870913]
                             [5 :movie/release-year 1966 536870913]
                             [5 :movie/title "The Good, the Bad and The Ugly" 536870913]
                             [6 :person/name "Sergio Leone" 536870913]
                             [7 :movie/director 8 536870913]
                             [7 :movie/release-year 2008 536870913]
                             [7 :movie/title "Gran Torino" 536870913]
                             [8 :person/name "Clint Eastwood" 536870913]]}


  ;; Find the names of all persons in db
  (dt/q                                                     ;; Datalog API
    '[:find [?name ...] :where
      [?p :person/name ?name]]
    db)
  => ["Harrison Ford" "Clint Eastwood" "Carrie Fisher" "Sergio Leone" "George Lucas"]

  ;; Find the names of all actors in DB
  (dt/q
    '[:find [?name ...] :where
      [?m :movie/actors ?p]
      [?p :person/name ?name]]
    db)
  => ["Harrison Ford" "Clint Eastwood" "Carrie Fisher"]

  ;; What do we know about the Star Wars movie?
  (def star-wars
    (dt/entity db [:movie/title "Star Wars: a New Hope"]))  ;; Entity API

  star-wars                                                 ;; a DataScript Entity is a lazy, Map-like view of the database, centered around a given entity.
  => {:db/id 2}

  (type star-wars)
  => datascript.impl.entity.Entity                          ;; although an Entity displays like a Map, it is not an ordinary Map.


  (dt/touch star-wars)                                      ;; `dt/touch`: load all attributes in the Entity's cache.
  =>
  {:movie/actors #{{:db/id 4} {:db/id 3}},                  ;; in an Entity, the values are either scalars (strings, numbers, etc.)
   :movie/director {:db/id 1},                              ;; or other Entities.
   :movie/release-year 1977,
   :movie/title "Star Wars: a New Hope",
   :db/id 2}

  ;; who's the director ?
  (-> star-wars :movie/director dt/touch)                   ;; following a reference-typed attribute navigates to another Entity
  => {:person/gender :gender/male,
      :person/name "George Lucas",
      :db/id 1}

  ;; Find all movies released after 1970
  (->>
    (dt/q '[:find [?m ...] :in $ ?time :where
            [?m :movie/release-year ?t]
            [(> ?t ?time)]]
      db 1970)
    (map #(dt/entity db %))
    (sort-by :movie/release-year)
    (mapv dt/touch))
  =>
  [{:movie/actors #{{:db/id 4} {:db/id 3}},
    :movie/director {:db/id 1},
    :movie/release-year 1977,
    :movie/title "Star Wars: a New Hope",
    :db/id 2}
   {:movie/director {:db/id 8},
    :movie/release-year 2008,
    :movie/title "Gran Torino",
    :db/id 7}]

  )


