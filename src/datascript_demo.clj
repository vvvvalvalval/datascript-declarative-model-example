(ns datascript-demo
  (:require [datascript.core :as dt]
            [datascript.core :as d]))

;;;; Declaring the (minimal required) schema of our DB

(def db-schema
  {:person/name {:db/unique :db.unique/identity}
   :movie/title {:db/unique :db.unique/identity}
   :movie/director {:db/valueType :db.type/ref
                    :db/cardinality :db.cardinality/one}
   :movie/actors {:db/valueType :db.type/ref
                  :db/cardinality :db.cardinality/many}})

;;;; Creating an empty db

(def empty-db (dt/db (dt/create-conn db-schema)))

;;;; Writing data

(def db
  (dt/db-with empty-db
    [{:person/name "George Lucas"
      :person/gender :gender/male}
     {:movie/title "Star Wars: a New Hope"
      :movie/release-year 1977
      :movie/director {:person/name "George Lucas"}
      :movie/actors [{:person/name "Carrie Fisher"
                      :person/gender :gender/female}
                     {:person/name "Harrison Ford"
                      :person/gender :gender/male}]}

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
  ;; Find the names of all persons in DB
  (dt/q
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
    (dt/entity db [:movie/title "Star Wars: a New Hope"]))

  star-wars
  => {:db/id 2}

  (dt/touch star-wars)
  =>
  {:movie/actors #{{:db/id 4} {:db/id 3}},
   :movie/director {:db/id 1},
   :movie/release-year 1977,
   :movie/title "Star Wars: a New Hope",
   :db/id 2}

  ;; who's the director ?
  (-> star-wars :movie/director dt/touch)
  => {:person/gender :gender/male, :person/name "George Lucas", :db/id 1}

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


