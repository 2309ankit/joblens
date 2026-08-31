package com.ankit.joblens.batchapi;

import com.ankit.joblens.discovery.SourceBoardRepository;
import com.ankit.joblens.discovery.SourceBoardView;
import com.ankit.joblens.workspace.WorkspaceContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/source-boards")
@Tag(
    name = "Discovered source boards",
    description =
        "Inspect company job boards that JobLens detected from trusted public job URLs. Users do not enter provider tokens.")
public class SourceBoardController {
  private final WorkspaceContext workspaceContext;
  private final SourceBoardRepository sourceBoards;

  public SourceBoardController(
      WorkspaceContext workspaceContext, SourceBoardRepository sourceBoards) {
    this.workspaceContext = workspaceContext;
    this.sourceBoards = sourceBoards;
  }

  @GetMapping
  @Operation(
      summary = "List automatically discovered company boards",
      description =
          "Returns this browser workspace's Greenhouse and Lever boards with their DISCOVERED, VALIDATED, or FAILED validation state. A board appears only when a public source exposes a direct official hosted-job URL.")
  public List<SourceBoardView> list(HttpServletRequest request, HttpServletResponse response) {
    return sourceBoards.list(workspaceContext.resolve(request, response));
  }
}
