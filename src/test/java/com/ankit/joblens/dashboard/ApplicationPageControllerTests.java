package com.ankit.joblens.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.ankit.joblens.workspace.WorkspaceCandidateProfileService;
import com.ankit.joblens.workspace.WorkspaceContext;
import com.ankit.joblens.workspace.WorkspaceNotReadyException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ConcurrentModel;

class ApplicationPageControllerTests {

  @Test
  void redirectsAnIncompleteWorkspaceToSetup() {
    UUID workspaceId = UUID.randomUUID();
    WorkspaceContext workspaceContext =
        new WorkspaceContext(null, false) {
          @Override
          public UUID resolve(HttpServletRequest request, HttpServletResponse response) {
            return workspaceId;
          }
        };
    WorkspaceCandidateProfileService candidateProfiles =
        new WorkspaceCandidateProfileService(null) {
          @Override
          public long requireCandidateProfile(UUID ignored) {
            throw new WorkspaceNotReadyException();
          }
        };
    var controller = new ApplicationPageController(workspaceContext, candidateProfiles, null, null);

    String view =
        controller.applications(
            new ConcurrentModel(), new MockHttpServletRequest(), new MockHttpServletResponse());

    assertThat(view).isEqualTo("redirect:/setup");
  }

  @Test
  void forwardsAReadyWorkspaceToTheReactApplication() {
    WorkspaceContext workspaceContext =
        new WorkspaceContext(null, false) {
          @Override
          public UUID resolve(HttpServletRequest request, HttpServletResponse response) {
            return UUID.randomUUID();
          }
        };
    WorkspaceCandidateProfileService candidateProfiles =
        new WorkspaceCandidateProfileService(null) {
          @Override
          public long requireCandidateProfile(UUID ignored) {
            return 1L;
          }
        };
    var controller = new ApplicationPageController(workspaceContext, candidateProfiles, null, null);

    String view =
        controller.applications(
            new ConcurrentModel(), new MockHttpServletRequest(), new MockHttpServletResponse());

    assertThat(view).isEqualTo("forward:/app/index.html");
  }
}
