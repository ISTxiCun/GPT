package com.istxicun.backend.common.identity;

/** Single immutable source of application/environment key isolation. */
public record ApplicationIdentity(String namespace, String environment) {
  public ApplicationIdentity {
    if (namespace == null
        || !namespace.matches("[A-Za-z0-9_-]{1,64}")
        || environment == null
        || !environment.matches("[A-Za-z0-9_-]{1,64}"))
      throw new IllegalArgumentException(
          "Application namespace and environment must match [A-Za-z0-9_-]{1,64}; set APP_ENV"
              + " explicitly");
  }

  public String prefix(String area) {
    if (area == null || !area.matches("[a-z-]{1,32}"))
      throw new IllegalArgumentException("Invalid key area");
    return namespace + ":" + environment + ":" + area + ":";
  }
}
