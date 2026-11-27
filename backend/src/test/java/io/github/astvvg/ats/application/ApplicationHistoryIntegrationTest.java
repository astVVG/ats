package io.github.astvvg.ats.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.astvvg.ats.application.domain.Application;
import io.github.astvvg.ats.application.domain.Stage;
import io.github.astvvg.ats.application.domain.StageHistory;
import io.github.astvvg.ats.application.repository.ApplicationRepository;
import io.github.astvvg.ats.application.repository.StageHistoryRepository;
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
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TestUsers.class})
@Transactional
class ApplicationHistoryIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TestUsers testUsers;
    @Autowired private JobRepository jobRepository;
    @Autowired private CandidateRepository candidateRepository;
    @Autowired private ApplicationRepository applicationRepository;
    @Autowired private StageHistoryRepository stageHistoryRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private User recruiter;
    private User admin;
    private Job job;

    @BeforeEach
    void setUp() {
        recruiter = testUsers.create(Role.RECRUITER);
        admin = testUsers.create(Role.ADMIN);
        job = job(JobStatus.OPEN);
    }

    // ---- helpers ----

    private Job job(JobStatus status) {
        Job created = new Job("History Job", null, null, null, recruiter);
        created.setStatus(status);
        return jobRepository.saveAndFlush(created);
    }

    private Candidate candidate() {
        return candidateRepository.saveAndFlush(
                new Candidate("Cand", TestUsers.uniqueEmail("history"), null, null));
    }

    private User user(Role role) {
        return role == Role.ADMIN ? admin : recruiter;
    }

    // Each request starts with a fresh persistence context, as in production.
    private ResultActions perform(MockHttpServletRequestBuilder request, User user)
            throws Exception {
        entityManager.flush();
        entityManager.clear();
        if (user != null) {
            request.header(HttpHeaders.AUTHORIZATION, testUsers.bearer(user));
        }
        return mockMvc.perform(request);
    }

    /** Applies through the API, so the initial history entry is recorded as in production. */
    private long apply(Job target, User user) throws Exception {
        String body =
                objectMapper.writeValueAsString(
                        Map.of("jobId", target.getId(), "candidateId", candidate().getId()));
        return json(perform(
                                post("/api/applications")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body),
                                user)
                        .andExpect(status().isCreated()))
                .get("id")
                .asLong();
    }

    private void move(long id, Stage stage, int position, User user) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("stage", stage, "position", position));
        perform(
                        patch("/api/applications/" + id + "/move")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body),
                        user)
                .andExpect(status().isOk());
    }

    private ResultActions history(Object id, String query, User user) throws Exception {
        return perform(
                get("/api/applications/" + id + "/history" + (query == null ? "" : query)), user);
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static void assertProblem(ResultActions result, int status) throws Exception {
        result.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(status));
    }

    private static List<Long> ids(JsonNode page) {
        List<Long> ids = new ArrayList<>();
        page.get("content").forEach(n -> ids.add(n.get("id").asLong()));
        return ids;
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private Application application(Job target) {
        return applicationRepository.saveAndFlush(
                new Application(target, candidate(), Stage.APPLIED, 0));
    }

    private StageHistory entry(Application application, Stage from, Stage to, User user) {
        return stageHistoryRepository.saveAndFlush(new StageHistory(application, from, to, user));
    }

    private void setMovedAt(StageHistory entry, Instant movedAt) {
        jdbcTemplate.update(
                "UPDATE stage_history SET moved_at = ? WHERE id = ?",
                Timestamp.from(movedAt),
                entry.getId());
    }

    // ---- tests ----

    @ParameterizedTest
    @EnumSource(Role.class)
    void historyListsEveryStageChangeNewestFirstWithWhoMovedIt(Role role) throws Exception {
        long id = apply(job, recruiter);
        move(id, Stage.INTERVIEW, 0, admin);
        move(id, Stage.OFFER, 0, recruiter);

        JsonNode page = json(history(id, null, user(role)).andExpect(status().isOk()));

        assertThat(page.get("totalElements").asLong()).isEqualTo(3);
        assertThat(page.get("size").asInt()).isEqualTo(20);
        JsonNode entries = page.get("content");
        for (JsonNode entry : entries) {
            assertThat(fieldNames(entry))
                    .containsExactlyInAnyOrder("id", "fromStage", "toStage", "movedBy", "movedAt");
            assertThat(fieldNames(entry.get("movedBy"))).containsExactlyInAnyOrder("id", "name");
            assertThat(entry.get("movedAt").isTextual()).isTrue();
        }
        assertThat(entries.get(0).get("fromStage").asText()).isEqualTo("INTERVIEW");
        assertThat(entries.get(0).get("toStage").asText()).isEqualTo("OFFER");
        assertThat(entries.get(0).get("movedBy").get("id").asLong()).isEqualTo(recruiter.getId());
        assertThat(entries.get(0).get("movedBy").get("name").asText())
                .isEqualTo(recruiter.getName());
        assertThat(entries.get(1).get("fromStage").asText()).isEqualTo("APPLIED");
        assertThat(entries.get(1).get("toStage").asText()).isEqualTo("INTERVIEW");
        assertThat(entries.get(1).get("movedBy").get("id").asLong()).isEqualTo(admin.getId());
        JsonNode initial = entries.get(2);
        assertThat(initial.has("fromStage")).isTrue();
        assertThat(initial.get("fromStage").isNull()).isTrue();
        assertThat(initial.get("toStage").asText()).isEqualTo("APPLIED");
        assertThat(initial.get("movedBy").get("id").asLong()).isEqualTo(recruiter.getId());
        List<Instant> movedAts = new ArrayList<>();
        entries.forEach(e -> movedAts.add(Instant.parse(e.get("movedAt").asText())));
        assertThat(movedAts).isSortedAccordingTo((a, b) -> b.compareTo(a));
    }

    @Test
    void reorderingInsideTheSameStageAddsNoHistory() throws Exception {
        apply(job, recruiter);
        long second = apply(job, recruiter);

        move(second, Stage.APPLIED, 0, recruiter);

        JsonNode page = json(history(second, null, recruiter).andExpect(status().isOk()));
        assertThat(page.get("totalElements").asLong()).isEqualTo(1);
        assertThat(page.get("content").get(0).get("fromStage").isNull()).isTrue();
    }

    @Test
    void tiesOnMovedAtAreBrokenByIdInTheSameDirection() throws Exception {
        Application application = application(job);
        Instant same = Instant.parse("2026-03-01T12:00:00Z");
        List<Long> created = new ArrayList<>();
        Stage[] stages = Stage.values();
        for (int i = 0; i < 5; i++) {
            StageHistory entry =
                    entry(
                            application,
                            i == 0 ? null : stages[(i - 1) % stages.length],
                            stages[i % stages.length],
                            i % 2 == 0 ? recruiter : admin);
            setMovedAt(entry, same);
            created.add(entry.getId());
        }
        List<Long> descending = created.reversed();

        JsonNode page0 = json(history(application.getId(), "?size=2", recruiter));
        JsonNode page1 = json(history(application.getId(), "?size=2&page=1", recruiter));
        JsonNode page2 = json(history(application.getId(), "?size=2&page=2", recruiter));
        assertThat(ids(page0)).containsExactlyElementsOf(descending.subList(0, 2));
        assertThat(ids(page1)).containsExactlyElementsOf(descending.subList(2, 4));
        assertThat(ids(page2)).containsExactlyElementsOf(descending.subList(4, 5));
        assertThat(page0.get("totalPages").asInt()).isEqualTo(3);

        JsonNode ascending =
                json(history(application.getId(), "?sort=movedAt,asc&size=3", recruiter));
        assertThat(ids(ascending)).containsExactlyElementsOf(created.subList(0, 3));
    }

    @Test
    void explicitSortByMovedAtAscendingIsOldestFirst() throws Exception {
        Application application = application(job);
        StageHistory older = entry(application, null, Stage.APPLIED, recruiter);
        StageHistory newer = entry(application, Stage.APPLIED, Stage.HIRED, admin);
        setMovedAt(older, Instant.parse("2026-01-01T00:00:00Z"));
        setMovedAt(newer, Instant.parse("2026-01-02T00:00:00Z"));

        assertThat(ids(json(history(application.getId(), "?sort=movedAt,asc", recruiter))))
                .containsExactly(older.getId(), newer.getId());
        assertThat(ids(json(history(application.getId(), "?sort=movedAt,desc", recruiter))))
                .containsExactly(newer.getId(), older.getId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "createdAt", "toStage", "movedBy.name"})
    void unsupportedSortIs400(String property) throws Exception {
        assertProblem(history(application(job).getId(), "?sort=" + property, recruiter), 400);
    }

    @Test
    void historyIsAvailableOnAClosedJob() throws Exception {
        long id = apply(job, recruiter);
        job.setStatus(JobStatus.CLOSED);
        jobRepository.saveAndFlush(job);

        history(id, null, admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void historyOfAMissingApplicationIs404() throws Exception {
        assertProblem(history(Long.MAX_VALUE, null, recruiter), 404);
    }

    @Test
    void historyRequiresToken() throws Exception {
        assertProblem(history(application(job).getId(), null, null), 401);
    }

    @Test
    void statementsDoNotGrowWithEntriesOrMovers() throws Exception {
        Application small = application(job);
        entry(small, null, Stage.APPLIED, recruiter);
        Application large = application(job(JobStatus.OPEN));
        List<User> movers =
                List.of(
                        recruiter,
                        admin,
                        testUsers.create(Role.RECRUITER),
                        testUsers.create(Role.ADMIN));
        Stage[] stages = Stage.values();
        for (int i = 0; i < 12; i++) {
            entry(
                    large,
                    stages[i % stages.length],
                    stages[(i + 1) % stages.length],
                    movers.get(i % movers.size()));
        }

        long withOne = statementsToList(small, 1, 1);
        long withTwelve = statementsToList(large, 12, movers.size());

        assertThat(withOne).isPositive();
        assertThat(withTwelve).isEqualTo(withOne);
    }

    private long statementsToList(Application target, int expectedEntries, int expectedMovers)
            throws Exception {
        entityManager.flush();
        entityManager.clear();
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        JsonNode page = json(history(target.getId(), null, recruiter).andExpect(status().isOk()));

        long count = statistics.getPrepareStatementCount();
        Set<Long> movers = new HashSet<>();
        page.get("content").forEach(e -> movers.add(e.get("movedBy").get("id").asLong()));
        assertThat(page.get("content").size()).isEqualTo(expectedEntries);
        assertThat(movers).hasSize(expectedMovers);
        return count;
    }
}
