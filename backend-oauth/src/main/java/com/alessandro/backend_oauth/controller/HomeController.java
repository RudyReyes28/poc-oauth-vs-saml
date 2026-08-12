package com.alessandro.backend_oauth.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home(@AuthenticationPrincipal OAuth2User principal, Model model) {
        if (principal != null) {
            model.addAttribute("attributes", principal.getAttributes());

            boolean esOidc = principal instanceof OidcUser;
            model.addAttribute("esOidc", esOidc);

            if (esOidc) {
                OidcUser oidcUser = (OidcUser) principal;
                model.addAttribute("idToken", oidcUser.getIdToken().getTokenValue());
                model.addAttribute("idTokenClaims", oidcUser.getIdToken().getClaims());
            }
        }
        return "home";
    }
}