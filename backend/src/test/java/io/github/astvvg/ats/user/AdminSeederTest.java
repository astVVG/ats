package io.github.astvvg.ats.user;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.astvvg.ats.support.TestcontainersConfiguration;
import io.github.astvvg.ats.user.domain.Role;
import io.github.astvvg.ats.user.domain.User;
import io.github.astvvg.ats.user.repository.UserRepository;
import io.github.astvvg.ats.user.service.AdminSeeder;
import io.github.astvvg.ats.user.service.AdminSeeder.AdminProperties;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
        properties = {
            "app.admin.email=" + AdminSeederTest.SEED_EMAIL,
            "app.admin.password=" + AdminSeederTest.SEED_PASSWORD,
            "app.admin.name=Seed Admin"
        })
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdminSeederTest {

    static final String SEED_EMAIL = "Seeder.Admin@Example.com";
    static final String SEED_PASSWORD = "Seed-admin-pass-1";

    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AdminSeeder adminSeeder;

    @AfterAll
    void removeSeededAdmin() {
        userRepository.findByEmail(SEED_EMAIL).ifPresent(userRepository::delete);
    }

    private User seededAdmin() {
        return userRepository.findByEmail(SEED_EMAIL).orElseThrow();
    }

    private AdminSeeder seederWith(String email, String password) {
        return new AdminSeeder(
                new AdminProperties(email, password, "Other"), userRepository, passwordEncoder);
    }

    @Test
    void startupCreatesActiveAdminWithBcryptHash() {
        User admin = seededAdmin();

        assertThat(admin.getEmail()).isEqualTo("seeder.admin@example.com");
        assertThat(admin.getName()).isEqualTo("Seed Admin");
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.isActive()).isTrue();
        assertThat(admin.getPasswordHash()).startsWith("$2");
        assertThat(passwordEncoder.matches(SEED_PASSWORD, admin.getPasswordHash())).isTrue();
    }

    @Test
    void runningAgainIsIdempotent() throws Exception {
        String hashBefore = seededAdmin().getPasswordHash();
        long countBefore = userRepository.count();

        adminSeeder.run(new DefaultApplicationArguments());

        assertThat(userRepository.count()).isEqualTo(countBefore);
        assertThat(seededAdmin().getPasswordHash()).isEqualTo(hashBefore);
    }

    @Test
    void existingEmailKeepsItsPassword() throws Exception {
        User before = seededAdmin();

        seederWith(SEED_EMAIL.toUpperCase(), "A-different-pass-99")
                .run(new DefaultApplicationArguments());

        User after = seededAdmin();
        assertThat(after.getPasswordHash()).isEqualTo(before.getPasswordHash());
        assertThat(after.getName()).isEqualTo(before.getName());
    }

    @Test
    void missingVariablesCreateNothing() throws Exception {
        long countBefore = userRepository.count();
        String email = "seed-" + UUID.randomUUID() + "@example.com";

        seederWith("", "").run(new DefaultApplicationArguments());
        seederWith(email, "").run(new DefaultApplicationArguments());
        seederWith(null, "Some-password-1").run(new DefaultApplicationArguments());

        assertThat(userRepository.count()).isEqualTo(countBefore);
        assertThat(userRepository.existsByEmail(email)).isFalse();
    }

    // Not a @Configuration: a nested one would replace the application context of this test.
    @EnableConfigurationProperties(AdminProperties.class)
    static class PropertiesConfig {}

    @Test
    void shortPasswordFailsStartup() {
        new ApplicationContextRunner()
                .withUserConfiguration(PropertiesConfig.class)
                .withPropertyValues(
                        "app.admin.email=short@example.com", "app.admin.password=short1")
                .run(context ->
                        assertThat(context.getStartupFailure())
                                .isNotNull()
                                .rootCause()
                                .hasMessageContaining("ADMIN_PASSWORD")
                                .hasMessageNotContaining("short1"));
    }

    @Test
    void passwordOverBcryptByteLimitFailsStartup() {
        // 40 characters, 80 bytes in UTF-8.
        String longPassword = "á".repeat(40);
        new ApplicationContextRunner()
                .withUserConfiguration(PropertiesConfig.class)
                .withPropertyValues(
                        "app.admin.email=bytes@example.com", "app.admin.password=" + longPassword)
                .run(context ->
                        assertThat(context.getStartupFailure())
                                .isNotNull()
                                .rootCause()
                                .hasMessageContaining("ADMIN_PASSWORD")
                                .hasMessageContaining("72 bytes")
                                .hasMessageNotContaining(longPassword));
    }
}
