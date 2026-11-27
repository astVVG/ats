package io.github.astvvg.ats.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/** Covers the filters shared by the three metrics endpoints and the funnel itself. */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TestUsers.class})
@Transactional
class MetricsFunnelIntegrationTest {

    private static final List<String> ENDPOINTS =
            List.of("funnel", "time-in-stage", "time-to-hire");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TestUsers testUsers;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private User recruiter;
    private User admin;
    private MetricsFixtures fixtures;
    private long job;

    @BeforeEach
    void setUp() {
        recruiter = testUsers.create(Role.RECRUITER);
        admin = testUsers.create(Role.ADMIN);
        fixtures = new MetricsFixtures(jdbcTemplate, recruiter.getId());
        job = fixtures.job("OPEN");
    }

    static List<String> endpoints() {
        return ENDPOINTS;
    }

    private ResultActions request(String path, String query, User user) throws Exception {
        return MetricsFixtures.metrics(
                mockMvc, entityManager, path, query, user == null ? null : testUsers.bearer(user));
    }

    private JsonNode funnel(String query) throws Exception {
        return MetricsFixtures.json(
                objectMapper, request("funnel", query, recruiter).andExpect(status().isOk()));
    }

    private static List<Long> counts(JsonNode body) {
        List<Long> counts = new ArrayList<>();
        body.get("stages").forEach(s -> counts.add(s.get("count").asLong()));
        return counts;
    }

    /** Conversions as plain strings, "null" for null, so 0.5 and 0.5000 compare equal. */
    private static List<String> conversions(JsonNode body) {
        List<String> conversions = new ArrayList<>();
        body.get("stages")
                .forEach(s -> {
                    JsonNode value = s.get("conversionToNext");
                    conversions.add(
                            value.isNull()
                                    ? "null"
                                    : value.decimalValue().stripTrailingZeros().toPlainString());
                });
        return conversions;
    }

    private static void assertProblem(ResultActions result, int status) throws Exception {
        result.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(status));
    }

    // ---- funnel ----

    @ParameterizedTest
    @EnumSource(Role.class)
    void referenceDataWithoutDates(Role role) throws Exception {
        fixtures.reference(job);

        JsonNode body =
                MetricsFixtures.json(
                        objectMapper,
                        request("funnel", "?jobId=" + job, role == Role.ADMIN ? admin : recruiter)
                                .andExpect(status().isOk()));

        List<String> stages = new ArrayList<>();
        body.get("stages").forEach(s -> stages.add(s.get("stage").asText()));
        assertThat(stages)
                .containsExactlyElementsOf(Arrays.stream(Stage.values()).map(Enum::name).toList());
        for (JsonNode stage : body.get("stages")) {
            Set<String> fields = new HashSet<>();
            stage.fieldNames().forEachRemaining(fields::add);
            assertThat(fields).containsExactlyInAnyOrder("stage", "count", "conversionToNext");
        }
        assertThat(counts(body)).containsExactly(4L, 2L, 2L, 2L, 1L);
        assertThat(conversions(body)).containsExactly("0.5", "1", "1", "null", "null");
        assertThat(body.get("stages").get(0).get("conversionToNext").decimalValue())
                .isEqualByComparingTo(new BigDecimal("0.5"));
    }

    @Test
    void referenceDataBetweenTheFifteenthAndTheTwentieth() throws Exception {
        fixtures.reference(job);

        JsonNode body = funnel("?jobId=" + job + "&from=2026-01-15&to=2026-01-20");

        assertThat(counts(body)).containsExactly(2L, 1L, 1L, 1L, 1L);
        assertThat(conversions(body)).containsExactly("0.5", "1", "1", "null", "null");
    }

    @Test
    void noDataGivesTheFiveStagesWithZeroCountsAndNullConversions() throws Exception {
        JsonNode emptyJob = funnel("?jobId=" + job);
        JsonNode emptyRange = funnel("?from=1990-01-01&to=1990-01-31");

        for (JsonNode body : List.of(emptyJob, emptyRange)) {
            assertThat(body.get("stages")).hasSize(5);
            assertThat(counts(body)).containsOnly(0L);
            assertThat(conversions(body)).containsOnly("null");
        }
    }

    @Test
    void anApplicationThatRepeatsAStageCountsOnce() throws Exception {
        Card card = fixtures.apply(job, "2026-04-01T00:00:00Z");
        fixtures.move(card, Stage.INTERVIEW, "2026-04-02T00:00:00Z");
        fixtures.move(card, Stage.APPLIED, "2026-04-03T00:00:00Z");
        fixtures.move(card, Stage.INTERVIEW, "2026-04-04T00:00:00Z");

        JsonNode body = funnel("?jobId=" + job);

        assertThat(counts(body)).containsExactly(1L, 1L, 0L, 0L, 0L);
        assertThat(conversions(body)).containsExactly("1", "0", "null", "null", "null");
    }

    @Test
    void skippedStagesDoNotCountAndConversionMayExceedOne() throws Exception {
        Card through = fixtures.apply(job, "2026-04-01T00:00:00Z");
        fixtures.move(through, Stage.INTERVIEW, "2026-04-02T00:00:00Z");
        fixtures.move(through, Stage.OFFER, "2026-04-03T00:00:00Z");
        for (int i = 0; i < 2; i++) {
            Card skipping = fixtures.apply(job, "2026-04-01T00:00:00Z");
            fixtures.move(skipping, Stage.OFFER, "2026-04-02T00:00:00Z");
        }

        JsonNode body = funnel("?jobId=" + job);

        assertThat(counts(body)).containsExactly(3L, 1L, 3L, 0L, 0L);
        assertThat(conversions(body)).containsExactly("0.3333", "3", "0", "null", "null");
    }

    @Test
    void conversionIsNullWhenTheCurrentCountIsZero() throws Exception {
        Card card = fixtures.apply(job, "2026-04-01T00:00:00Z");
        fixtures.move(card, Stage.HIRED, "2026-04-02T00:00:00Z");

        JsonNode body = funnel("?jobId=" + job);

        assertThat(counts(body)).containsExactly(1L, 0L, 0L, 1L, 0L);
        assertThat(conversions(body)).containsExactly("0", "null", "null", "null", "null");
    }

    // ---- filters (shared by the three endpoints) ----

    @Test
    void jobIdSeparatesJobsAndIncludesClosedOnes() throws Exception {
        long closed = fixtures.job("CLOSED");
        fixtures.reference(job);
        Card card = fixtures.apply(closed, "2026-01-12T00:00:00Z");
        fixtures.move(card, Stage.REJECTED, "2026-01-13T00:00:00Z");

        assertThat(counts(funnel("?jobId=" + job))).containsExactly(4L, 2L, 2L, 2L, 1L);
        assertThat(counts(funnel("?jobId=" + closed))).containsExactly(1L, 0L, 0L, 0L, 1L);
    }

    @Test
    void withoutJobIdEveryJobIsIncluded() throws Exception {
        // A far-off year keeps the data of other tests out of the range.
        long other = fixtures.job("CLOSED");
        fixtures.apply(job, "2040-05-01T12:00:00Z");
        Card hired = fixtures.apply(other, "2040-05-02T12:00:00Z");
        fixtures.move(hired, Stage.HIRED, "2040-05-03T12:00:00Z");

        assertThat(counts(funnel("?from=2040-05-01&to=2040-05-31")))
                .containsExactly(2L, 0L, 0L, 1L, 0L);
    }

    @Test
    void dayBoundariesFollowTheConfiguredTimeZone() throws Exception {
        // America/Mexico_City is UTC-6 all year, so 2026-03-11 starts at 06:00 UTC.
        fixtures.apply(job, "2026-03-11T05:59:59Z");
        Card next = fixtures.apply(job, "2026-03-11T06:00:00Z");
        fixtures.move(next, Stage.INTERVIEW, "2026-03-12T00:00:00Z");

        assertThat(counts(funnel("?jobId=" + job + "&to=2026-03-10")))
                .containsExactly(1L, 0L, 0L, 0L, 0L);
        assertThat(counts(funnel("?jobId=" + job + "&from=2026-03-11")))
                .containsExactly(1L, 1L, 0L, 0L, 0L);
        assertThat(counts(funnel("?jobId=" + job + "&from=2026-03-10&to=2026-03-11")))
                .containsExactly(2L, 1L, 0L, 0L, 0L);
    }

    @Test
    void onlyFromOrOnlyToIsEnough() throws Exception {
        fixtures.reference(job);

        assertThat(counts(funnel("?jobId=" + job + "&from=2026-01-20")))
                .containsExactly(2L, 0L, 1L, 1L, 0L);
        assertThat(counts(funnel("?jobId=" + job + "&to=2026-01-14")))
                .containsExactly(1L, 1L, 1L, 1L, 0L);
    }

    @Test
    void movesOutsideTheRangeStillCountForAnIncludedApplication() throws Exception {
        Card card = fixtures.apply(job, "2026-05-31T12:00:00Z");
        fixtures.move(card, Stage.INTERVIEW, "2026-07-01T12:00:00Z");

        assertThat(counts(funnel("?jobId=" + job + "&from=2026-05-01&to=2026-05-31")))
                .containsExactly(1L, 1L, 0L, 0L, 0L);
        assertThat(counts(funnel("?jobId=" + job + "&from=2026-07-01"))).containsOnly(0L);
    }

    @ParameterizedTest
    @MethodSource("endpoints")
    void fromAfterToIs400OnTheFromField(String path) throws Exception {
        ResultActions result = request(path, "?from=2026-01-21&to=2026-01-20", recruiter);

        assertProblem(result, 400);
        result.andExpect(jsonPath("$.errors[0].field").value("from"));
    }

    @Test
    void theSameDayInFromAndToIsAccepted() throws Exception {
        request("funnel", "?from=2026-01-20&to=2026-01-20", recruiter).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "?from=2026-13-01",
                "?from=2026-02-30",
                "?to=20-01-2026",
                "?to=2026-01-20T00:00:00",
                "?from=yesterday",
                "?jobId=abc",
                "?jobId=1.5",
                "?jobId=99999999999999999999"
            })
    void malformedParametersAre400ProblemDetails(String query) throws Exception {
        for (String path : ENDPOINTS) {
            assertProblem(request(path, query, recruiter), 400);
        }
    }

    @Test
    void unknownJobIs404OnEveryEndpoint() throws Exception {
        for (String path : ENDPOINTS) {
            assertProblem(request(path, "?jobId=" + Long.MAX_VALUE, recruiter), 404);
        }
    }

    @Test
    void everyEndpointRequiresToken() throws Exception {
        for (String path : ENDPOINTS) {
            assertProblem(request(path, null, null), 401);
        }
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void everyEndpointAnswers200ForBothRoles(Role role) throws Exception {
        for (String path : ENDPOINTS) {
            request(path, "?jobId=" + job, role == Role.ADMIN ? admin : recruiter)
                    .andExpect(status().isOk());
        }
    }

    // ---- statements ----

    @Test
    void funnelStatementsDoNotGrowWithData() throws Exception {
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
        assertThat(statements("?from=2026-01-01")).isEqualTo(withOne - 1);
    }

    private long statements(String query) throws Exception {
        entityManager.flush();
        entityManager.clear();
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        request("funnel", query, recruiter).andExpect(status().isOk());
        return statistics.getPrepareStatementCount();
    }
}
