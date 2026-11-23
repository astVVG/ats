package io.github.astvvg.ats.security;

import io.github.astvvg.ats.user.domain.Role;
import io.github.astvvg.ats.user.domain.User;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public final class AuthenticatedUser implements UserDetails, CredentialsContainer {

    private final Long id;
    private final String email;
    private final Role role;
    private final boolean enabled;
    private String passwordHash;

    private AuthenticatedUser(
            Long id, String email, Role role, boolean enabled, String passwordHash) {
        this.id = id;
        this.email = email;
        this.role = role;
        this.enabled = enabled;
        this.passwordHash = passwordHash;
    }

    public static AuthenticatedUser from(User user) {
        return new AuthenticatedUser(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                user.isActive(),
                user.getPasswordHash());
    }

    public Long getId() {
        return id;
    }

    public Role getRole() {
        return role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }
}
