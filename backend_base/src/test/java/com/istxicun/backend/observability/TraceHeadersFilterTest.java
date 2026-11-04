package com.istxicun.backend.observability;

import static org.assertj.core.api.Assertions.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;

class TraceHeadersFilterTest {
  @Test
  void dropsInvalidAndSensitiveContextBeforeObservation() throws Exception {
    var request = new MockHttpServletRequest();
    request.addHeader("traceparent", "00-" + "0".repeat(32) + "-" + "1".repeat(16) + "-01");
    request.addHeader("baggage", "token=secret");
    request.addHeader("tracestate", "vendor=secret");
    new TraceHeadersFilter()
        .doFilter(
            request,
            new MockHttpServletResponse(),
            (req, res) -> {
              var headers = (HttpServletRequest) req;
              assertThat(headers.getHeader("traceparent")).isNull();
              assertThat(headers.getHeader("baggage")).isNull();
              assertThat(headers.getHeader("tracestate")).isNull();
              assertThat(Collections.list(headers.getHeaderNames())).isEmpty();
            });
  }

  @Test
  void preservesValidW3cParent() throws Exception {
    var request = new MockHttpServletRequest();
    String parent = "00-" + "1".repeat(32) + "-" + "2".repeat(16) + "-01";
    request.addHeader("traceparent", parent);
    new TraceHeadersFilter()
        .doFilter(
            request,
            new MockHttpServletResponse(),
            (req, res) ->
                assertThat(((HttpServletRequest) req).getHeader("traceparent")).isEqualTo(parent));
  }
}
