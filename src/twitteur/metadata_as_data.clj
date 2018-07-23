(ns twitteur.metadata-as-data)

(def domain-model-metadata
  {:types
   [{:twitteur.entity-type/name :twitteur/User
     :twitteur.entity-type/attributes
     [{:twitteur.attribute/name :user/email
       :twitteur.attribute.scalar/type :string
       :twitteur.security/private? true}
      {:twitteur.attribute/name :user/tweets
       :twitteur.attribute/ref-typed? true
       :twitteur.attribute.ref-typed/type :twitteur/Tweet
       :twitteur.attribute.ref-typed/many? true}
      ...]}
    {:twitteur.entity-type/name :twitteur/Tweet
     :twitteur.entity-type/attributes
     [...]}]})
