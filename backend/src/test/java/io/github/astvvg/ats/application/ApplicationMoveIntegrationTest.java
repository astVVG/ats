package io.github.astvvg.ats.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
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
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
class ApplicationMoveIntegrationTest {

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
    private String recruiterAuth;
    private String adminAuth;
    private Job job;
    private Job otherJob;
    private Map<Long, String> otherJobBefore;

    @BeforeEach
    void setUp() {
        recruiter = testUsers.create(Role.RECRUITER);
        admin = testUsers.create(Role.ADMIN);
        recruiterAuth = testUsers.bearer(recruiter);
        adminAuth = testUsers.bearer(admin);
        job = job(JobStatus.OPEN);
        // A second job with cards in every column must never be touched by moves on the first.
        otherJob = job(JobStatus.OPEN);
        for (Stage stage : Stage.values()) {
            column(otherJob, stage, 2);
        }
        otherJobBefore = snapshot(otherJob);
    }

    // ---- helpers ----

    private Job job(JobStatus status) {
        Job created = new Job("Move Job", null, null, null, recruiter);
        created.setStatus(status);
        return jobRepository.saveAndFlush(created);
    }

    /** Creates {@code size} applications at positions 0..size-1 and returns them in order. */
    private List<Application> column(Job target, Stage stage, int size) {
        List<Application> cards = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            Candidate candidate =
                    candidateRepository.saveAndFlush(
                            new Candidate("Cand", TestUsers.uniqueEmail("move"), null, null));
            cards.add(
                    applicationRepository.saveAndFlush(
                            new Application(target, candidate, stage, i)));
        }
        return cards;
    }

    private ResultActions move(String auth, long id, Object stage, Object position)
            throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("stage", stage);
        body.put("position", position);
        // Each request starts with a fresh persistence context, as in production.
        entityManager.flush();
        entityManager.clear();
        var request =
                patch("/api/applications/" + id + "/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body));
        if (auth != null) {
            request.header(HttpHeaders.AUTHORIZATION, auth);
        }
        return mockMvc.perform(request);
    }

    private JsonNode moved(String auth, Application application, Stage stage, int position)
            throws Exception {
        String json =
                move(auth, application.getId(), stage.name(), position)
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        JsonNode body = objectMapper.readTree(json);
        assertThat(body.get("id").asLong()).isEqualTo(application.getId());
        assertThat(body.get("stage").asText()).isEqualTo(stage.name());
        assertThat(body.get("position").asInt()).isEqualTo(position);
        return body;
    }

    /** Ids of a column in position order. */
    private List<Long> ids(Job target, Stage stage) {
        return jdbcTemplate.queryForList(
                "SELECT id FROM applications WHERE job_id = ? AND stage = ? ORDER BY position",
                Long.class,
                target.getId(),
                stage.name());
    }

    private static List<Long> ids(List<Application> cards) {
        return cards.stream().map(Application::getId).toList();
    }

    private Map<Long, String> snapshot(Job target) {
        Map<Long, String> rows = new HashMap<>();
        jdbcTemplate.query(
                "SELECT id, stage, position, updated_at FROM applications WHERE job_id = ?",
                rs -> {
                    rows.put(
                            rs.getLong("id"),
                            rs.getString("stage")
                                    + "/"
                                    + rs.getInt("position")
                                    + "/"
                                    + rs.getTimestamp("updated_at"));
                },
                target.getId());
        return rows;
    }

    private Timestamp updatedAt(long id) {
        return jdbcTemplate.queryForObject(
                "SELECT updated_at FROM applications WHERE id = ?", Timestamp.class, id);
    }

    private List<Map<String, Object>> history(long id) {
        return jdbcTemplate.queryForList(
                "SELECT from_stage, to_stage, moved_by, moved_at FROM stage_history"
                        + " WHERE application_id = ? ORDER BY id",
                id);
    }

    /** Every column of the job holds exactly positions 0..n-1, and the other job is intact. */
    private void assertContiguous() {
        for (Stage stage : Stage.values()) {
            List<Integer> positions =
                    jdbcTemplate.queryForList(
                            "SELECT position FROM applications WHERE job_id = ? AND stage = ?"
                                    + " ORDER BY position",
                            Integer.class,
                            job.getId(),
                            stage.name());
            assertThat(positions)
                    .as("positions of %s", stage)
                    .isEqualTo(IntStream.range(0, positions.size()).boxed().toList());
        }
        assertThat(snapshot(otherJob)).isEqualTo(otherJobBefore);
    }

    private static void assertProblem(ResultActions result, int status) throws Exception {
        result.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(status));
    }

    private static void assertFieldError(ResultActions result, String field) throws Exception {
        assertProblem(result, 400);
        result.andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors[*].field", contains(field)));
    }

    // ---- reorder inside a column ----

    @Test
    void reorderDownwardsInTheSameStage() throws Exception {
        List<Application> applied = column(job, Stage.APPLIED, 5);

        moved(recruiterAuth, applied.get(1), Stage.APPLIED, 3);

        assertThat(ids(job, Stage.APPLIED))
                .containsExactly(
                        applied.get(0).getId(),
                        applied.get(2).getId(),
                        applied.get(3).getId(),
                        applied.get(1).getId(),
                        applied.get(4).getId());
        assertThat(history(applied.get(1).getId())).isEmpty();
        assertContiguous();
    }

    @Test
    void reorderUpwardsInTheSameStage() throws Exception {
        List<Application> applied = column(job, Stage.APPLIED, 5);

        moved(recruiterAuth, applied.get(3), Stage.APPLIED, 1);

        assertThat(ids(job, Stage.APPLIED))
                .containsExactly(
                        applied.get(0).getId(),
                        applied.get(3).getId(),
                        applied.get(1).getId(),
                        applied.get(2).getId(),
                        applied.get(4).getId());
        assertThat(history(applied.get(3).getId())).isEmpty();
        assertContiguous();
    }

    @Test
    void firstToLastAndLastToFirstInTheSameStage() throws Exception {
        List<Application> offer = column(job, Stage.OFFER, 4);

        moved(recruiterAuth, offer.get(0), Stage.OFFER, 3);
        assertThat(ids(job, Stage.OFFER))
                .containsExactly(
                        offer.get(1).getId(),
                        offer.get(2).getId(),
                        offer.get(3).getId(),
                        offer.get(0).getId());
        assertContiguous();

        moved(recruiterAuth, offer.get(0), Stage.OFFER, 0);
        assertThat(ids(job, Stage.OFFER)).isEqualTo(ids(offer));
        assertContiguous();
    }

    @Test
    void onlyTheMovedApplicationsGetANewUpdatedAt() throws Exception {
        List<Application> applied = column(job, Stage.APPLIED, 4);
        List<Application> interview = column(job, Stage.INTERVIEW, 2);
        Map<Long, Timestamp> before = new HashMap<>();
        for (Application card : applied) {
            before.put(card.getId(), updatedAt(card.getId()));
        }
        for (Application card : interview) {
            before.put(card.getId(), updatedAt(card.getId()));
        }

        // Shifts applied 1..3 and interview 1.
        moved(recruiterAuth, applied.get(0), Stage.INTERVIEW, 1);
        // applied 2 is now at 1; this reorder shifts applied 1.
        moved(recruiterAuth, applied.get(2), Stage.APPLIED, 0);

        List<Long> movedIds = List.of(applied.get(0).getId(), applied.get(2).getId());
        before.forEach((id, timestamp) -> {
            if (movedIds.contains(id)) {
                assertThat(updatedAt(id)).as("moved %s", id).isAfter(timestamp);
            } else {
                assertThat(updatedAt(id)).as("shifted %s", id).isEqualTo(timestamp);
            }
        });
        assertContiguous();
    }

    // ---- move to another stage ----

    @Test
    void moveToTheStartOfAnotherStage() throws Exception {
        List<Application> applied = column(job, Stage.APPLIED, 3);
        List<Application> interview = column(job, Stage.INTERVIEW, 2);

        moved(recruiterAuth, applied.get(1), Stage.INTERVIEW, 0);

        assertThat(ids(job, Stage.APPLIED))
                .containsExactly(applied.get(0).getId(), applied.get(2).getId());
        assertThat(ids(job, Stage.INTERVIEW))
                .containsExactly(
                        applied.get(1).getId(), interview.get(0).getId(), interview.get(1).getId());
        assertContiguous();
    }

    @Test
    void moveToTheMiddleOfAnotherStage() throws Exception {
        List<Application> applied = column(job, Stage.APPLIED, 3);
        List<Application> interview = column(job, Stage.INTERVIEW, 3);

        moved(recruiterAuth, applied.get(2), Stage.INTERVIEW, 1);

        assertThat(ids(job, Stage.APPLIED))
                .containsExactly(applied.get(0).getId(), applied.get(1).getId());
        assertThat(ids(job, Stage.INTERVIEW))
                .containsExactly(
                        interview.get(0).getId(),
                        applied.get(2).getId(),
                        interview.get(1).getId(),
                        interview.get(2).getId());
        assertContiguous();
    }

    @Test
    void moveToTheEndOfAnotherStageWithPositionN() throws Exception {
        List<Application> applied = column(job, Stage.APPLIED, 2);
        List<Application> interview = column(job, Stage.INTERVIEW, 2);

        moved(recruiterAuth, applied.get(0), Stage.INTERVIEW, 2);

        assertThat(ids(job, Stage.APPLIED)).containsExactly(applied.get(1).getId());
        assertThat(ids(job, Stage.INTERVIEW))
                .containsExactly(
                        interview.get(0).getId(), interview.get(1).getId(), applied.get(0).getId());
        assertContiguous();
    }

    @Test
    void moveTheOnlyApplicationOfAColumnToAnEmptyColumn() throws Exception {
        Application only = column(job, Stage.OFFER, 1).get(0);

        moved(recruiterAuth, only, Stage.REJECTED, 0);

        assertThat(ids(job, Stage.OFFER)).isEmpty();
        assertThat(ids(job, Stage.REJECTED)).containsExactly(only.getId());
        assertContiguous();
    }

    @Test
    void stageChangeRecordsOneHistoryRowPerMove() throws Exception {
        Application application = column(job, Stage.APPLIED, 1).get(0);

        moved(recruiterAuth, application, Stage.INTERVIEW, 0);
        moved(adminAuth, application, Stage.OFFER, 0);
        moved(adminAuth, application, Stage.OFFER, 0);

        List<Map<String, Object>> rows = history(application.getId());
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get("from_stage")).isEqualTo("APPLIED");
        assertThat(rows.get(0).get("to_stage")).isEqualTo("INTERVIEW");
        assertThat(((Number) rows.get(0).get("moved_by")).longValue()).isEqualTo(recruiter.getId());
        assertThat(rows.get(0).get("moved_at")).isNotNull();
        assertThat(rows.get(1).get("from_stage")).isEqualTo("INTERVIEW");
        assertThat(rows.get(1).get("to_stage")).isEqualTo("OFFER");
        assertThat(((Number) rows.get(1).get("moved_by")).longValue()).isEqualTo(admin.getId());
        assertContiguous();
    }

    // ---- no-op ----

    @Test
    void sameStageAndPositionIsANoOp() throws Exception {
        List<Application> applied = column(job, Stage.APPLIED, 3);
        Map<Long, String> before = snapshot(job);

        JsonNode body = moved(recruiterAuth, applied.get(1), Stage.APPLIED, 1);

        assertThat(snapshot(job)).isEqualTo(before);
        assertThat(history(applied.get(1).getId())).isEmpty();
        assertThat(Timestamp.from(Instant.parse(body.get("updatedAt").asText())))
                .isEqualTo(updatedAt(applied.get(1).getId()));
        assertContiguous();
    }

    // ---- HIRED ----

    @Test
    void recruiterCannotMoveOutOfHired() throws Exception {
        Application hired = column(job, Stage.HIRED, 1).get(0);
        column(job, Stage.OFFER, 1);
        Map<Long, String> before = snapshot(job);

        assertProblem(move(recruiterAuth, hired.getId(), "OFFER", 0), 403);

        assertThat(snapshot(job)).isEqualTo(before);
        assertThat(history(hired.getId())).isEmpty();
    }

    @Test
    void adminCanMoveOutOfHired() throws Exception {
        Application hired = column(job, Stage.HIRED, 2).get(0);

        moved(adminAuth, hired, Stage.OFFER, 0);

        assertThat(history(hired.getId())).hasSize(1);
        assertContiguous();
    }

    @Test
    void recruiterCanMoveIntoHiredAndReorderInsideIt() throws Exception {
        List<Application> hired = column(job, Stage.HIRED, 2);
        Application offer = column(job, Stage.OFFER, 1).get(0);

        moved(recruiterAuth, offer, Stage.HIRED, 2);
        moved(recruiterAuth, hired.get(0), Stage.HIRED, 2);

        assertThat(ids(job, Stage.HIRED))
                .containsExactly(hired.get(1).getId(), offer.getId(), hired.get(0).getId());
        assertContiguous();
    }

    // ---- invalid requests ----

    @Test
    void negativePositionIsBadRequest() throws Exception {
        Application application = column(job, Stage.APPLIED, 2).get(0);
        Map<Long, String> before = snapshot(job);

        assertFieldError(move(recruiterAuth, application.getId(), "APPLIED", -1), "position");

        assertThat(snapshot(job)).isEqualTo(before);
    }

    @Test
    void positionPastTheEndOfTheSameStageIsBadRequest() throws Exception {
        Application application = column(job, Stage.APPLIED, 3).get(0);
        Map<Long, String> before = snapshot(job);

        ResultActions result = move(recruiterAuth, application.getId(), "APPLIED", 3);

        assertFieldError(result, "position");
        result.andExpect(jsonPath("$.errors[0].message").value("must be between 0 and 2"));
        assertThat(snapshot(job)).isEqualTo(before);
    }

    @Test
    void positionPastNInAnotherStageIsBadRequest() throws Exception {
        Application application = column(job, Stage.APPLIED, 1).get(0);
        column(job, Stage.INTERVIEW, 2);
        Map<Long, String> before = snapshot(job);

        assertFieldError(move(recruiterAuth, application.getId(), "INTERVIEW", 3), "position");
        assertFieldError(move(recruiterAuth, application.getId(), "OFFER", 1), "position");

        assertThat(snapshot(job)).isEqualTo(before);
        assertThat(history(application.getId())).isEmpty();
    }

    @Test
    void missingStageOrPositionIsBadRequest() throws Exception {
        Application application = column(job, Stage.APPLIED, 1).get(0);

        assertFieldError(move(recruiterAuth, application.getId(), null, 0), "stage");
        assertFieldError(move(recruiterAuth, application.getId(), "APPLIED", null), "position");
    }

    @Test
    void unknownStageIsBadRequest() throws Exception {
        Application application = column(job, Stage.APPLIED, 1).get(0);

        assertFieldError(move(recruiterAuth, application.getId(), "ONBOARDING", 0), "stage");
        assertThat(ids(job, Stage.APPLIED)).containsExactly(application.getId());
    }

    @Test
    void missingApplicationIsNotFound() throws Exception {
        assertProblem(move(recruiterAuth, Long.MAX_VALUE, "APPLIED", 0), 404);
    }

    @Test
    void closedJobIsConflictAndChangesNothing() throws Exception {
        Job closed = job(JobStatus.CLOSED);
        Application application = column(closed, Stage.APPLIED, 2).get(0);
        Map<Long, String> before = snapshot(closed);

        assertProblem(move(adminAuth, application.getId(), "INTERVIEW", 0), 409);
        assertProblem(move(adminAuth, application.getId(), "APPLIED", 1), 409);

        assertThat(snapshot(closed)).isEqualTo(before);
        assertThat(history(application.getId())).isEmpty();
    }

    @Test
    void moveRequiresToken() throws Exception {
        Application application = column(job, Stage.APPLIED, 1).get(0);

        assertProblem(move(null, application.getId(), "INTERVIEW", 0), 401);
        assertThat(ids(job, Stage.APPLIED)).containsExactly(application.getId());
    }
}
