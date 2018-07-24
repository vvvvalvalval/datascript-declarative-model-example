(ns twitteur.ds-model
  (:require [datascript.core :as dt]))

;;;; Model meta-data
;; These 2 values are DataScript Transaction Requests, i.e data structures defining writes to a DataScript database
;; NOTE in a real-world codebase, these 2 would typically live in different files.

(def user-model
  [{:twitteur.entity-type/name :twitteur/User
    :twitteur.schema/doc "a User is a person who has signed up to Twitteur."
    :twitteur.entity-type/attributes
    [{:twitteur.attribute/name :user/id
      :twitteur.schema/doc "The unique ID of this user."
      :twitteur.attribute/ref-typed? false
      :twitteur.attribute.scalar/type :uuid
      :twitteur.attribute/unique-identity true}
     {:twitteur.attribute/name :user/email
      :twitteur.schema/doc "The email address of this user (not visible to other users)."
      :twitteur.attribute/ref-typed? false
      :twitteur.attribute.scalar/type :string
      :twitteur.attribute.security/private? true}                    ;; here's a domain-specific security rule
     {:twitteur.attribute/name :user/name
      :twitteur.schema/doc "The public name of this user on Twitteur."
      :twitteur.attribute/ref-typed? false
      :twitteur.attribute.scalar/type :string}
     {:twitteur.attribute/name :user/follows
      :twitteur.schema/doc "The Twitteur users whom this user follows."
      :twitteur.attribute/ref-typed? true                            ;; this attribute is a reference-typed
      :twitteur.attribute.ref-typed/many? true
      :twitteur.attribute.ref-typed/type {:twitteur.entity-type/name :twitteur/User}}
     {:twitteur.attribute/name :user/n_followers
      :twitteur.schema/doc "How many users follow this user."
      :twitteur.attribute/ref-typed? false
      :twitteur.attribute.ref-typed/many? true
      :twitteur.attribute.scalar/type :long
      :twitteur.attribute/derived? true}                             ;; this attribute is not stored in DB
     {:twitteur.attribute/name :user/tweets
      :twitteur.schema/doc "The tweets posted by this user."
      :twitteur.attribute/ref-typed? true
      :twitteur.attribute.ref-typed/many? true
      :twitteur.attribute.ref-typed/type {:twitteur.entity-type/name :twitteur/Tweet}
      :twitteur.attribute/derived? true}
     ]}])

(def tweet-model
  ;; NOTE: to demonstrate the flexibility of DataScript, we choose a different but equivalent data layout
  ;; in this one, we define the Entity Type and the Attributes separately
  [;; Entity Type
   {:twitteur.entity-type/name :twitteur/Tweet
    :twitteur.schema/doc "a Tweet is a short message posted by a User on Twitteur, published to all her Followers."
    :twitteur.entity-type/attributes
    [{:twitteur.attribute/name :tweet/id}
     {:twitteur.attribute/name :tweet/content}
     {:twitteur.attribute/name :tweet/author}
     {:twitteur.attribute/name :tweet/time}]}
   ;; Attributes
   {:twitteur.attribute/name :tweet/id
    :twitteur.schema/doc "The unique ID of this Tweet"
    :twitteur.attribute/ref-typed? false
    :twitteur.attribute.scalar/type :uuid
    :twitteur.attribute/unique-identity true}
   {:twitteur.attribute/name :tweet/content
    :twitteur.schema/doc "The textual message of this Tweet"
    :twitteur.attribute/ref-typed? false
    :twitteur.attribute.scalar/type :string}
   {:twitteur.attribute/name :tweet/author
    :twitteur.schema/doc "The Twitteur user who wrote this Tweet."
    :twitteur.attribute/ref-typed? true
    :twitteur.attribute.ref-typed/many? false
    :twitteur.attribute.ref-typed/type {:twitteur.entity-type/name :twitteur/User}}
   {:twitteur.attribute/name :tweet/time
    :twitteur.schema/doc "The time at which this Tweet was published, as a timestamp."
    :twitteur.attribute/ref-typed? false
    :twitteur.attribute.scalar/type :long}])

;;;; Writing this metadata to a DataScript db
(require '[datascript.core :as dt])

(def meta-schema
  {:twitteur.entity-type/name {:db/unique :db.unique/identity}
   :twitteur.entity-type/attributes {:db/valueType :db.type/ref
                                     :db/cardinality :db.cardinality/many}
   :twitteur.attribute/name {:db/unique :db.unique/identity}
   :twitteur.attribute.ref-typed/type {:db/valueType :db.type/ref
                                       :db/cardinality :db.cardinality/one}})

(defn empty-model-db
  []
  (let [conn (dt/create-conn meta-schema)]
    (dt/db conn)))

(def model-db
  "A DataScript database value, holding a representation of our Domain Model."
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
        [?attr :twitteur.attribute/name ?attrName]]
      model-db))
  => (:tweet/author :tweet/content :tweet/id :tweet/time :user/email :user/follows :user/id :user/n_followers :user/name)

  ;; What do we know about :tweet/author?
  (def tweet-author-attr
    (dt/entity model-db [:twitteur.attribute/name :tweet/author]))

  tweet-author-attr
  => {:db/id 10}

  (dt/touch tweet-author-attr)
  =>
  {:twitteur.schema/doc "The Twitteur user who wrote this Tweet.",
   :twitteur.attribute/name :tweet/author,
   :twitteur.attribute/ref-typed? true,
   :twitteur.attribute.ref-typed/many? false,
   :twitteur.attribute.ref-typed/type {:db/id 1},
   :db/id 10}

  (-> tweet-author-attr :twitteur.attribute.ref-typed/type dt/touch)
  =>
  {:twitteur.schema/doc "a User is a person who has signed up to Twitteur.",
   :twitteur.entity-type/attributes #{{:db/id 4} {:db/id 6} {:db/id 3} {:db/id 2} {:db/id 5}},
   :twitteur.entity-type/name :twitteur/User,
   :db/id 1}

  ;; What attributes have type :twitteur/User?
  (dt/q '[:find ?attrName ?to-many? :in $ ?type :where
          [?attr :twitteur.attribute.ref-typed/type ?type]
          [?attr :twitteur.attribute/name ?attrName]
          [?attr :twitteur.attribute.ref-typed/many? ?to-many?]]
    model-db [:twitteur.entity-type/name :twitteur/User])
  => #{[:tweet/author false] [:user/follows true]}

  ;; What attributes are derived, and therefore should not be stored in the database?
  (->>
    (dt/q '[:find [?attr ...] :where
            [?attr :twitteur.attribute/derived? true]]
      model-db)
    (map #(dt/entity model-db %))
    (sort-by :twitteur.attribute/name)
    (mapv dt/touch))
  =>
  [{:twitteur.schema/doc "The tweets posted by this user.",
    :twitteur.attribute/derived? true,
    :twitteur.attribute/name :user/follows,
    :twitteur.attribute/ref-typed? true,
    :twitteur.attribute.ref-typed/many? true,
    :twitteur.attribute.ref-typed/type {:db/id 7},
    :db/id 5}
   {:twitteur.schema/doc "How many users follow this user.",
    :twitteur.attribute/derived? true,
    :twitteur.attribute/name :user/n_followers,
    :twitteur.attribute/ref-typed? false,
    :twitteur.attribute.ref-typed/many? true,
    :twitteur.attribute.scalar/type :long,
    :db/id 6}]

  ;; What attributes are private, and therefore should not be exposed publicly?
  (set
    (dt/q '[:find [?attrName ...] :where
            [?attr :twitteur.attribute.security/private? true]
            [?attr :twitteur.attribute/name ?attrName]]
      model-db))
  => #{:user/email}
  )


;;;; Let's make our schema code more readable,
;;;; by using some concision helpers

(require '[twitteur.utils.model.dml :as dml])

(def user-model
  [(dml/entity-type :twitteur/User
     "a User is a person who has signed up to Twitteur."
     {:twitteur.entity-type/attributes
      [(dml/scalar :user/id :uuid (dml/unique-id) "The unique ID of this user.")
       (dml/scalar :user/email :string (dml/private) "The email address of this user (not visible to other users).")
       (dml/scalar :user/name :string "The public name of this user on Twitteur.")
       (dml/to-many :user/follows :twitteur/User "The Twitteur users whom this user follows.")
       (dml/scalar :user/n_followers :long (dml/derived) "How many users follow this user.")
       (dml/to-many :user/tweets :twitteur/Tweet (dml/derived) "The tweets posted by this user.")
       ]})])

(def tweet-model
  [(dml/entity-type :twitteur/Tweet
     "a Tweet is a short message posted by a User on Twitteur, published to all her Followers."
     {:twitteur.entity-type/attributes
      [(dml/scalar :tweet/id :uuid "The unique ID of this Tweet" (dml/unique-id))
       (dml/scalar :tweet/content :string "The textual message of this Tweet")
       (dml/to-one :tweet/author :twitteur/User "The Twitteur user who wrote this Tweet.")
       (dml/scalar :tweet/time :long "The time at which this Tweet was published, as a timestamp.")
       ]})])

;; Note that there's no macro magic above: user-model and tweet-model are still plain data structures,
;; we just use the dml/... functions to assemble them in a more readable way.
;; In particular, you can evaluate any sub-expression above in the REPL and see exactly
;; how it translates to a data structure.