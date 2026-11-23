package io.github.astvvg.ats.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.astvvg.ats.support.TestcontainersConfiguration;
import io.github.astvvg.ats.user.domain.Role;
import io.github.astvvg.ats.user.domain.User;
import io.github.astvvg.ats.user.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static String uniqueEmail() {
        return "repo-" + UUID.randomUUID() + "@example.com";
    }

    @Test
    void savesAndFindsByEmailNormalized() {
        String email = uniqueEmail();
        User saved =
                userRepository.saveAndFlush(
                        new User("Ana", "  " + email.toUpperCase() + " ", "hash", Role.RECRUITER));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEmail()).isEqualTo(email);
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(userRepository.findByEmail(" " + email.toUpperCase()))
                .get()
                .extracting(User::getId)
                .isEqualTo(saved.getId());
        assertThat(userRepository.existsByEmail(email)).isTrue();
        assertThat(userRepository.existsByEmail(uniqueEmail())).isFalse();
    }

    @Test
    void rejectsDuplicateEmail() {
        String email = uniqueEmail();
        userRepository.saveAndFlush(new User("Ana", email, "hash", Role.RECRUITER));

        assertThatThrownBy(() ->
                        userRepository.saveAndFlush(
                                new User("Otro", email.toUpperCase(), "hash", Role.ADMIN)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void flywayAppliedV1() {
        Boolean applied =
                jdbcTemplate.queryForObject(
                        "SELECT success FROM flyway_schema_history WHERE version = '1'",
                        Boolean.class);
        assertThat(applied).isTrue();
        assertThat(
                        jdbcTemplate.queryForList(
                                "SELECT column_name FROM information_schema.columns"
                                        + " WHERE table_name = 'users'",
                                String.class))
                .containsExactlyInAnyOrder(
                        "id", "name", "email", "password_hash", "role", "active", "created_at");
        assertThat(
                        jdbcTemplate.queryForList(
                                "SELECT constraint_name FROM information_schema.table_constraints"
                                        + " WHERE table_name = 'users'"
                                        + " AND constraint_type IN ('UNIQUE', 'CHECK')",
                                String.class))
                .contains("uq_users_email", "ck_users_role");
    }
}
