package io.github.astvvg.ats.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TestUsers.class})
@Transactional
class JobBoardIntegrationTest {

    private static final List<String> STAGES =
            List.of("APPLIED", "INTERVIEW", "OFFER", "HIRED", "REJECTED");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TestUsers testUsers;
    @Autowired private JobRepository jobRepository;
    @Autowired private CandidateRepository candidateRepository;
    @Autowired private ApplicationRepository applicationRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private User recruiter;
    private String recruiterAuth;

    @BeforeEach
    void setUp() {
        recruiter = testUsers.create(Role.RECRUITER);
        recruiterAuth = testUsers.bearer(recruiter);
    }

    private Job job(JobStatus status) {
        Job job = new Job("Board Job", null, null, null, recruiter);
        job.setStatus(status);
        return jobRepository.saveAndFlush(job);
    }

    private Candidate candidate(String name) {
        return candidateRepository.saveAndFlush(
                new Candidate(name, TestUsers.uniqueEmail("board"), null, null));
    }

    private Application application(Job job, Stage stage, int position) {
        return applicationRepository.saveAndFlush(
                new Application(job, candidate("Cand " + stage + position), stage, position));
    }

    private ResultActions board(Object jobId, String auth) throws Exception {
        var request = get("/api/jobs/" + jobId + "/board");
        if (auth != null) {
            request.header(HttpHeaders.AUTHORIZATION, auth);
        }
        return mockMvc.perform(request);
    }

    // Detaches everything so the request has to load candidates from the database.
    private JsonNode boardJson(Job job) throws Exception {
        entityManager.flush();
        entityManager.clear();
        String json =
                board(job.getId(), recruiterAuth)
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return objectMapper.readTree(json);
    }

    private static List<String> stages(JsonNode body) {
        List<String> stages = new ArrayList<>();
        body.get("columns").forEach(c -> stages.add(c.get("stage").asText()));
        return stages;
    }

    private static List<Long> cardIds(JsonNode body, int column) {
        List<Long> ids = new ArrayList<>();
        body.get("columns")
                .get(column)
                .get("applications")
                .forEach(a -> ids.add(a.get("id").asLong()));
        return ids;
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static void assertProblem(ResultActions result, int status) throws Exception {
        result.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(status));
    }

    @Test
    void emptyJobHasFiveEmptyColumnsInStageOrder() throws Exception {
        Job job = job(JobStatus.OPEN);

        JsonNode body = boardJson(job);

        assertThat(fieldNames(body)).containsExactlyInAnyOrder("job", "columns");
        assertThat(body.get("job").get("id").asLong()).isEqualTo(job.getId());
        assertThat(body.get("job").get("title").asText()).isEqualTo("Board Job");
        assertThat(body.get("job").get("status").asText()).isEqualTo("OPEN");
        assertThat(fieldNames(body.get("job"))).containsExactlyInAnyOrder("id", "title", "status");
        assertThat(stages(body)).isEqualTo(STAGES);
        body.get("columns")
                .forEach(column -> {
                    assertThat(fieldNames(column))
                            .containsExactlyInAnyOrder("stage", "applications");
                    assertThat(column.get("applications").isArray()).isTrue();
                    assertThat(column.get("applications")).isEmpty();
                });
    }

    @Test
    void cardsAreGroupedByStageAndOrderedByPosition() throws Exception {
        Job job = job(JobStatus.OPEN);
        Application applied2 = application(job, Stage.APPLIED, 2);
        Application applied0 = application(job, Stage.APPLIED, 0);
        Application applied1 = application(job, Stage.APPLIED, 1);
        Application interview1 = application(job, Stage.INTERVIEW, 1);
        Application interview0 = application(job, Stage.INTERVIEW, 0);
        Application rejected = application(job, Stage.REJECTED, 0);
        application(job(JobStatus.OPEN), Stage.APPLIED, 0);

        JsonNode body = boardJson(job);

        assertThat(stages(body)).isEqualTo(STAGES);
        assertThat(cardIds(body, 0))
                .containsExactly(applied0.getId(), applied1.getId(), applied2.getId());
        assertThat(cardIds(body, 1)).containsExactly(interview0.getId(), interview1.getId());
        assertThat(cardIds(body, 2)).isEmpty();
        assertThat(cardIds(body, 3)).isEmpty();
        assertThat(cardIds(body, 4)).containsExactly(rejected.getId());
    }

    @Test
    void cardHasCandidateSummaryAndNoEntityInternals() throws Exception {
        Job job = job(JobStatus.OPEN);
        Application application = application(job, Stage.OFFER, 0);
        jdbcTemplate.update("UPDATE applications SET rating = 4 WHERE id = ?", application.getId());
        jdbcTemplate.update(
                "UPDATE candidates SET resume_path = 'resumes/secret.pdf' WHERE id = ?",
                application.getCandidate().getId());

        JsonNode body = boardJson(job);
        JsonNode card = body.get("columns").get(2).get("applications").get(0);

        assertThat(fieldNames(card))
                .containsExactlyInAnyOrder("id", "position", "rating", "candidate", "updatedAt");
        assertThat(card.get("id").asLong()).isEqualTo(application.getId());
        assertThat(card.get("position").asInt()).isZero();
        assertThat(card.get("rating").asInt()).isEqualTo(4);
        assertThat(card.get("updatedAt").isTextual()).isTrue();
        JsonNode candidate = card.get("candidate");
        assertThat(fieldNames(candidate)).containsExactlyInAnyOrder("id", "fullName", "email");
        assertThat(candidate.get("id").asLong()).isEqualTo(application.getCandidate().getId());
        assertThat(candidate.get("fullName").asText()).isEqualTo("Cand OFFER0");
        assertThat(candidate.get("email").asText())
                .isEqualTo(application.getCandidate().getEmail());
        assertThat(body.toString())
                .doesNotContainIgnoringCase("resumePath")
                .doesNotContain("resumes/");
    }

    @Test
    void unratedCardHasNullRating() throws Exception {
        Job job = job(JobStatus.OPEN);
        application(job, Stage.APPLIED, 0);

        JsonNode card = boardJson(job).get("columns").get(0).get("applications").get(0);

        assertThat(card.get("rating").isNull()).isTrue();
    }

    @Test
    void closedJobBoardIsAvailable() throws Exception {
        Job job = job(JobStatus.CLOSED);
        Application hired = application(job, Stage.HIRED, 0);

        JsonNode body = boardJson(job);

        assertThat(body.get("job").get("status").asText()).isEqualTo("CLOSED");
        assertThat(cardIds(body, 3)).containsExactly(hired.getId());
    }

    @Test
    void adminCanReadTheBoard() throws Exception {
        String adminAuth = testUsers.bearer(testUsers.create(Role.ADMIN));
        board(job(JobStatus.OPEN).getId(), adminAuth).andExpect(status().isOk());
    }

    @Test
    void missingJobIsNotFound() throws Exception {
        assertProblem(board(Long.MAX_VALUE, recruiterAuth), 404);
    }

    @Test
    void boardRequiresToken() throws Exception {
        assertProblem(board(job(JobStatus.OPEN).getId(), null), 401);
    }

    @Test
    void statementCountDoesNotGrowWithApplications() throws Exception {
        Job small = job(JobStatus.OPEN);
        application(small, Stage.APPLIED, 0);
        Job large = job(JobStatus.OPEN);
        Stage[] stages = Stage.values();
        for (int i = 0; i < 20; i++) {
            application(large, stages[i % stages.length], i);
        }

        long withOne = statementsToLoad(small, 1);
        long withTwenty = statementsToLoad(large, 20);

        assertThat(withOne).isPositive();
        assertThat(withTwenty).isEqualTo(withOne);
    }

    private long statementsToLoad(Job job, int expectedCards) throws Exception {
        entityManager.flush();
        entityManager.clear();
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        JsonNode body = boardJson(job);

        long count = statistics.getPrepareStatementCount();
        int cards = 0;
        for (JsonNode column : body.get("columns")) {
            cards += column.get("applications").size();
        }
        assertThat(cards).isEqualTo(expectedCards);
        return count;
    }
}
