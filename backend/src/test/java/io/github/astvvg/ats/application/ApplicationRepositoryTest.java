package io.github.astvvg.ats.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.astvvg.ats.application.domain.Application;
import io.github.astvvg.ats.application.domain.Stage;
import io.github.astvvg.ats.application.repository.ApplicationRepository;
import io.github.astvvg.ats.candidate.domain.Candidate;
import io.github.astvvg.ats.candidate.repository.CandidateRepository;
import io.github.astvvg.ats.job.domain.Job;
import io.github.astvvg.ats.job.repository.JobRepository;
import io.github.astvvg.ats.support.TestcontainersConfiguration;
import io.github.astvvg.ats.user.domain.Role;
import io.github.astvvg.ats.user.domain.User;
import io.github.astvvg.ats.user.repository.UserRepository;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
class ApplicationRepositoryTest {

    @Autowired private ApplicationRepository applicationRepository;
    @Autowired private JobRepository jobRepository;
    @Autowired private CandidateRepository candidateRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private User creator;

    @BeforeEach
    void setUp() {
        creator =
                userRepository.saveAndFlush(
                        new User(
                                "Repo",
                                "apprepo-" + UUID.randomUUID() + "@example.com",
                                "hash",
                                Role.RECRUITER));
    }

    private Job job() {
        return jobRepository.saveAndFlush(new Job("Job", null, null, null, creator));
    }

    private Candidate candidate() {
        return candidateRepository.saveAndFlush(
                new Candidate("Cand", "apprepo-" + UUID.randomUUID() + "@example.com", null, null));
    }

    private Application save(Job job, Candidate candidate, Stage stage, int position) {
        return applicationRepository.saveAndFlush(new Application(job, candidate, stage, position));
    }

    private List<Integer> positions(Job job, Stage stage) {
        return jdbcTemplate.queryForList(
                "SELECT position FROM applications WHERE job_id = ? AND stage = ? ORDER BY id",
                Integer.class,
                job.getId(),
                stage.name());
    }

    private List<Timestamp> updatedAts(Job job) {
        return jdbcTemplate.queryForList(
                "SELECT updated_at FROM applications WHERE job_id = ? ORDER BY id",
                Timestamp.class,
                job.getId());
    }

    @Test
    void countAndJobIdLookup() {
        Job job = job();
        Application applied = save(job, candidate(), Stage.APPLIED, 0);
        save(job, candidate(), Stage.APPLIED, 1);
        save(job, candidate(), Stage.HIRED, 0);
        save(job(), candidate(), Stage.APPLIED, 0);

        assertThat(applicationRepository.countByJobIdAndStage(job.getId(), Stage.APPLIED))
                .isEqualTo(2);
        assertThat(applicationRepository.countByJobIdAndStage(job.getId(), Stage.OFFER)).isZero();
        assertThat(applicationRepository.findJobIdById(applied.getId())).contains(job.getId());
        assertThat(applicationRepository.findJobIdById(Long.MAX_VALUE)).isEmpty();
    }

    @Test
    void decrementShiftsOnlyTheRangeOfOneColumnAndKeepsUpdatedAt() {
        Job job = job();
        Job otherJob = job();
        for (int i = 0; i < 5; i++) {
            save(job, candidate(), Stage.APPLIED, i);
        }
        save(job, candidate(), Stage.INTERVIEW, 2);
        save(otherJob, candidate(), Stage.APPLIED, 2);
        List<Timestamp> before = updatedAts(job);

        int updated = applicationRepository.decrementPositions(job.getId(), Stage.APPLIED, 1, 3);

        assertThat(updated).isEqualTo(2);
        assertThat(positions(job, Stage.APPLIED)).containsExactly(0, 1, 1, 2, 4);
        assertThat(positions(job, Stage.INTERVIEW)).containsExactly(2);
        assertThat(positions(otherJob, Stage.APPLIED)).containsExactly(2);
        assertThat(updatedAts(job)).isEqualTo(before);
    }

    @Test
    void incrementShiftsOnlyTheRangeOfOneColumnAndKeepsUpdatedAt() {
        Job job = job();
        Job otherJob = job();
        for (int i = 0; i < 5; i++) {
            save(job, candidate(), Stage.OFFER, i);
        }
        save(job, candidate(), Stage.APPLIED, 1);
        save(otherJob, candidate(), Stage.OFFER, 1);
        List<Timestamp> before = updatedAts(job);

        int updated =
                applicationRepository.incrementPositions(
                        job.getId(), Stage.OFFER, 1, Integer.MAX_VALUE);

        assertThat(updated).isEqualTo(4);
        assertThat(positions(job, Stage.OFFER)).containsExactly(0, 2, 3, 4, 5);
        assertThat(positions(job, Stage.APPLIED)).containsExactly(1);
        assertThat(positions(otherJob, Stage.OFFER)).containsExactly(1);
        assertThat(updatedAts(job)).isEqualTo(before);
    }

    @Test
    void savesApplicationWithTimestampsAndNoRating() {
        Job job = job();
        Candidate candidate = candidate();

        Application saved = save(job, candidate, Stage.APPLIED, 0);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getRating()).isNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isEqualTo(saved.getCreatedAt());
        assertThat(
                        applicationRepository.existsByJobIdAndCandidateId(
                                job.getId(), candidate.getId()))
                .isTrue();
        assertThat(applicationRepository.existsByCandidateId(candidate.getId())).isTrue();
        assertThat(applicationRepository.existsByCandidateId(candidate().getId())).isFalse();
    }

    @Test
    void maxPositionOnlyCountsTheGivenJobAndStage() {
        Job job = job();
        Job otherJob = job();

        assertThat(applicationRepository.findMaxPosition(job.getId(), Stage.APPLIED)).isEmpty();

        save(job, candidate(), Stage.APPLIED, 0);
        save(job, candidate(), Stage.APPLIED, 3);
        save(job, candidate(), Stage.INTERVIEW, 9);
        save(otherJob, candidate(), Stage.APPLIED, 7);

        assertThat(applicationRepository.findMaxPosition(job.getId(), Stage.APPLIED)).contains(3);
        assertThat(applicationRepository.findMaxPosition(job.getId(), Stage.INTERVIEW)).contains(9);
        assertThat(applicationRepository.findMaxPosition(job.getId(), Stage.OFFER)).isEmpty();
        assertThat(applicationRepository.findMaxPosition(otherJob.getId(), Stage.APPLIED))
                .contains(7);
    }

    @Test
    void flywayCreatedApplicationTablesWithConstraintsAndIndex() {
        assertThat(
                        jdbcTemplate.queryForList(
                                "SELECT constraint_name FROM information_schema.table_constraints"
                                        + " WHERE table_name = 'applications'",
                                String.class))
                .contains(
                        "uq_applications_job_candidate",
                        "ck_applications_stage",
                        "ck_applications_position",
                        "ck_applications_rating",
                        "fk_applications_job",
                        "fk_applications_candidate");
        assertThat(
                        jdbcTemplate.queryForList(
                                "SELECT constraint_name FROM information_schema.table_constraints"
                                        + " WHERE table_name = 'stage_history'",
                                String.class))
                .contains(
                        "ck_stage_history_from_stage",
                        "ck_stage_history_to_stage",
                        "fk_stage_history_application",
                        "fk_stage_history_moved_by");
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT count(*) FROM pg_indexes WHERE tablename = 'applications'"
                                        + " AND indexname = 'idx_applications_job_stage_position'",
                                Integer.class))
                .isEqualTo(1);
    }

    // Must stay last in its test: the failed insert aborts the transaction.
    @Test
    void sameCandidateTwiceForSameJobViolatesUniqueConstraint() {
        Job job = job();
        Candidate candidate = candidate();
        save(job, candidate, Stage.APPLIED, 0);

        assertThatThrownBy(() -> save(job, candidate, Stage.INTERVIEW, 0))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_applications_job_candidate");
    }
}
