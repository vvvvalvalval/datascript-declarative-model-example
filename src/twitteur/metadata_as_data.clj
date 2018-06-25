(ns twitteur.metadata-as-data)

(def domain-model-metadata
  {:types
   [{:entity-type/name :twitteur/User
     :entity-type/attributes
     [{:attribute/name :user/email
       :attribute.scalar/type :string
       :twitteur.security/private? true}
      {:attribute/name :user/tweets
       :attribute/ref-typed? true
       :attribute.ref-typed/type :twitteur/Tweet
       :attribute.ref-typed/many? true}
      ...]}
    {:entity-type/name :twitteur/Tweet
     :entity-type/attributes
     [...]}]})
