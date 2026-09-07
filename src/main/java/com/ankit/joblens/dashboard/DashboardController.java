package com.ankit.joblens.dashboard;

import com.ankit.joblens.workspace.WorkspaceCandidateProfileService;
import com.ankit.joblens.workspace.WorkspaceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Routes the established dashboard URL to the React production bundle. */
@Controller
public class DashboardController {
  private final WorkspaceContext workspaceContext;
  private final WorkspaceCandidateProfileService candidateProfiles;

  public DashboardController(
      WorkspaceContext workspaceContext, WorkspaceCandidateProfileService candidateProfiles) {
    this.workspaceContext = workspaceContext;
    this.candidateProfiles = candidateProfiles;
  }

  @GetMapping({"/", "/dashboard"})
  public String dashboard(HttpServletRequest request, HttpServletResponse response) {
    var workspaceId = workspaceContext.resolve(request, response);
    if (!candidateProfiles.hasCandidateProfile(workspaceId)) {
      return "redirect:/setup";
    }
    return "forward:/app/index.html";
  }
}
