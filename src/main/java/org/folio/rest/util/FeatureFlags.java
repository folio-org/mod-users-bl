package org.folio.rest.util;

public final class FeatureFlags {

  private static final String KEY = "EUREKA_LOGIN_PERMS";

  private static final boolean EUREKA_LOGIN_PERMS =
    Boolean.parseBoolean(
      System.getProperty(KEY,
        System.getenv().getOrDefault(KEY, "false")
      ));

  private FeatureFlags() {}

  public static boolean isEurekaLoginPermsEnabled() {
    return EUREKA_LOGIN_PERMS;
  }
}
