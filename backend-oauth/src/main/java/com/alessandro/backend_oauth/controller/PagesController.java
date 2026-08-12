package com.alessandro.backend_oauth.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PagesController {

    @GetMapping("/public/info")
    public String publicInfo() {
        return "public-info"; // no requiere login
    }

    @GetMapping("/private/dashboard")
    public String dashboard() {
        return "dashboard"; // sí requiere login
    }
}