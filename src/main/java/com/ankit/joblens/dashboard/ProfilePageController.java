package com.ankit.joblens.dashboard;
import com.ankit.joblens.profile.ResumeProfileService; import org.springframework.stereotype.Controller; import org.springframework.ui.Model; import org.springframework.web.bind.annotation.GetMapping;
@Controller public class ProfilePageController { private final ResumeProfileService service; public ProfilePageController(ResumeProfileService service){this.service=service;} @GetMapping("/profile") public String profile(Model model){model.addAttribute("profile",service.profile()); return "profile";} }
