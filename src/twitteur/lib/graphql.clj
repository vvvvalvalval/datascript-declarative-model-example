(ns twitteur.lib.graphql
  "Deriving GraphQL components from a Domain Model Representation."
  (:require [datascript.core :as dt]))

(defn- attribute-graphql-name
  [attribute]
  (-> attribute :twitteur.attribute/name name keyword))

(defn- entity-type-graphql-name
  [entity-type]
  (-> entity-type :twitteur.entity-type/name name keyword))

(defn- attribute->graphql-field-schema
  [attribute]
  (let [attribute-name (attribute-graphql-name attribute)]
    {attribute-name
     {:description (:twitteur.schema/doc attribute)
      :type
      (if (:twitteur.attribute/ref-typed? attribute)
        (let [target-type-name (-> attribute
                                 :twitteur.attribute.ref-typed/type
                                 entity-type-graphql-name)]
          (if (:twitteur.attribute.ref-typed/many? attribute)
            (list 'list target-type-name)
            target-type-name))
        (case (:twitteur.attribute.scalar/type attribute)
          :string 'String
          :long 'Int
          :uuid 'Uuid
          ;; etc.
          ))}}))

(defn- entity-type->graphql-object-schema [entity-type]
  (let [graphql-type-name (entity-type-graphql-name entity-type)]
    {graphql-type-name
     {:description (:twitteur.schema/doc entity-type)
      :fields
      (->> entity-type
        :twitteur.entity-type/attributes
        (map attribute->graphql-field-schema)
        (reduce merge (sorted-map)))}}))

(defn derive-graphql-schema
  "Generates a GraphQL schema from a Domain Model Representation.

  Given a DataScript database `model-db` (the Domain Model Representation),
  returns a data structure as accepted by Lacinia for defining a GraphQL schema."
  [model-db]
  {:enums {}
   :queries {}
   :objects
   (->> model-db
     (dt/q '[:find [?entity-type ...] :where
             [?entity-type :twitteur.entity-type/name]])
     (map (fn [eid]
            (let [entity-type (dt/entity model-db eid)]
              (entity-type->graphql-object-schema entity-type))))
     (reduce merge (sorted-map)))})

(comment
  ;;;; Example (see http://lacinia.readthedocs.io/en/latest/overview.html#schema)
  (require 'twitteur.ds-model)

  (derive-graphql-schema twitteur.ds-model/model-db)
  =>
  {:enums {},
   :queries {},
   :objects
   {:Tweet
    {:description "a Tweet is a short message posted by a User on Twitteur, published to all her Followers.",
     :fields
     {:author
      {:description "The Twitteur user who wrote this Tweet.",
       :type :User},
      :content
      {:description "The textual message of this Tweet",
       :type String},
      :id
      {:description "The unique ID of this Tweet",
       :type Uuid},
      :time
      {:description "The time at which this Tweet was published, as a timestamp.",
       :type Int}}},
    :User
    {:description "a User is a person who has signed up to Twitteur.",
     :fields
     {:email {:description "The email address of this user (not visible to other users).",
              :type String},
      :follows {:description "The Twitteur users whom this user follows.", :type (list :User)},
      :id {:description "The unique ID of this user.",
           :type Uuid},
      :n_followers {:description "How many users follow this user.",
                    :type Int},
      :name {:description "The public name of this user on Twitteur.",
             :type String},
      :tweets {:description "The tweets posted by this user.",
               :type (list :Tweet)}}}}}
  )