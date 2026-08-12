package com.alessandro.backend_oauth.security;

import com.alessandro.backend_oauth.entity.AppUser;
import com.alessandro.backend_oauth.entity.Role;
import com.alessandro.backend_oauth.repository.AppUserRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Service
public class CustomOidcUserService extends OidcUserService {

    private final AppUserRepository appUserRepository;

    public CustomOidcUserService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) {
        OidcUser oidcUser = super.loadUser(userRequest); // Google ya confirmó la identidad aquí

        String email = oidcUser.getEmail();
        String provider = userRequest.getClientRegistration().getRegistrationId(); // "google"

        AppUser appUser = buscarOCrearUsuario(email, provider);

        Set<GrantedAuthority> authorities = new HashSet<>(oidcUser.getAuthorities());
        authorities.add(new SimpleGrantedAuthority("ROLE_" + appUser.getRole().name()));

        return new DefaultOidcUser(authorities, oidcUser.getIdToken(), oidcUser.getUserInfo());
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