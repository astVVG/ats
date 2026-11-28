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
import java.util.ArrayList;
import java.util.Arrays;
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
class MetricsTimeInStageIntegrationTest {

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

    private JsonNode timeInStage(String query) throws Exception {
        return MetricsFixtures.json(
                objectMapper,
                MetricsFixtures.metrics(mockMvc, entityManager, "time-in-stage", query, auth)
                        .andExpect(status().isOk()));
    }

    /** "averageHours/samples" per stage in stage order, with averages as plain strings. */
    private static List<String> summary(JsonNode body) {
        List<String> summary = new ArrayList<>();
        body.get("stages")
                .forEach(s -> {
                    JsonNode average = s.get("averageHours");
                    summary.add(
                            (average.isNull() ? "null" : average.decimalValue().toPlainString())
                                    + "/"
                                    + s.get("samples").asLong());
                });
        return summary;
    }

    @Test
    void referenceDataWithoutDates() throws Exception {
        fixtures.reference(job);

        JsonNode body = timeInStage("?jobId=" + job);

        List<String> stages = new ArrayList<>();
        body.get("stages").forEach(s -> stages.add(s.get("stage").asText()));
        assertThat(stages)
                .containsExactlyElementsOf(Arrays.stream(Stage.values()).map(Enum::name).toList());
        for (JsonNode stage : body.get("stages")) {
            Set<String> fields = new HashSet<>();
            stage.fieldNames().forEachRemaining(fields::add);
            assertThat(fields).containsExactlyInAnyOrder("stage", "averageHours", "samples");
        }
        assertThat(summary(body))
                .containsExactly("26.00/3", "36.00/2", "33.00/2", "null/0", "null/0");
    }

    @Test
    void referenceDataBetweenTheFifteenthAndTheTwentieth() throws Exception {
        fixtures.reference(job);

        JsonNode body = timeInStage("?jobId=" + job + "&from=2026-01-15&to=2026-01-20");

        assertThat(summary(body))
                .containsExactly("27.00/2", "24.00/1", "42.00/1", "null/0", "null/0");
    }

    @Test
    void noDataGivesTheFiveStagesWithoutSamples() throws Exception {
        fixtures.apply(job, "2026-04-01T00:00:00Z");

        assertThat(summary(timeInStage("?jobId=" + job))).containsOnly("null/0");
        assertThat(summary(timeInStage("?from=1990-01-01&to=1990-01-31"))).containsOnly("null/0");
    }

    @Test
    void eachFinishedStayCountsAndTheCurrentOneIsLeftOut() throws Exception {
        Card card = fixtures.apply(job, "2026-04-01T00:00:00Z");
        fixtures.move(card, Stage.INTERVIEW, "2026-04-01T10:00:00Z");
        fixtures.move(card, Stage.APPLIED, "2026-04-01T12:00:00Z");
        fixtures.move(card, Stage.INTERVIEW, "2026-04-01T20:00:00Z");

        // APPLIED: 10 h and 8 h; INTERVIEW: 2 h, while the stay since 20:00 is still open.
        assertThat(summary(timeInStage("?jobId=" + job)))
                .containsExactly("9.00/2", "2.00/1", "null/0", "null/0", "null/0");
    }

    @Test
    void movesAtTheSameInstantAreOrderedById() throws Exception {
        Card card = fixtures.apply(job, "2026-04-01T00:00:00Z");
        fixtures.move(card, Stage.INTERVIEW, "2026-04-01T01:00:00Z");
        fixtures.move(card, Stage.OFFER, "2026-04-01T01:00:00Z");
        fixtures.move(card, Stage.HIRED, "2026-04-01T03:00:00Z");

        assertThat(summary(timeInStage("?jobId=" + job)))
                .containsExactly("1.00/1", "0.00/1", "2.00/1", "null/0", "null/0");
    }

    @Test
    void staysStartAtTheInitialMovedAtNotAtCreatedAt() throws Exception {
        Card card = fixtures.apply(job, "2026-04-01T00:00:00Z", "2026-04-01T01:00:00Z");
        fixtures.move(card, Stage.INTERVIEW, "2026-04-01T03:00:00Z");

        assertThat(summary(timeInStage("?jobId=" + job)).getFirst()).isEqualTo("2.00/1");
    }

    @Test
    void averageIsRoundedHalfUpToTwoDecimals() throws Exception {
        // 7.5 minutes is 0.125 hours: half-up gives 0.13 where half-even would give 0.12.
        Card card = fixtures.apply(job, "2026-04-01T00:00:00Z");
        fixtures.move(card, Stage.INTERVIEW, "2026-04-01T00:07:30Z");

        assertThat(summary(timeInStage("?jobId=" + job)).getFirst()).isEqualTo("0.13/1");
    }

    @Test
    void samplesAreNotRoundedBeforeAveraging() throws Exception {
        // 0.006 h, 0.006 h and 0.002 h average 0.00467 h; rounding each first would give 0.01.
        for (String movedAt :
                List.of(
                        "2026-04-01T00:00:21.6Z",
                        "2026-04-01T00:00:21.6Z",
                        "2026-04-01T00:00:07.2Z")) {
            Card card = fixtures.apply(job, "2026-04-01T00:00:00Z");
            fixtures.move(card, Stage.INTERVIEW, movedAt);
        }

        assertThat(summary(timeInStage("?jobId=" + job)).getFirst()).isEqualTo("0.00/3");
    }

    @Test
    void fractionsOfASecondSurviveTheSum() throws Exception {
        // 17.6 s and 18.4 s average exactly 18 s, which is 0.005 h and rounds up to 0.01;
        // dropping the fractions would give 17.5 s and 0.00.
        for (String movedAt : List.of("2026-04-01T00:00:17.6Z", "2026-04-01T00:00:18.4Z")) {
            Card card = fixtures.apply(job, "2026-04-01T00:00:00Z");
            fixtures.move(card, Stage.INTERVIEW, movedAt);
        }

        assertThat(summary(timeInStage("?jobId=" + job)).getFirst()).isEqualTo("0.01/2");
    }

    @Test
    void movesOutsideTheRangeStillCountForAnIncludedApplication() throws Exception {
        Card card = fixtures.apply(job, "2026-05-31T12:00:00Z");
        fixtures.move(card, Stage.INTERVIEW, "2026-07-01T12:00:00Z");
        fixtures.move(card, Stage.OFFER, "2026-07-02T12:00:00Z");

        assertThat(summary(timeInStage("?jobId=" + job + "&from=2026-05-01&to=2026-05-31")))
                .containsExactly("744.00/1", "24.00/1", "null/0", "null/0", "null/0");
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
        timeInStage(query);
        return statistics.getPrepareStatementCount();
    }
}
