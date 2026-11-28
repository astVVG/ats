package io.github.astvvg.ats.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.astvvg.ats.application.domain.Application;
import io.github.astvvg.ats.application.domain.Stage;
import io.github.astvvg.ats.application.repository.ApplicationRepository;
import io.github.astvvg.ats.candidate.domain.Candidate;
import io.github.astvvg.ats.candidate.repository.CandidateRepository;
import io.github.astvvg.ats.job.domain.Job;
import io.github.astvvg.ats.job.domain.JobStatus;
import io.github.astvvg.ats.job.repository.JobRepository;
import io.github.astvvg.ats.support.TestUsers;
import io.github.astvvg.ats.support.TestcontainersConfiguration;
import io.github.astvvg.ats.user.domain.Role;
import io.github.astvvg.ats.user.domain.User;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TestUsers.class})
@Transactional
class ApplicationRatingIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TestUsers testUsers;
    @Autowired private JobRepository jobRepository;
    @Autowired private CandidateRepository candidateRepository;
    @Autowired private ApplicationRepository applicationRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;

    private User recruiter;
    private User admin;
    private Job job;
    private Application first;
    private Application target;
    private Application last;

    @BeforeEach
    void setUp() {
        recruiter = testUsers.create(Role.RECRUITER);
        admin = testUsers.create(Role.ADMIN);
        job = job(JobStatus.OPEN);
        first = application(job, Stage.INTERVIEW, 0);
        target = application(job, Stage.INTERVIEW, 1);
        last = application(job, Stage.INTERVIEW, 2);
    }

    // ---- helpers ----

    private Job job(JobStatus status) {
        Job created = new Job("Rating Job", null, null, null, recruiter);
        created.setStatus(status);
        return jobRepository.saveAndFlush(created);
    }

    private Application application(Job owner, Stage stage, int position) {
        Candidate candidate =
                candidateRepository.saveAndFlush(
                        new Candidate("Cand", TestUsers.uniqueEmail("rating"), null, null));
        return applicationRepository.saveAndFlush(
                new Application(owner, candidate, stage, position));
    }

    private User user(Role role) {
        return role == Role.ADMIN ? admin : recruiter;
    }

    private ResultActions rate(Object id, String body, User user) throws Exception {
        // Each request starts with a fresh persistence context, as in production.
        entityManager.flush();
        entityManager.clear();
        var request =
                patch("/api/applications/" + id + "/rating")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body);
        if (user != null) {
            request.header(HttpHeaders.AUTHORIZATION, testUsers.bearer(user));
        }
        return mockMvc.perform(request);
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private Map<String, Object> row(Application application) {
        entityManager.flush();
        return jdbcTemplate.queryForMap(
                "SELECT stage, position, rating, updated_at FROM applications WHERE id = ?",
                application.getId());
    }

    private void setRating(Application application, Integer rating) {
        jdbcTemplate.update(
                "UPDATE applications SET rating = ? WHERE id = ?", rating, application.getId());
    }

    private int historyCount() {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM stage_history h JOIN applications a"
                                + " ON a.id = h.application_id WHERE a.job_id = ?",
                        Integer.class,
                        job.getId());
        return count == null ? 0 : count;
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static void assertRatingError(ResultActions result) throws Exception {
        result.andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[0].field").value("rating"));
    }

    private static void assertProblem(ResultActions result, int status) throws Exception {
        result.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(status));
    }

    // ---- tests ----

    @ParameterizedTest
    @EnumSource(Role.class)
    void acceptsTheBoundsOfTheRange(Role role) throws Exception {
        JsonNode one =
                json(rate(target.getId(), "{\"rating\":1}", user(role)).andExpect(status().isOk()));
        assertThat(one.get("rating").asInt()).isEqualTo(1);
        assertThat(row(target).get("rating")).isEqualTo(1);

        JsonNode five =
                json(rate(target.getId(), "{\"rating\":5}", user(role)).andExpect(status().isOk()));
        assertThat(five.get("rating").asInt()).isEqualTo(5);
        assertThat(row(target).get("rating")).isEqualTo(5);
    }

    @Test
    void respondsWithTheSameShapeAsCreate() throws Exception {
        JsonNode body =
                json(rate(target.getId(), "{\"rating\":3}", recruiter).andExpect(status().isOk()));

        assertThat(fieldNames(body))
                .containsExactlyInAnyOrder(
                        "id",
                        "jobId",
                        "candidateId",
                        "stage",
                        "position",
                        "rating",
                        "createdAt",
                        "updatedAt");
        assertThat(body.get("id").asLong()).isEqualTo(target.getId());
        assertThat(body.get("jobId").asLong()).isEqualTo(job.getId());
        assertThat(body.get("stage").asText()).isEqualTo("INTERVIEW");
        assertThat(body.get("position").asInt()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"rating\":null}", "{}"})
    void nullOrAnEmptyBodyRemovesTheRating(String body) throws Exception {
        setRating(target, 4);

        JsonNode response = json(rate(target.getId(), body, recruiter).andExpect(status().isOk()));

        assertThat(response.has("rating")).isTrue();
        assertThat(response.get("rating").isNull()).isTrue();
        assertThat(row(target).get("rating")).isNull();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"rating\":0}",
                "{\"rating\":6}",
                "{\"rating\":-1}",
                "{\"rating\":3.5}",
                "{\"rating\":3.0}",
                "{\"rating\":\"abc\"}",
                "{\"rating\":\"3\"}",
                "{\"rating\":true}",
                "{\"rating\":99999999999}",
                "{\"rating\":[3]}",
                "{\"rating\":{\"value\":3}}"
            })
    void invalidRatingsAre400AndChangeNothing(String body) throws Exception {
        setRating(target, 2);
        Map<String, Object> before = row(target);

        assertRatingError(rate(target.getId(), body, recruiter));

        assertThat(row(target)).isEqualTo(before);
    }

    @Test
    void missingOrMalformedBodyIs400() throws Exception {
        Map<String, Object> before = row(target);

        assertProblem(rate(target.getId(), "", recruiter), 400);
        assertProblem(rate(target.getId(), "{\"rating\":", recruiter), 400);

        assertThat(row(target)).isEqualTo(before);
    }

    /** MySQL hands DATETIME columns over as the stored local value, which is UTC. */
    private static Instant storedInstant(Map<String, Object> row) {
        return ((LocalDateTime) row.get("updated_at")).toInstant(ZoneOffset.UTC);
    }

    @Test
    void sameValueChangesNothingNotEvenUpdatedAt() throws Exception {
        setRating(target, 3);
        Map<String, Object> before = row(target);

        JsonNode body =
                json(rate(target.getId(), "{\"rating\":3}", recruiter).andExpect(status().isOk()));

        assertThat(row(target)).isEqualTo(before);
        assertThat(Instant.parse(body.get("updatedAt").asText())).isEqualTo(storedInstant(before));
    }

    @Test
    void removingAMissingRatingChangesNothing() throws Exception {
        Map<String, Object> before = row(target);

        rate(target.getId(), "{}", recruiter).andExpect(status().isOk());

        assertThat(row(target)).isEqualTo(before);
    }

    @Test
    void changeUpdatesOnlyRatingAndUpdatedAtOfThatApplication() throws Exception {
        Map<String, Object> before = row(target);
        Map<String, Object> firstBefore = row(first);
        Map<String, Object> lastBefore = row(last);
        int history = historyCount();

        JsonNode body =
                json(rate(target.getId(), "{\"rating\":4}", admin).andExpect(status().isOk()));

        Map<String, Object> after = row(target);
        assertThat(after.get("rating")).isEqualTo(4);
        assertThat(after.get("stage")).isEqualTo(before.get("stage"));
        assertThat(after.get("position")).isEqualTo(before.get("position"));
        Instant updatedBefore = storedInstant(before);
        Instant updatedAfter = storedInstant(after);
        assertThat(updatedAfter).isAfter(updatedBefore);
        assertThat(Instant.parse(body.get("updatedAt").asText())).isEqualTo(updatedAfter);
        assertThat(row(first)).isEqualTo(firstBefore);
        assertThat(row(last)).isEqualTo(lastBefore);
        assertThat(historyCount()).isEqualTo(history);
    }

    @Test
    void ratingShowsOnTheBoard() throws Exception {
        rate(target.getId(), "{\"rating\":5}", recruiter).andExpect(status().isOk());
        entityManager.clear();

        JsonNode board =
                json(
                        mockMvc.perform(
                                        get("/api/jobs/" + job.getId() + "/board")
                                                .header(
                                                        HttpHeaders.AUTHORIZATION,
                                                        testUsers.bearer(recruiter)))
                                .andExpect(status().isOk()));

        JsonNode interview =
                board.get("columns").get(Stage.INTERVIEW.ordinal()).get("applications");
        assertThat(interview.get(1).get("id").asLong()).isEqualTo(target.getId());
        assertThat(interview.get(1).get("rating").asInt()).isEqualTo(5);
        assertThat(interview.get(0).get("rating").isNull()).isTrue();
    }

    @Test
    void ratingIsAllowedOnAClosedJob() throws Exception {
        Application closed = application(job(JobStatus.CLOSED), Stage.HIRED, 0);

        rate(closed.getId(), "{\"rating\":2}", recruiter)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(2));
        assertThat(row(closed).get("rating")).isEqualTo(2);
    }

    @Test
    void ratingAMissingApplicationIs404() throws Exception {
        assertProblem(rate(Long.MAX_VALUE, "{\"rating\":3}", recruiter), 404);
    }

    @Test
    void ratingRequiresToken() throws Exception {
        Map<String, Object> before = row(target);

        assertProblem(rate(target.getId(), "{\"rating\":3}", null), 401);

        assertThat(row(target)).isEqualTo(before);
    }
}
