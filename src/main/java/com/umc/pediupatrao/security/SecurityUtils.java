package com.umc.pediupatrao.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static String usuarioAtual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return "sistema";
        }
        return auth.getName();
    }

    public static String perfilAtual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return "sistema";
        }
        for (GrantedAuthority authority : auth.getAuthorities()) {
            String nome = authority.getAuthority();
            if (nome.startsWith("ROLE_")) {
                return nome.substring(5);
            }
        }
        return "sistema";
    }
}
