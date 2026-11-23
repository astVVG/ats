package io.github.astvvg.ats.job;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.astvvg.ats.job.domain.Job;
import io.github.astvvg.ats.job.domain.JobStatus;
import io.github.astvvg.ats.job.repository.JobRepository;
import io.github.astvvg.ats.support.TestcontainersConfiguration;
import io.github.astvvg.ats.user.domain.Role;
import io.github.astvvg.ats.user.domain.User;
import io.github.astvvg.ats.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class JobRepositoryTest {

    @Autowired private JobRepository jobRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private User creator;
    private String token;

    @BeforeEach
    void setUp() {
        creator =
                userRepository.saveAndFlush(
                        new User(
                                "Repo",
                                "jobrepo-" + UUID.randomUUID() + "@example.com",
                                "hash",
                                Role.RECRUITER));
        // Keeps these rows apart from whatever else is in the shared database.
        token = "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private Job save(String title, String department, JobStatus status) {
        Job job = new Job(title, department, null, null, creator);
        job.setStatus(status);
        return jobRepository.saveAndFlush(job);
    }

    private List<String> titles(JobStatus status, String q) {
        return jobRepository
                .findAll(JobRepository.matching(status, q), PageRequest.of(0, 50))
                .map(Job::getTitle)
                .getContent();
    }

    @Test
    void percentAndUnderscoreInQueryAreLiteral() {
        save(token + " 100% remote", null, JobStatus.OPEN);
        save(token + " 100 remote", null, JobStatus.OPEN);
        save(token + " a_b", null, JobStatus.OPEN);
        save(token + " axb", null, JobStatus.OPEN);
        save(token + " back\\slash", null, JobStatus.OPEN);

        assertThat(titles(null, token + " 100%")).containsExactly(token + " 100% remote");
        assertThat(titles(null, token + " a_b")).containsExactly(token + " a_b");
        assertThat(titles(null, token + " back\\")).containsExactly(token + " back\\slash");
        assertThat(titles(null, "%" + token)).isEmpty();
    }

    @Test
    void queryMatchesTitleOrDepartmentIgnoringCase() {
        save("Engineer " + token.toUpperCase(), "Sales", JobStatus.OPEN);
        save("Designer", "Dept " + token, JobStatus.CLOSED);
        save("Other", "Other", JobStatus.OPEN);

        assertThat(titles(null, "  " + token + " "))
                .containsExactlyInAnyOrder("Engineer " + token.toUpperCase(), "Designer");
        assertThat(titles(JobStatus.CLOSED, token)).containsExactly("Designer");
        assertThat(titles(JobStatus.OPEN, token))
                .containsExactly("Engineer " + token.toUpperCase());
    }

    @Test
    void newJobIsOpenWithTimestamps() {
        Job job = save("Timestamps " + token, null, JobStatus.OPEN);

        assertThat(job.getStatus()).isEqualTo(JobStatus.OPEN);
        assertThat(job.getCreatedAt()).isNotNull();
        assertThat(job.getUpdatedAt()).isEqualTo(job.getCreatedAt());
    }

    @Test
    void flywayCreatedJobsTableWithStatusCheckAndIndex() {
        assertThat(
                        jdbcTemplate.queryForList(
                                "SELECT constraint_name FROM information_schema.table_constraints"
                                        + " WHERE table_name = 'jobs'",
                                String.class))
                .contains("ck_jobs_status", "fk_jobs_created_by");
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT count(*) FROM pg_indexes WHERE tablename = 'jobs'"
                                        + " AND indexname = 'idx_jobs_status'",
                                Integer.class))
                .isEqualTo(1);
    }
}
