package com.ankit.joblens.workspace;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

@Service
public class WorkspaceContext {
  public static final String COOKIE_NAME = "JOBLENS_WORKSPACE";
  private final WorkspaceRepository repository;
  private final boolean secureCookie;

  public WorkspaceContext(
      WorkspaceRepository repository,
      @Value("${joblens.workspace.cookie-secure:false}") boolean secureCookie) {
    this.repository = repository;
    this.secureCookie = secureCookie;
  }

  public UUID resolve(HttpServletRequest request, HttpServletResponse response) {
    UUID workspaceId = read(request);
    if (workspaceId == null || !repository.exists(workspaceId)) {
      workspaceId = UUID.randomUUID();
      repository.create(workspaceId);
      ResponseCookie cookie =
          ResponseCookie.from(COOKIE_NAME, workspaceId.toString())
              .httpOnly(true)
              .secure(secureCookie)
              .sameSite("Lax")
              .path("/")
              .maxAge(Duration.ofDays(365))
              .build();
      response.addHeader("Set-Cookie", cookie.toString());
    }
    return workspaceId;
  }

  private static UUID read(HttpServletRequest request) {
    if (request.getCookies() == null) {
      return null;
    }
    return Arrays.stream(request.getCookies())
        .filter(cookie -> COOKIE_NAME.equals(cookie.getName()))
        .map(Cookie::getValue)
        .map(WorkspaceContext::parse)
        .filter(java.util.Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  private static UUID parse(String value) {
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException exception) {
      return null;
    }
  }
}
