(ns twitteur.ds-model
  (:require [datascript.core :as dt]
            [twitteur.utils.model.dml :as dml]))

;;;; Model meta-data
;; These 2 values are DataScript Transaction Requests, i.e data structures defining writes to a DataScript database
;; NOTE in a real-world codebase, these 2 would be in different namespaces.

(def user-model
  [{:entity-type/name :twitteur/User
    :doc "a User is a person who has signed up to Twitteur."
    :entity-type/attributes
    [{:attribute/name :user/id
      :doc "The unique ID of this user."
      :attribute/ref-typed? false
      :attribute.scalar/type :uuid
      :attribute/unique-identity true}
     {:attribute/name :user/email
      :doc "The email address of this user (not visible to other users)."
      :attribute/ref-typed? false
      :attribute.scalar/type :string
      :attribute.twitteur.security/private? true}           ;; here's a domain-specific security rule
     {:attribute/name :user/name
      :doc "The public name of this user on Twitteur."
      :attribute/ref-typed? false
      :attribute.scalar/type :string}
     {:attribute/name :user/follows
      :doc "The Twitteur users whom this user follows."
      :attribute/ref-typed? true                            ;; this attribute is a reference-typed
      :attribute.ref-typed/many? true
      :attribute.ref-typed/type {:entity-type/name :twitteur/User}}
     {:attribute/name :user/n_followers
      :doc "How many users follow this user."
      :attribute/ref-typed? false
      :attribute.ref-typed/many? true
      :attribute.scalar/type :long
      :attribute/derived? true}                             ;; this attribute is not stored in DB
     {:attribute/name :user/tweets
      :doc "The tweets posted by this user."
      :attribute/ref-typed? true
      :attribute.ref-typed/many? true
      :attribute.ref-typed/type {:entity-type/name :twitteur/Tweet}
      :attribute/derived? true}
     ]}])

(def tweet-model
  ;; NOTE: to demonstrate the flexibility of DataScript, we choose a different but equivalent data layout
  ;; in this one, we define the Entity Type and the Attributes separately
  [;; Entity Type
   {:entity-type/name :twitteur/Tweet
    :doc "a Tweet is a short message posted by a User on Twitteur, published to all her Followers."
    :entity-type/attributes
    [{:attribute/name :tweet/id}
     {:attribute/name :tweet/content}
     {:attribute/name :tweet/author}
     {:attribute/name :tweet/time}]}
   ;; Attributes
   {:attribute/name :tweet/id
    :doc "The unique ID of this Tweet"
    :attribute/ref-typed? false
    :attribute.scalar/type :uuid
    :attribute/unique-identity true}
   {:attribute/name :tweet/content
    :doc "The textual message of this Tweet"
    :attribute/ref-typed? false
    :attribute.scalar/type :string}
   {:attribute/name :tweet/author
    :doc "The Twitteur user who wrote this Tweet."
    :attribute/ref-typed? true
    :attribute.ref-typed/many? false
    :attribute.ref-typed/type {:entity-type/name :twitteur/User}}
   {:attribute/name :tweet/time
    :doc "The time at which this Tweet was published, as a timestamp."
    :attribute/ref-typed? false
    :attribute.scalar/type :long}])

;;;; Writing this metadata to a DataScript db

(def meta-schema
  {:entity-type/name {:db/unique :db.unique/identity}
   :entity-type/attributes {:db/valueType :db.type/ref
                            :db/cardinality :db.cardinality/many}
   :attribute/name {:db/unique :db.unique/identity}
   :attribute.ref-typed/type {:db/valueType :db.type/ref
                              :db/cardinality :db.cardinality/one}})

(defn empty-model-db
  []
  (let [conn (dt/create-conn meta-schema)]
    (dt/db conn)))

(def model-db
  (dt/db-with
    (empty-model-db)
    ;; Composing DataScript transactions is as simple as that: concat
    (concat
      user-model
      tweet-model)))

;;;; Let's query this a bit

(comment
  ;; What are all the attributes names in our Domain Model ?
  (sort
    (dt/q
      '[:find [?attrName ...] :where
        [?attr :attribute/name ?attrName]]
      model-db))
  => (:tweet/author :tweet/content :tweet/id :tweet/time :user/email :user/follows :user/id :user/n_followers :user/name)

  ;; What do we know about :tweet/author?
  (def tweet-author-attr
    (dt/entity model-db [:attribute/name :tweet/author]))

  tweet-author-attr
  => {:db/id 10}

  (dt/touch tweet-author-attr)
  =>
  {:doc "The Twitteur user who wrote this Tweet.",
   :attribute/name :tweet/author,
   :attribute/ref-typed? true,
   :attribute.ref-typed/many? false,
   :attribute.ref-typed/type {:db/id 1},
   :db/id 10}

  (-> tweet-author-attr :attribute.ref-typed/type dt/touch)
  =>
  {:doc "a User is a person who has signed up to Twitteur.",
   :entity-type/attributes #{{:db/id 4} {:db/id 6} {:db/id 3} {:db/id 2} {:db/id 5}},
   :entity-type/name :twitteur/User,
   :db/id 1}

  ;; What attributes have type :twitteur/User?
  (dt/q '[:find ?attrName ?to-many? :in $ ?type :where
          [?attr :attribute.ref-typed/type ?type]
          [?attr :attribute/name ?attrName]
          [?attr :attribute.ref-typed/many? ?to-many?]]
    model-db [:entity-type/name :twitteur/User])
  => #{[:tweet/author false] [:user/follows true]}

  ;; What attributes are derived, and therefore should not be stored in the database?
  (->>
    (dt/q '[:find [?attr ...] :where
            [?attr :attribute/derived? true]]
      model-db)
    (map #(dt/entity model-db %))
    (sort-by :attribute/name)
    (mapv dt/touch))
  =>
  [{:doc "The tweets posted by this user.",
    :attribute/derived? true,
    :attribute/name :user/follows,
    :attribute/ref-typed? true,
    :attribute.ref-typed/many? true,
    :attribute.ref-typed/type {:db/id 7},
    :db/id 5}
   {:doc "How many users follow this user.",
    :attribute/derived? true,
    :attribute/name :user/n_followers,
    :attribute/ref-typed? false,
    :attribute.ref-typed/many? true,
    :attribute.scalar/type :long,
    :db/id 6}]

  ;; What attributes are private, and therefore should not be exposed publicly?
  (set
    (dt/q '[:find [?attrName ...] :where
            [?attr :attribute.twitteur.security/private? true]
            [?attr :attribute/name ?attrName]]
      model-db))
  => #{:user/email}
  )


;;;; Let's make our schema code more readable,
;;;; by using some concision helpers

(require '[twitteur.utils.model.dml :as dml])

(def user-model2
  [{:entity-type/name :twitteur/User
    :doc "a User is a person who has signed up to Twitteur."
    :entity-type/attributes
    [(dml/scalar :user/id :uuid "The unique ID of this user."
       {:attribute/unique-identity true})
     (dml/scalar :user/email :string "The email address of this user (not visible to other users)."
       {:attribute.twitteur.security/private? true})
     (dml/scalar :user/name :string "The public name of this user on Twitteur.")
     (dml/to-many :user/follows :twitteur/User "The Twitteur users whom this user follows.")
     (dml/scalar :user/n_followers :long "How many users follow this user."
       {:attribute/derived? true})
     (dml/to-many :user/tweets :twitteur/Tweet "The tweets posted by this user."
       {:attribute/derived? true})
     ]}])

(def tweet-model2
  ;; NOTE: to demonstrate the flexibility of DataScript, we choose a different but equivalent data layout
  ;; in this one, we define the Entity Type and the Attributes separately
  [;; Entity Type
   {:entity-type/name :twitteur/Tweet
    :doc "a Tweet is a short message posted by a User on Twitteur, published to all her Followers."
    :entity-type/attributes
    [(dml/scalar :tweet/id :uuid "The unique ID of this Tweet"
       {:attribute/unique-identity true})
     (dml/scalar :tweet/content :string "The textual message of this Tweet")
     (dml/to-one :tweet/author :twitteur/User "The Twitteur user who wrote this Tweet.")
     (dml/scalar :tweet/time :long "The time at which this Tweet was published, as a timestamp.")
     ]}])
