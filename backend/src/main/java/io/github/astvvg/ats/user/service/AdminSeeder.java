package io.github.astvvg.ats.user.service;

import io.github.astvvg.ats.common.validation.MaxUtf8Bytes;
import io.github.astvvg.ats.user.domain.Role;
import io.github.astvvg.ats.user.domain.User;
import io.github.astvvg.ats.user.repository.UserRepository;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

/** Creates the initial ADMIN from ADMIN_EMAIL and ADMIN_PASSWORD if it does not exist yet. */
@Component
@Order(1)
@EnableConfigurationProperties(AdminSeeder.AdminProperties.class)
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);
    private static final String DEFAULT_NAME = "Administrator";

    private final AdminProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminSeeder(
            AdminProperties properties,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isConfigured()) {
            log.info("ADMIN_EMAIL or ADMIN_PASSWORD is not set; skipping admin seeding");
            return;
        }
        String email = User.normalizeEmail(properties.email());
        if (userRepository.existsByEmail(email)) {
            log.info("Admin user {} already exists; skipping admin seeding", email);
            return;
        }
        String name =
                StringUtils.hasText(properties.name()) ? properties.name().trim() : DEFAULT_NAME;
        userRepository.save(
                new User(name, email, passwordEncoder.encode(properties.password()), Role.ADMIN));
        log.info("Created admin user {}", email);
    }

    @Validated
    @ConfigurationProperties("app.admin")
    public record AdminProperties(@Email String email, String password, String name) {

        static final int MIN_PASSWORD_LENGTH = 8;

        // Validated through a boolean property so the password never appears in startup errors.
        @AssertTrue(message = "app.admin.password (ADMIN_PASSWORD) must have at least 8 characters")
        public boolean isPasswordLengthValid() {
            return !StringUtils.hasText(password) || password.length() >= MIN_PASSWORD_LENGTH;
        }

        @AssertTrue(
                message =
                        "app.admin.password (ADMIN_PASSWORD) must be at most 72 bytes in UTF-8"
                                + " (BCrypt limit)")
        public boolean isPasswordWithinBcryptLimit() {
            return MaxUtf8Bytes.Validator.fits(password, MaxUtf8Bytes.BCRYPT_MAX_BYTES);
        }

        boolean isConfigured() {
            return StringUtils.hasText(email) && StringUtils.hasText(password);
        }
    }
}
