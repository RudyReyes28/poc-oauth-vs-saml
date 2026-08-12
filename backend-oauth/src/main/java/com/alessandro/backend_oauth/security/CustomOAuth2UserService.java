package com.alessandro.backend_oauth.security;

import com.alessandro.backend_oauth.entity.AppUser;
import com.alessandro.backend_oauth.entity.Role;
import com.alessandro.backend_oauth.repository.AppUserRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final AppUserRepository appUserRepository;

    public CustomOAuth2UserService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        OAuth2User oauth2User = super.loadUser(userRequest);

        String provider = userRequest.getClientRegistration().getRegistrationId(); // "github"

        // GitHub no siempre expone el email en /user si no está público;
        // para esta POC usamos el "login" (username) como identificador si no hay email.
        String email = oauth2User.getAttribute("email") != null
                ? oauth2User.getAttribute("email")
                : oauth2User.getAttribute("login") + "@github.local";

        AppUser appUser = buscarOCrearUsuario(email, provider);

        Set<GrantedAuthority> authorities = new HashSet<>(oauth2User.getAuthorities());
        authorities.add(new SimpleGrantedAuthority("ROLE_" + appUser.getRole().name()));

        return new DefaultOAuth2User(authorities, oauth2User.getAttributes(), "login");
    }

    private AppUser buscarOCrearUsuario(String email, String provider) {
        return appUserRepository.findByEmail(email)
                .orElseGet(() -> appUserRepository.save(
                        AppUser.builder()
                                .email(email)
                                .provider(provider)
                                .enabled(true)
                                .role(Role.USER)
                                .build()
                ));
    }
}