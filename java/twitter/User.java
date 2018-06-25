package twitter;

import java.util.List;
import java.util.UUID;

public class User {
  UUID id;
  @UserPrivate
  String email;
  String name;
  @RefTyped(cardinality="many")
  List<User> follows;
  @Derived
  int n_followers(){}
  @Derived
  @RefTyped(cardinality="many")
  List<Tweet> tweets(){}
}
