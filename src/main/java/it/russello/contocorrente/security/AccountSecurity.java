package it.russello.contocorrente.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component("accountSecurity")
public class AccountSecurity {

    public Long currentPersonId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new AuthenticationCredentialsNotFoundException("Autenticazione JWT richiesta");
        }
        Object claim = jwt.getClaims().get("personId");
        if (claim == null) {
            throw new AuthenticationCredentialsNotFoundException("Persona assente nel JWT");
        }
        try {
            Long personId = Long.valueOf(claim.toString());
            if (personId <= 0) {
                throw new NumberFormatException();
            }
            return personId;
        } catch (NumberFormatException e) {
            throw new AuthenticationCredentialsNotFoundException("personId non valido nel JWT");
        }
    }

    public boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return false;
        }
        return authentication.getAuthorities().stream().anyMatch(a -> "ROLE_admin".equals(a.getAuthority()));
    }

}
