package io.github.astvvg.ats.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.astvvg.ats.application.domain.Stage;
import io.github.astvvg.ats.metrics.MetricsFixtures.Card;
import io.github.astvvg.ats.support.TestUsers;
import io.github.astvvg.ats.support.TestcontainersConfiguration;
import io.github.astvvg.ats.user.domain.Role;
import io.github.astvvg.ats.user.domain.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TestUsers.class})
@Transactional
class MetricsTimeToHireIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TestUsers testUsers;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private String auth;
    private MetricsFixtures fixtures;
    private long job;

    @BeforeEach
    void setUp() {
        User recruiter = testUsers.create(Role.RECRUITER);
        auth = testUsers.bearer(recruiter);
        fixtures = new MetricsFixtures(jdbcTemplate, recruiter.getId());
        job = fixtures.job("OPEN");
    }

    private JsonNode timeToHire(String query) throws Exception {
        return MetricsFixtures.json(
                objectMapper,
                MetricsFixtures.metrics(mockMvc, entityManager, "time-to-hire", query, auth)
                        .andExpect(status().isOk()));
    }

    /** "averageDays/hiredCount" with the average as a plain string. */
    private static String summary(JsonNode body) {
        JsonNode average = body.get("averageDays");
        return (average.isNull() ? "null" : average.decimalValue().toPlainString())
                + "/"
                + body.get("hiredCount").asLong();
    }

    @Test
    void referenceDataWithoutDates() throws Exception {
        fixtures.reference(job);

        JsonNode body = timeToHire("?jobId=" + job);

        Set<String> fields = new HashSet<>();
        body.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("averageDays", "hiredCount");
        assertThat(summary(body)).isEqualTo("3.00/2");
    }

    @Test
    void referenceDataBetweenTheFifteenthAndTheTwentieth() throws Exception {
        fixtures.reference(job);

        assertThat(summary(timeToHire("?jobId=" + job + "&from=2026-01-15&to=2026-01-20")))
                .isEqualTo("2.00/1");
    }

    @Test
    void nobodyHiredGivesNullAndZero() throws Exception {
        Card card = fixtures.apply(job, "2026-04-01T00:00:00Z");
        fixtures.move(card, Stage.OFFER, "2026-04-02T00:00:00Z");

        assertThat(summary(timeToHire("?jobId=" + job))).isEqualTo("null/0");
        assertThat(summary(timeToHire("?from=1990-01-01&to=1990-01-31"))).isEqualTo("null/0");
    }

    @Test
    void leavingHiredAndComingBackCountsOnceFromTheFirstArrival() throws Exception {
        Card card = fixtures.apply(job, "2026-04-01T00:00:00Z");
        fixtures.move(card, Stage.HIRED, "2026-04-03T00:00:00Z");
        fixtures.move(card, Stage.REJECTED, "2026-04-04T00:00:00Z");
        fixtures.move(card, Stage.HIRED, "2026-04-10T00:00:00Z");

        assertThat(summary(timeToHire("?jobId=" + job))).isEqualTo("2.00/1");
    }

    @Test
    void stillCountsAfterLeavingHired() throws Exception {
        Card card = fixtures.apply(job, "2026-04-01T00:00:00Z");
        fixtures.move(card, Stage.HIRED, "2026-04-02T12:00:00Z");
        fixtures.move(card, Stage.REJECTED, "2026-04-05T00:00:00Z");

        assertThat(summary(timeToHire("?jobId=" + job))).isEqualTo("1.50/1");
    }

    @Test
    void measuresFromCreatedAtNotFromTheInitialMovedAt() throws Exception {
        Card card = fixtures.apply(job, "2026-04-01T00:00:00Z", "2026-04-01T12:00:00Z");
        fixtures.move(card, Stage.HIRED, "2026-04-02T00:00:00Z");

        assertThat(summary(timeToHire("?jobId=" + job))).isEqualTo("1.00/1");
    }

    @Test
    void averageIsRoundedHalfUpOnlyAtTheEnd() throws Exception {
        // 1 h and 2 h are 0.041666 and 0.083333 days: their exact average 0.0625 rounds to 0.06,
        // and 3 h on its own (0.125 days) rounds half-up to 0.13.
        Card first = fixtures.apply(job, "2026-04-01T00:00:00Z");
        fixtures.move(first, Stage.HIRED, "2026-04-01T01:00:00Z");
        Card second = fixtures.apply(job, "2026-04-01T00:00:00Z");
        fixtures.move(second, Stage.HIRED, "2026-04-01T02:00:00Z");
        long other = fixtures.job("CLOSED");
        Card third = fixtures.apply(other, "2026-04-01T00:00:00Z");
        fixtures.move(third, Stage.HIRED, "2026-04-01T03:00:00Z");

        assertThat(summary(timeToHire("?jobId=" + job))).isEqualTo("0.06/2");
        assertThat(summary(timeToHire("?jobId=" + other))).isEqualTo("0.13/1");
    }

    @Test
    void fractionsOfASecondSurviveTheSum() throws Exception {
        // 431.6 s and 432.4 s average exactly 432 s, which is 0.005 days and rounds up to 0.01;
        // dropping the fractions would give 431.5 s and 0.00.
        for (String movedAt : List.of("2026-04-01T00:07:11.6Z", "2026-04-01T00:07:12.4Z")) {
            Card card = fixtures.apply(job, "2026-04-01T00:00:00Z");
            fixtures.move(card, Stage.HIRED, movedAt);
        }

        assertThat(summary(timeToHire("?jobId=" + job))).isEqualTo("0.01/2");
    }

    @Test
    void hiresOutsideTheRangeStillCountForAnIncludedApplication() throws Exception {
        Card card = fixtures.apply(job, "2026-05-31T12:00:00Z");
        fixtures.move(card, Stage.HIRED, "2026-07-01T12:00:00Z");

        assertThat(summary(timeToHire("?jobId=" + job + "&to=2026-05-31"))).isEqualTo("31.00/1");
        assertThat(summary(timeToHire("?jobId=" + job + "&from=2026-06-01"))).isEqualTo("null/0");
    }

    @Test
    void statementsDoNotGrowWithData() throws Exception {
        long small = fixtures.job("OPEN");
        fixtures.apply(small, "2026-06-01T00:00:00Z");
        long large = fixtures.job("OPEN");
        for (int i = 0; i < 10; i++) {
            fixtures.reference(large);
        }

        long withOne = statements("?jobId=" + small);
        long withMany = statements("?jobId=" + large);

        assertThat(withOne).isPositive();
        assertThat(withMany).isEqualTo(withOne);
    }

    private long statements(String query) throws Exception {
        entityManager.flush();
        entityManager.clear();
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        timeToHire(query);
        return statistics.getPrepareStatementCount();
    }
}
