package com.istxicun.backend.distributed.key;

import com.istxicun.backend.common.identity.ApplicationIdentity;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public final class RedisKeyFactory {
  private final String prefix;

  public RedisKeyFactory(ApplicationIdentity identity) {
    prefix = identity.prefix("dist");
  }

  public String key(String feature, String... parts) {
    if (!feature.matches("[a-z-]{1,32}"))
      throw new IllegalArgumentException("Invalid feature name");
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (String part : parts) {
        Objects.requireNonNull(part, "Key part");
        byte[] bytes = part.getBytes(StandardCharsets.UTF_8);
        digest.update((bytes.length + ":").getBytes(StandardCharsets.UTF_8));
        digest.update(bytes);
      }
      return prefix + feature + ":" + HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }
}
