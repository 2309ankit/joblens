package com.ankit.joblens.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Keeps the HTML entry point fresh while allowing content-hashed dashboard assets to stay cached.
 */
@Component
public class DashboardAssetCacheControlFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String path = request.getRequestURI();
    if (path.equals("/") || path.equals("/dashboard") || path.equals("/app/index.html")) {
      response.setHeader("Cache-Control", CacheControl.noStore().getHeaderValue());
    } else if (path.startsWith("/app/assets/")) {
      response.setHeader(
          "Cache-Control",
          CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable().getHeaderValue());
    }
    filterChain.doFilter(request, response);
  }
}
