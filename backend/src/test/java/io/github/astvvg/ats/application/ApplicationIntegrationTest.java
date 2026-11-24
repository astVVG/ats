package io.github.astvvg.ats.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TestUsers.class})
@Transactional
class ApplicationIntegrationTest {

    private static final String APPLICATIONS = "/api/applications";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TestUsers testUsers;
    @Autowired private JobRepository jobRepository;
    @Autowired private CandidateRepository candidateRepository;
    @Autowired private ApplicationRepository applicationRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private User recruiter;
    private String recruiterAuth;

    @BeforeEach
    void setUp() {
        recruiter = testUsers.create(Role.RECRUITER);
        recruiterAuth = testUsers.bearer(recruiter);
    }

    private Job job(JobStatus status) {
        Job job = new Job("Backend Developer", null, null, null, recruiter);
        job.setStatus(status);
        return jobRepository.saveAndFlush(job);
    }

    private Candidate candidate() {
        return candidateRepository.saveAndFlush(
                new Candidate("Ana Lopez", TestUsers.uniqueEmail("applicant"), null, null));
    }

    private ResultActions apply(String auth, Object jobId, Object candidateId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("jobId", jobId);
        body.put("candidateId", candidateId);
        var request =
                post(APPLICATIONS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body));
        if (auth != null) {
            request.header(HttpHeaders.AUTHORIZATION, auth);
        }
        return mockMvc.perform(request);
    }

    private JsonNode created(Job job, Candidate candidate) throws Exception {
        String json =
                apply(recruiterAuth, job.getId(), candidate.getId())
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return objectMapper.readTree(json);
    }

    private int countRows(String table, String column, long id) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE " + column + " = ?", Integer.class, id);
    }

    private static void assertProblem(ResultActions result, int status) throws Exception {
        result.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(status));
    }

    @Test
    void createReturnsApplicationInAppliedAtTheEndOfTheColumn() throws Exception {
        Job job = job(JobStatus.OPEN);
        Candidate first = candidate();
        Candidate second = candidate();

        JsonNode body = created(job, first);

        assertThat(body.get("id").asLong()).isPositive();
        assertThat(body.get("jobId").asLong()).isEqualTo(job.getId());
        assertThat(body.get("candidateId").asLong()).isEqualTo(first.getId());
        assertThat(body.get("stage").asText()).isEqualTo("APPLIED");
        assertThat(body.get("position").asInt()).isZero();
        assertThat(body.get("rating").isNull()).isTrue();
        assertThat(Instant.parse(body.get("createdAt").asText()))
                .isEqualTo(Instant.parse(body.get("updatedAt").asText()));
        assertThat(body.size()).isEqualTo(8);

        assertThat(created(job, second).get("position").asInt()).isEqualTo(1);
    }

    @Test
    void createRecordsInitialStageHistoryForTheAuthenticatedUser() throws Exception {
        long id = created(job(JobStatus.OPEN), candidate()).get("id").asLong();

        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList(
                        "SELECT from_stage, to_stage, moved_by, moved_at FROM stage_history"
                                + " WHERE application_id = ?",
                        id);
        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(row.get("from_stage")).isNull();
        assertThat(row.get("to_stage")).isEqualTo("APPLIED");
        assertThat(((Number) row.get("moved_by")).longValue()).isEqualTo(recruiter.getId());
        assertThat(row.get("moved_at")).isNotNull();
    }

    @Test
    void positionOnlyCountsTheAppliedColumnOfTheSameJob() throws Exception {
        Job job = job(JobStatus.OPEN);
        Job otherJob = job(JobStatus.OPEN);
        applicationRepository.saveAndFlush(new Application(job, candidate(), Stage.INTERVIEW, 0));
        applicationRepository.saveAndFlush(new Application(job, candidate(), Stage.OFFER, 5));
        applicationRepository.saveAndFlush(
                new Application(otherJob, candidate(), Stage.APPLIED, 4));

        assertThat(created(job, candidate()).get("position").asInt()).isZero();
        assertThat(created(otherJob, candidate()).get("position").asInt()).isEqualTo(5);
    }

    @Test
    void duplicateApplicationIsConflict() throws Exception {
        Job job = job(JobStatus.OPEN);
        Candidate candidate = candidate();
        created(job, candidate);

        assertProblem(apply(recruiterAuth, job.getId(), candidate.getId()), 409);
        assertThat(countRows("applications", "job_id", job.getId())).isEqualTo(1);
    }

    @Test
    void closedJobIsConflictAndCreatesNothing() throws Exception {
        Job job = job(JobStatus.CLOSED);
        Candidate candidate = candidate();

        assertProblem(apply(recruiterAuth, job.getId(), candidate.getId()), 409);
        assertThat(countRows("applications", "job_id", job.getId())).isZero();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT count(*) FROM stage_history h JOIN applications a"
                                        + " ON a.id = h.application_id WHERE a.job_id = ?",
                                Integer.class,
                                job.getId()))
                .isZero();
    }

    @Test
    void missingJobOrCandidateIsNotFound() throws Exception {
        Job job = job(JobStatus.OPEN);
        Candidate candidate = candidate();

        assertProblem(apply(recruiterAuth, Long.MAX_VALUE, candidate.getId()), 404);
        assertProblem(apply(recruiterAuth, job.getId(), Long.MAX_VALUE), 404);
    }

    @ParameterizedTest
    @ValueSource(strings = {"jobId", "candidateId"})
    void missingIdIsBadRequestWithFieldError(String field) throws Exception {
        Long jobId = "jobId".equals(field) ? null : job(JobStatus.OPEN).getId();
        Long candidateId = "candidateId".equals(field) ? null : candidate().getId();

        ResultActions result = apply(recruiterAuth, jobId, candidateId);

        assertProblem(result, 400);
        result.andExpect(jsonPath("$.errors[*].field", containsInAnyOrder(field)));
    }

    @Test
    void adminCanApplyToo() throws Exception {
        String adminAuth = testUsers.bearer(testUsers.create(Role.ADMIN));
        apply(adminAuth, job(JobStatus.OPEN).getId(), candidate().getId())
                .andExpect(status().isCreated());
    }

    @Test
    void createRequiresToken() throws Exception {
        assertProblem(apply(null, 1L, 1L), 401);
    }

    // Runs outside the test transaction so each request commits on its own connection.
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentApplicationsToTheSameJobGetDistinctContiguousPositions() throws Exception {
        int requests = 6;
        Job job = job(JobStatus.OPEN);
        List<Candidate> candidates = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            candidates.add(
                    candidateRepository.saveAndFlush(
                            new Candidate(
                                    "Concurrent " + i,
                                    "concurrent-" + UUID.randomUUID() + "@example.com",
                                    null,
                                    null)));
        }

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(requests);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (Candidate candidate : candidates) {
                Callable<Integer> task =
                        () -> {
                            start.await();
                            return apply(recruiterAuth, job.getId(), candidate.getId())
                                    .andReturn()
                                    .getResponse()
                                    .getStatus();
                        };
                results.add(executor.submit(task));
            }
            start.countDown();
            for (Future<Integer> result : results) {
                assertThat(result.get(30, TimeUnit.SECONDS)).isEqualTo(201);
            }
        } finally {
            executor.shutdownNow();
        }

        List<Integer> positions =
                jdbcTemplate.queryForList(
                        "SELECT position FROM applications WHERE job_id = ? ORDER BY position",
                        Integer.class,
                        job.getId());
        assertThat(positions).isEqualTo(IntStream.range(0, requests).boxed().toList());
    }
}
