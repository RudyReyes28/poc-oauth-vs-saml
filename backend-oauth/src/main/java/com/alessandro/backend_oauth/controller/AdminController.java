package com.alessandro.backend_oauth.controller;

import com.alessandro.backend_oauth.entity.AppUser;
import com.alessandro.backend_oauth.entity.Role;
import com.alessandro.backend_oauth.repository.AppUserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AppUserRepository appUserRepository;

    public AdminController(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @GetMapping
    public String panel(Model model) {
        model.addAttribute("users", appUserRepository.findAll());
        return "admin";
    }

    @PostMapping("/{id}/toggle-role")
    public String toggleRole(@PathVariable Long id) {
        AppUser user = appUserRepository.findById(id).orElseThrow();
        user.setRole(user.getRole() == Role.ADMIN ? Role.USER : Role.ADMIN);
        appUserRepository.save(user);
        return "redirect:/admin";
    }
}