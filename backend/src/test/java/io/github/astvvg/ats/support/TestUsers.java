package io.github.astvvg.ats.support;

import io.github.astvvg.ats.security.JwtService;
import io.github.astvvg.ats.user.domain.Role;
import io.github.astvvg.ats.user.domain.User;
import io.github.astvvg.ats.user.repository.UserRepository;
import java.util.UUID;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Creates users with a known password and issues tokens for them. Import it where needed. */
@TestComponent
public class TestUsers {

    public static final String PASSWORD = "Test-password-123";

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final String passwordHash;

    public TestUsers(
            UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        // Encoded once: BCrypt is deliberately slow.
        this.passwordHash = passwordEncoder.encode(PASSWORD);
    }

    public static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    public User create(Role role) {
        return create(role, true);
    }

    public User create(Role role, boolean active) {
        User user =
                new User(
                        "Test " + role.name(),
                        uniqueEmail(role.name().toLowerCase()),
                        passwordHash,
                        role);
        user.setActive(active);
        return userRepository.saveAndFlush(user);
    }

    public String bearer(User user) {
        return "Bearer " + jwtService.generateToken(user);
    }
}
