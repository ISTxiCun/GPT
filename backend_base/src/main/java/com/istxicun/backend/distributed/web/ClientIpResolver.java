package com.istxicun.backend.distributed.web;

import com.istxicun.backend.common.exception.FailureCategory;
import com.istxicun.backend.distributed.DistributedException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Exact-IP proxy allowlist. No DNS lookups or implicit trust of forwarded headers. */
public final class ClientIpResolver {
  private final Set<String> trusted = new HashSet<>();

  public ClientIpResolver(List<String> trustedProxies) {
    for (String address : trustedProxies) trusted.add(normalize(address));
  }

  public String resolve(HttpServletRequest request) {
    String current = normalize(request.getRemoteAddr());
    if (!trusted.contains(current)) return current;
    String header = String.join(",", Collections.list(request.getHeaders("X-Forwarded-For")));
    if (header.isBlank()) return current;
    if (header.length() > 2048) throw invalid();
    String[] chain = header.split(",", -1);
    if (chain.length > 16) throw invalid();
    for (int i = chain.length - 1; i >= 0 && trusted.contains(current); i--) {
      try {
        current = normalize(chain[i].trim());
      } catch (IllegalArgumentException ex) {
        throw invalid();
      }
    }
    return current;
  }

  private static DistributedException invalid() {
    return new DistributedException(
        FailureCategory.INVALID_ARGUMENT, "INVALID_FORWARDED_FOR", "代理地址格式不合法");
  }

  private static String normalize(String input) {
    if (input == null
        || !(input.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")
            || (input.contains(":") && input.matches("[0-9a-fA-F:.]+"))))
      throw new IllegalArgumentException(
          "Trusted proxies must be literal IPv4/IPv6 addresses (no CIDR, hostnames or zone IDs)");
    try {
      return InetAddress.getByName(input).getHostAddress();
    } catch (UnknownHostException ex) {
      throw new IllegalArgumentException("Invalid IP literal", ex);
    }
  }
}
