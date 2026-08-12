package com.alessandro.backend_saml.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home(Authentication authentication, Model model) {
        if (authentication != null && authentication.getPrincipal() instanceof Saml2AuthenticatedPrincipal principal) {
            model.addAttribute("name", principal.getName());
            model.addAttribute("attributes", principal.getAttributes());
        }

        return "home";
    }
}