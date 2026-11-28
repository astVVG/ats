package io.github.astvvg.ats.common.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.astvvg.ats.application.domain.Stage;
import io.github.astvvg.ats.candidate.repository.CandidateRepository;
import io.github.astvvg.ats.common.demo.DemoSeeder.DemoProperties;
import io.github.astvvg.ats.job.repository.JobRepository;
import io.github.astvvg.ats.metrics.dto.TimeToHireResponse;
import io.github.astvvg.ats.metrics.service.MetricsService;
import io.github.astvvg.ats.user.domain.Role;
import io.github.astvvg.ats.user.domain.User;
import io.github.astvvg.ats.user.repository.UserRepository;
import io.github.astvvg.ats.user.service.AdminSeeder.AdminProperties;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.MySQLContainer;

/**
 * Uses a database of its own: the seeder only loads into an empty one, and the shared container
 * keeps rows committed by other tests.
 */
@SpringBootTest(properties = "logging.level.io.github.astvvg.ats.common.demo=INFO")
@ActiveProfiles("test")
@Import(DemoSeederTest.OwnDatabase.class)
@ExtendWith(OutputCaptureExtension.class)
class DemoSeederTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class OwnDatabase {

        @Bean
        @ServiceConnection
        MySQLContainer<?> demoMysqlContainer() {
            return new MySQLContainer<>("mysql:8.4")
                    .withCommand(
                            "--default-time-zone=-06:00", "--collation-server=utf8mb4_0900_as_ci");
        }
    }

    private static final String ADMIN_EMAIL = "demo.admin@example.com";
    private static final String PASSWORD = "Demo-test-pass-1";

    @Autowired private UserRepository userRepository;
    @Autowired private JobRepository jobRepository;
    @Autowired private CandidateRepository candidateRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private MetricsService metricsService;

    private User admin;

    // Only ever runs against the private container above.
    @BeforeEach
    void emptyDatabase() {
        for (String table :
                List.of("notes", "stage_history", "applications", "candidates", "jobs", "users")) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
        admin = userRepository.save(new User("Admin", ADMIN_EMAIL, "unused-hash", Role.ADMIN));
    }

    @Test
    void emptyDatabaseGetsTheFullDemo(CapturedOutput output) {
        seed(PASSWORD);

        User recruiter = recruiter();
        assertThat(recruiter.getRole()).isEqualTo(Role.RECRUITER);
        assertThat(recruiter.isActive()).isTrue();
        assertThat(passwordEncoder.matches(PASSWORD, recruiter.getPasswordHash())).isTrue();
        assertThat(count("SELECT COUNT(*) FROM jobs WHERE status = 'OPEN'")).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM jobs WHERE status = 'CLOSED'")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM candidates")).isEqualTo(20);
        assertThat(
                        count(
                                "SELECT COUNT(*) FROM candidates WHERE email LIKE '%@demo.test'"
                                        + " AND resume_path IS NULL"))
                .isEqualTo(20);
        assertThat(count("SELECT COUNT(DISTINCT stage) FROM applications"))
                .isEqualTo(Stage.values().length);
        assertThat(count("SELECT COUNT(*) FROM applications WHERE rating IS NOT NULL")).isZero();
        assertThat(count("SELECT COUNT(*) FROM notes")).isZero();
        assertAuthor(recruiter.getId());
        assertThat(output).contains("Loaded demo data").doesNotContain(PASSWORD);
    }

    @Test
    void positionsAreContiguousFromZeroInEveryColumn() {
        seed(PASSWORD);

        assertThat(
                        count(
                                "SELECT COUNT(*) FROM (SELECT job_id, stage FROM applications"
                                        + " GROUP BY job_id, stage HAVING MIN(position) <> 0"
                                        + " OR MAX(position) <> COUNT(*) - 1"
                                        + " OR COUNT(DISTINCT position) <> COUNT(*)) broken"))
                .isZero();
    }

    @Test
    void historyAndDatesAreCoherent() {
        seed(PASSWORD);

        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList(
                        "SELECT h.application_id AS id, h.from_stage AS source,"
                                + " h.to_stage AS target, h.moved_at AS moved,"
                                + " a.stage AS current_stage, a.created_at AS created,"
                                + " a.updated_at AS updated, j.created_at AS job_created,"
                                + " c.created_at AS candidate_created"
                                + " FROM stage_history h"
                                + " JOIN applications a ON a.id = h.application_id"
                                + " JOIN jobs j ON j.id = a.job_id"
                                + " JOIN candidates c ON c.id = a.candidate_id"
                                + " ORDER BY h.application_id, h.id");
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            boolean first = i == 0 || !rows.get(i - 1).get("id").equals(row.get("id"));
            boolean last = i == rows.size() - 1 || !rows.get(i + 1).get("id").equals(row.get("id"));
            if (first) {
                assertThat(row.get("source")).isNull();
                assertThat(row.get("target")).isEqualTo("APPLIED");
                assertThat(row.get("moved")).isEqualTo(row.get("created"));
                assertThat(compare(row, "job_created", "created")).isNotPositive();
                assertThat(compare(row, "candidate_created", "created")).isNotPositive();
            } else {
                assertThat(row.get("source")).isEqualTo(rows.get(i - 1).get("target"));
                assertThat(row.get("target")).isNotEqualTo(row.get("source"));
                assertThat(compare(rows.get(i - 1), row, "moved")).isNegative();
            }
            if (last) {
                assertThat(row.get("target")).isEqualTo(row.get("current_stage"));
                assertThat(compare(row, "moved", "updated")).isNotPositive();
            }
        }
        assertThat(rows.stream().map(row -> row.get("id")).distinct()).hasSize(20);
        // Compared inside the database, whose sessions are in UTC.
        for (String column :
                List.of(
                        "stage_history.moved_at",
                        "applications.updated_at",
                        "jobs.updated_at",
                        "candidates.updated_at")) {
            String[] parts = column.split("\\.");
            assertThat(
                            count(
                                    "SELECT COUNT(*) FROM "
                                            + parts[0]
                                            + " WHERE "
                                            + parts[1]
                                            + " > UTC_TIMESTAMP(6)"))
                    .as(column)
                    .isZero();
        }
        // The newest application is two days old: a shifted time zone would move it.
        assertThat(
                        count(
                                "SELECT TIMESTAMPDIFF(HOUR, MAX(created_at), UTC_TIMESTAMP(6))"
                                        + " FROM applications"))
                .isEqualTo(48);
        assertThat(
                        count(
                                "SELECT COUNT(*) FROM stage_history h"
                                        + " JOIN applications a ON a.id = h.application_id"
                                        + " JOIN jobs j ON j.id = a.job_id"
                                        + " WHERE j.status = 'CLOSED' AND h.from_stage IS NOT NULL"
                                        + " AND h.moved_at <= j.updated_at"))
                .isPositive();
    }

    @Test
    void metricsHaveSomethingToShow() {
        seed(PASSWORD);

        assertThat(metricsService.funnel(null, null, null).stages())
                .allSatisfy(stage -> assertThat(stage.count()).isPositive());
        assertThat(metricsService.timeInStage(null, null, null).stages())
                .filteredOn(stage ->
                        List.of(Stage.APPLIED, Stage.INTERVIEW, Stage.OFFER)
                                .contains(stage.stage()))
                .hasSize(3)
                .allSatisfy(stage -> assertThat(stage.samples()).isPositive());
        TimeToHireResponse timeToHire = metricsService.timeToHire(null, null, null);
        assertThat(timeToHire.hiredCount()).isGreaterThanOrEqualTo(2);
        assertThat(timeToHire.averageDays()).isNotNull();
    }

    @Test
    void secondRunChangesNothing() {
        seed(PASSWORD);
        List<List<Map<String, Object>>> before = snapshot();

        seed("Another-demo-pass-2");

        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void anExistingJobPreventsBusinessData() {
        jdbcTemplate.update(
                "INSERT INTO jobs (title, created_by) VALUES ('Real job', ?)", admin.getId());

        seed(PASSWORD);

        assertThat(count("SELECT COUNT(*) FROM jobs")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM candidates")).isZero();
        assertThat(count("SELECT COUNT(*) FROM applications")).isZero();
    }

    @Test
    void anExistingCandidatePreventsBusinessData() {
        jdbcTemplate.update(
                "INSERT INTO candidates (full_name, email) VALUES ('Real', 'real@example.com')");

        seed(PASSWORD);

        assertThat(count("SELECT COUNT(*) FROM jobs")).isZero();
        assertThat(count("SELECT COUNT(*) FROM candidates")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM applications")).isZero();
    }

    @Test
    void withoutTheAdminUserNothingIsCreated(CapturedOutput output) {
        for (String adminEmail : new String[] {"", "missing@example.com"}) {
            seeder(adminEmail, PASSWORD, jdbcTemplate).run(new DefaultApplicationArguments());
        }

        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM jobs")).isZero();
        assertThat(count("SELECT COUNT(*) FROM candidates")).isZero();
        assertThat(output).contains("skipping demo data");
    }

    @Test
    void withoutPasswordTheAdminOwnsTheDemo(CapturedOutput output) {
        seed("");

        assertThat(userRepository.findByEmail(DemoSeeder.RECRUITER_EMAIL)).isEmpty();
        assertThat(count("SELECT COUNT(*) FROM candidates")).isEqualTo(20);
        assertAuthor(admin.getId());
        assertThat(output).contains("DEMO_RECRUITER_PASSWORD is not set");

        // Defining it later creates the user without reassigning what is already loaded.
        seed(PASSWORD);

        assertThat(recruiter().getRole()).isEqualTo(Role.RECRUITER);
        assertThat(count("SELECT COUNT(*) FROM candidates")).isEqualTo(20);
        assertAuthor(admin.getId());
    }

    @Test
    void existingRecruiterIsReusedUntouched() {
        User existing = new User("Kept", DemoSeeder.RECRUITER_EMAIL, "kept-hash", Role.ADMIN);
        existing.setActive(false);
        Long id = userRepository.save(existing).getId();

        for (String password : new String[] {PASSWORD, ""}) {
            seed(password);

            User after = recruiter();
            assertThat(after.getId()).isEqualTo(id);
            assertThat(after.getName()).isEqualTo("Kept");
            assertThat(after.getPasswordHash()).isEqualTo("kept-hash");
            assertThat(after.getRole()).isEqualTo(Role.ADMIN);
            assertThat(after.isActive()).isFalse();
        }
        assertAuthor(id);
    }

    @Test
    void aFailureHalfwayLeavesNoBusinessData() {
        // Fails on the last history row of the load, after everything else was inserted.
        JdbcTemplate failing =
                new JdbcTemplate(dataSource) {
                    private int historyRows;

                    @Override
                    public int update(String sql, Object... args) {
                        if (sql.startsWith("INSERT INTO stage_history") && ++historyRows == 40) {
                            assertThat(count("SELECT COUNT(*) FROM jobs")).isEqualTo(3);
                            throw new IllegalStateException("simulated failure");
                        }
                        return super.update(sql, args);
                    }
                };

        assertThatThrownBy(() ->
                        seeder(ADMIN_EMAIL, PASSWORD, failing)
                                .run(new DefaultApplicationArguments()))
                .hasMessage("simulated failure");

        assertThat(count("SELECT COUNT(*) FROM jobs")).isZero();
        assertThat(count("SELECT COUNT(*) FROM candidates")).isZero();
        assertThat(count("SELECT COUNT(*) FROM applications")).isZero();
        assertThat(count("SELECT COUNT(*) FROM stage_history")).isZero();
    }

    private void seed(String recruiterPassword) {
        seeder(ADMIN_EMAIL, recruiterPassword, jdbcTemplate).run(new DefaultApplicationArguments());
    }

    // Built by hand: the bean only exists in dev, and that profile reads the local .env file.
    private DemoSeeder seeder(String adminEmail, String recruiterPassword, JdbcTemplate jdbc) {
        return new DemoSeeder(
                new DemoProperties(recruiterPassword),
                new AdminProperties(adminEmail, "", ""),
                userRepository,
                jobRepository,
                candidateRepository,
                passwordEncoder,
                jdbc,
                transactionManager);
    }

    private User recruiter() {
        return userRepository.findByEmail(DemoSeeder.RECRUITER_EMAIL).orElseThrow();
    }

    private void assertAuthor(long userId) {
        assertThat(count("SELECT COUNT(*) FROM jobs WHERE created_by <> " + userId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM stage_history WHERE moved_by <> " + userId))
                .isZero();
    }

    private long count(String sql) {
        return jdbcTemplate.queryForObject(sql, Long.class);
    }

    private List<List<Map<String, Object>>> snapshot() {
        return List.of("users", "jobs", "candidates", "applications", "stage_history").stream()
                .map(table -> jdbcTemplate.queryForList("SELECT * FROM " + table + " ORDER BY id"))
                .toList();
    }

    private static int compare(Map<String, Object> row, String first, String second) {
        return time(row, first).compareTo(time(row, second));
    }

    private static int compare(Map<String, Object> a, Map<String, Object> b, String column) {
        return time(a, column).compareTo(time(b, column));
    }

    @SuppressWarnings("unchecked")
    private static Comparable<Object> time(Map<String, Object> row, String column) {
        return (Comparable<Object>) row.get(column);
    }
}
