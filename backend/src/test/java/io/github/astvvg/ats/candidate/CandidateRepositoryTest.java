package io.github.astvvg.ats.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.astvvg.ats.candidate.domain.Candidate;
import io.github.astvvg.ats.candidate.repository.CandidateRepository;
import io.github.astvvg.ats.support.TestcontainersConfiguration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class CandidateRepositoryTest {

    @Autowired private CandidateRepository candidateRepository;

    private String token;

    @BeforeEach
    void setUp() {
        // Keeps these rows apart from whatever else is in the shared database.
        token = "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private Candidate save(String fullName, String email) {
        return candidateRepository.saveAndFlush(new Candidate(fullName, email, null, null));
    }

    private List<String> names(String q) {
        return candidateRepository
                .findAll(CandidateRepository.matching(q), PageRequest.of(0, 50))
                .map(Candidate::getFullName)
                .getContent();
    }

    @Test
    void percentAndUnderscoreInQueryAreLiteral() {
        save(token + " 50% Off", token + "1@example.com");
        save(token + " 50 Off", token + "2@example.com");
        save("Name", token + "_x@example.com");
        save("Other", token + "ax@example.com");

        assertThat(names(token + " 50%")).containsExactly(token + " 50% Off");
        assertThat(names(token + "_x")).containsExactly("Name");
        assertThat(names(token + "%")).isEmpty();
    }

    @Test
    void queryMatchesNameOrEmailIgnoringCase() {
        save("Ana " + token.toUpperCase(), "ana-" + UUID.randomUUID() + "@example.com");
        save("Luis", "luis-" + token + "@example.com");
        save("Nobody", "nobody-" + UUID.randomUUID() + "@example.com");

        assertThat(names(" " + token + " "))
                .containsExactlyInAnyOrder("Ana " + token.toUpperCase(), "Luis");
        assertThat(names("LUIS-" + token.toUpperCase())).containsExactly("Luis");
    }

    @Test
    void emailIsStoredNormalizedAndUniqueInAnyCase() {
        String email = token + "@example.com";
        Candidate saved = save("Ana", "  " + email.toUpperCase() + " ");

        assertThat(saved.getEmail()).isEqualTo(email);
        assertThat(saved.getCreatedAt()).isNotNull().isEqualTo(saved.getUpdatedAt());
        assertThat(candidateRepository.existsByEmail(email.toUpperCase())).isTrue();
        assertThat(candidateRepository.existsByEmailForOtherCandidate(email, saved.getId()))
                .isFalse();
        assertThatThrownBy(() -> save("Otra", email.toUpperCase()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_candidates_email");
    }
}
