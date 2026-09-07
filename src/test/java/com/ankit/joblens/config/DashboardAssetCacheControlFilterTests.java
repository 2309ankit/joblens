package com.ankit.joblens.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class DashboardAssetCacheControlFilterTests {

  private final DashboardAssetCacheControlFilter filter = new DashboardAssetCacheControlFilter();

  @Test
  void preventsCachingTheDashboardEntryPoint() throws Exception {
    MockHttpServletResponse response = apply("/dashboard");

    assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
  }

  @Test
  void marksContentHashedAssetsAsImmutable() throws Exception {
    MockHttpServletResponse response = apply("/app/assets/index-example.js");

    assertThat(response.getHeader("Cache-Control"))
        .isEqualTo("max-age=31536000, public, immutable");
  }

  private MockHttpServletResponse apply(String path) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, new MockFilterChain());
    return response;
  }
}
