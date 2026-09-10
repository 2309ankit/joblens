package com.ankit.joblens.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class WorkspaceContextTests {

  @Test
  void marksNewWorkspaceCookieSecureWhenConfiguredForHttps() {
    RecordingWorkspaceRepository repository = new RecordingWorkspaceRepository();
    WorkspaceContext context = new WorkspaceContext(repository, true);
    MockHttpServletResponse response = new MockHttpServletResponse();

    UUID workspaceId = context.resolve(new MockHttpServletRequest(), response);

    assertThat(repository.createdWorkspaceId).isEqualTo(workspaceId);
    assertThat(response.getHeader("Set-Cookie"))
        .contains(WorkspaceContext.COOKIE_NAME + "=" + workspaceId)
        .contains("Path=/")
        .contains("Max-Age=31536000")
        .contains("Secure")
        .contains("HttpOnly")
        .contains("SameSite=Lax");
  }

  @Test
  void keepsLocalHttpWorkspaceCookieUsableByDefault() {
    RecordingWorkspaceRepository repository = new RecordingWorkspaceRepository();
    WorkspaceContext context = new WorkspaceContext(repository, false);
    MockHttpServletResponse response = new MockHttpServletResponse();

    context.resolve(new MockHttpServletRequest(), response);

    assertThat(response.getHeader("Set-Cookie")).doesNotContain("Secure");
  }

  private static final class RecordingWorkspaceRepository extends WorkspaceRepository {
    private UUID createdWorkspaceId;

    private RecordingWorkspaceRepository() {
      super(null);
    }

    @Override
    public boolean exists(UUID workspaceId) {
      return false;
    }

    @Override
    public void create(UUID workspaceId) {
      createdWorkspaceId = workspaceId;
    }
  }
}
