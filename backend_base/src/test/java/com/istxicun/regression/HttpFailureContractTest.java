package com.istxicun.regression;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.istxicun.backend.common.exception.*;
import com.istxicun.backend.config.RequestIdFilter;
import com.istxicun.backend.web.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

class HttpFailureContractTest {
  @RestController
  static class Probe {
    @GetMapping("/failure/{category}")
    void failure(@PathVariable FailureCategory category) {
      throw new ApplicationException(
          category,
          "CORE_FAILURE",
          "failure",
          category == FailureCategory.RATE_LIMITED ? 3L : null,
          null);
    }
  }

  @Test
  void everyCoreCategoryMapsAtTheWebBoundaryAndPreservesResponseMetadata() throws Exception {
    var mvc =
        MockMvcBuilders.standaloneSetup(new Probe())
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new RequestIdFilter())
            .build();
    var expected =
        java.util.Map.of(
            FailureCategory.INVALID_ARGUMENT,
            400,
            FailureCategory.UNAUTHENTICATED,
            401,
            FailureCategory.FORBIDDEN,
            403,
            FailureCategory.NOT_FOUND,
            404,
            FailureCategory.CONFLICT,
            409,
            FailureCategory.RATE_LIMITED,
            429,
            FailureCategory.UNAVAILABLE,
            503,
            FailureCategory.INTERNAL,
            500);
    for (var entry : expected.entrySet()) {
      var result =
          mvc.perform(get("/failure/" + entry.getKey()))
              .andExpect(status().is(entry.getValue()))
              .andExpect(jsonPath("$.code").value("CORE_FAILURE"))
              .andExpect(jsonPath("$.requestId").isNotEmpty());
      if (entry.getKey() == FailureCategory.RATE_LIMITED)
        result.andExpect(header().string("Retry-After", "3"));
    }
  }
}
