package com.ankit.joblens.dashboard;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Routes the established dashboard URL to the React production bundle. */
@Controller
public class DashboardController {

  @GetMapping({"/", "/dashboard"})
  public String dashboard() {
    return "forward:/app/index.html";
  }
}
