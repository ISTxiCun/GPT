package com.istxicun.backend.observability;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bound external context before the server observation filter; baggage is not accepted. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceHeadersFilter extends OncePerRequestFilter {
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    chain.doFilter(
        new HttpServletRequestWrapper(request) {
          private boolean hidden(String name) {
            if (name.equalsIgnoreCase("baggage")) return true;
            if (name.equalsIgnoreCase("traceparent")) {
              String value = super.getHeader(name);
              return value != null
                  && (!value.matches("00-[0-9a-f]{32}-[0-9a-f]{16}-0[01]")
                      || value.substring(3, 35).equals("0".repeat(32))
                      || value.substring(36, 52).equals("0".repeat(16)));
            }
            return name.equalsIgnoreCase(
                "tracestate"); // no vendor state is required by this application
          }

          @Override
          public String getHeader(String name) {
            return hidden(name) ? null : super.getHeader(name);
          }

          @Override
          public Enumeration<String> getHeaders(String name) {
            return hidden(name) ? Collections.emptyEnumeration() : super.getHeaders(name);
          }

          @Override
          public Enumeration<String> getHeaderNames() {
            return Collections.enumeration(
                Collections.list(super.getHeaderNames()).stream().filter(n -> !hidden(n)).toList());
          }
        },
        response);
  }
}
