package io.github.astvvg.ats.note;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import io.github.astvvg.ats.note.domain.Note;
import io.github.astvvg.ats.note.repository.NoteRepository;
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
class NoteIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TestUsers testUsers;
    @Autowired private JobRepository jobRepository;
    @Autowired private CandidateRepository candidateRepository;
    @Autowired private ApplicationRepository applicationRepository;
    @Autowired private NoteRepository noteRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private User recruiter;
    private User otherRecruiter;
    private User admin;
    private Application application;

    @BeforeEach
    void setUp() {
        recruiter = testUsers.create(Role.RECRUITER);
        otherRecruiter = testUsers.create(Role.RECRUITER);
        admin = testUsers.create(Role.ADMIN);
        application = application(JobStatus.OPEN);
    }

    // ---- helpers ----

    private Application application(JobStatus status) {
        Job job = new Job("Notes Job", null, null, null, recruiter);
        job.setStatus(status);
        jobRepository.saveAndFlush(job);
        Candidate candidate =
                candidateRepository.saveAndFlush(
                        new Candidate("Cand", TestUsers.uniqueEmail("note"), null, null));
        return applicationRepository.saveAndFlush(
                new Application(job, candidate, Stage.APPLIED, 0));
    }

    private User user(Role role) {
        return role == Role.ADMIN ? admin : recruiter;
    }

    private Note note(Application target, User author, String text) {
        return noteRepository.saveAndFlush(new Note(target, author, text));
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

    private ResultActions create(Object applicationId, Object body, User user) throws Exception {
        String json = body instanceof String s ? s : objectMapper.writeValueAsString(body);
        return perform(
                post("/api/applications/" + applicationId + "/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json),
                user);
    }

    private ResultActions list(Object applicationId, String query, User user) throws Exception {
        return perform(
                get("/api/applications/" + applicationId + "/notes" + (query == null ? "" : query)),
                user);
    }

    private ResultActions remove(Object noteId, User user) throws Exception {
        return perform(delete("/api/notes/" + noteId), user);
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static void assertProblem(ResultActions result, int status) throws Exception {
        result.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(status));
    }

    private static void assertContentError(ResultActions result) throws Exception {
        assertProblem(result, 400);
        result.andExpect(jsonPath("$.errors[0].field").value("content"));
    }

    private void setCreatedAt(Note note, Instant createdAt) {
        jdbcTemplate.update(
                "UPDATE notes SET created_at = ? WHERE id = ?",
                Timestamp.from(createdAt),
                note.getId());
    }

    // The request shares the test transaction, so its pending delete is flushed before checking.
    private boolean exists(Note note) {
        entityManager.flush();
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM notes WHERE id = ?", Integer.class, note.getId());
        return count != null && count == 1;
    }

    private int notesOf(Application target) {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM notes WHERE application_id = ?",
                        Integer.class,
                        target.getId());
        return count == null ? 0 : count;
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

    // ---- create ----

    @ParameterizedTest
    @EnumSource(Role.class)
    void createReturns201WithTheTokenUserAsAuthor(Role role) throws Exception {
        User author = user(role);

        JsonNode body =
                json(
                        create(
                                        application.getId(),
                                        Map.of("content", "  Good fit for the team  "),
                                        author)
                                .andExpect(status().isCreated()));

        assertThat(fieldNames(body))
                .containsExactlyInAnyOrder("id", "applicationId", "content", "author", "createdAt");
        assertThat(fieldNames(body.get("author"))).containsExactlyInAnyOrder("id", "name");
        assertThat(body.get("applicationId").asLong()).isEqualTo(application.getId());
        assertThat(body.get("content").asText()).isEqualTo("Good fit for the team");
        assertThat(body.get("author").get("id").asLong()).isEqualTo(author.getId());
        assertThat(body.get("author").get("name").asText()).isEqualTo(author.getName());
        assertThat(body.get("createdAt").isNull()).isFalse();
        Map<String, Object> row =
                jdbcTemplate.queryForMap(
                        "SELECT application_id, author_id, content FROM notes WHERE id = ?",
                        body.get("id").asLong());
        assertThat(row.get("application_id")).isEqualTo(application.getId());
        assertThat(row.get("author_id")).isEqualTo(author.getId());
        assertThat(row.get("content")).isEqualTo("Good fit for the team");
    }

    @Test
    void authorIdInTheBodyIsIgnored() throws Exception {
        JsonNode body =
                json(
                        create(
                                        application.getId(),
                                        Map.of("content", "Mine", "authorId", admin.getId()),
                                        recruiter)
                                .andExpect(status().isCreated()));

        assertThat(body.get("author").get("id").asLong()).isEqualTo(recruiter.getId());
    }

    @Test
    void createIsAllowedOnAClosedJob() throws Exception {
        Application closed = application(JobStatus.CLOSED);

        create(closed.getId(), Map.of("content", "Archived feedback"), recruiter)
                .andExpect(status().isCreated());
        list(closed.getId(), null, recruiter)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   \t\n  "})
    void blankContentIsRejected(String text) throws Exception {
        assertContentError(create(application.getId(), Map.of("content", text), recruiter));
        assertThat(notesOf(application)).isZero();
    }

    @Test
    void missingOrNullContentIsRejected() throws Exception {
        assertContentError(create(application.getId(), "{}", recruiter));
        assertContentError(create(application.getId(), "{\"content\":null}", recruiter));
    }

    @Test
    void contentOverTheLimitIsRejected() throws Exception {
        String text = "x".repeat(Note.MAX_CONTENT_LENGTH + 1);

        assertContentError(create(application.getId(), Map.of("content", text), recruiter));
    }

    @Test
    void limitAppliesToTheTrimmedContent() throws Exception {
        String text = "x".repeat(Note.MAX_CONTENT_LENGTH);

        JsonNode body =
                json(
                        create(
                                        application.getId(),
                                        Map.of("content", "  " + text + "  "),
                                        recruiter)
                                .andExpect(status().isCreated()));

        assertThat(body.get("content").asText()).isEqualTo(text);
    }

    @Test
    void createOnAMissingApplicationIs404() throws Exception {
        assertProblem(create(Long.MAX_VALUE, Map.of("content", "x"), recruiter), 404);
    }

    // ---- list ----

    @ParameterizedTest
    @EnumSource(Role.class)
    void listIsNewestFirstByDefaultAndOnlyForThatApplication(Role role) throws Exception {
        Note first = note(application, recruiter, "first");
        Note second = note(application, admin, "second");
        Note third = note(application, otherRecruiter, "third");
        note(application(JobStatus.OPEN), recruiter, "elsewhere");
        Instant base = Instant.parse("2026-01-01T10:00:00Z");
        setCreatedAt(first, base);
        setCreatedAt(second, base.plusSeconds(60));
        setCreatedAt(third, base.plusSeconds(120));

        JsonNode page =
                json(list(application.getId(), null, user(role)).andExpect(status().isOk()));

        assertThat(ids(page)).containsExactly(third.getId(), second.getId(), first.getId());
        assertThat(page.get("page").asInt()).isZero();
        assertThat(page.get("size").asInt()).isEqualTo(20);
        assertThat(page.get("totalElements").asLong()).isEqualTo(3);
        assertThat(page.get("totalPages").asInt()).isEqualTo(1);
        JsonNode newest = page.get("content").get(0);
        assertThat(newest.get("author").get("id").asLong()).isEqualTo(otherRecruiter.getId());
        assertThat(fieldNames(newest.get("author"))).containsExactlyInAnyOrder("id", "name");
    }

    @Test
    void tiesOnCreatedAtAreBrokenByIdInTheSameDirection() throws Exception {
        Instant same = Instant.parse("2026-02-01T09:00:00Z");
        List<Long> created = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Note note = note(application, i % 2 == 0 ? recruiter : admin, "n" + i);
            setCreatedAt(note, same);
            created.add(note.getId());
        }
        List<Long> descending = created.reversed();

        JsonNode page0 = json(list(application.getId(), "?size=2", recruiter));
        JsonNode page1 = json(list(application.getId(), "?size=2&page=1", recruiter));
        JsonNode page2 = json(list(application.getId(), "?size=2&page=2", recruiter));
        assertThat(ids(page0)).containsExactlyElementsOf(descending.subList(0, 2));
        assertThat(ids(page1)).containsExactlyElementsOf(descending.subList(2, 4));
        assertThat(ids(page2)).containsExactlyElementsOf(descending.subList(4, 5));
        assertThat(page0.get("totalElements").asLong()).isEqualTo(5);
        assertThat(page0.get("totalPages").asInt()).isEqualTo(3);

        JsonNode ascending =
                json(list(application.getId(), "?sort=createdAt,asc&size=3", recruiter));
        assertThat(ids(ascending)).containsExactlyElementsOf(created.subList(0, 3));
    }

    @Test
    void sizeIsCappedAt100() throws Exception {
        list(application.getId(), "?size=500", recruiter)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "content", "author", "author.name", "movedAt"})
    void unsupportedSortIs400(String property) throws Exception {
        assertProblem(list(application.getId(), "?sort=" + property, recruiter), 400);
    }

    @Test
    void listOnAMissingApplicationIs404() throws Exception {
        assertProblem(list(Long.MAX_VALUE, null, recruiter), 404);
    }

    @Test
    void listStatementsDoNotGrowWithNotesOrAuthors() throws Exception {
        Application small = application(JobStatus.OPEN);
        note(small, recruiter, "only");
        Application large = application(JobStatus.OPEN);
        List<User> authors = new ArrayList<>(List.of(recruiter, otherRecruiter, admin));
        authors.add(testUsers.create(Role.RECRUITER));
        authors.add(testUsers.create(Role.ADMIN));
        for (int i = 0; i < 15; i++) {
            note(large, authors.get(i % authors.size()), "n" + i);
        }

        long withOne = statementsToList(small, 1, 1);
        long withFifteen = statementsToList(large, 15, authors.size());

        assertThat(withOne).isPositive();
        assertThat(withFifteen).isEqualTo(withOne);
    }

    private long statementsToList(Application target, int expectedNotes, int expectedAuthors)
            throws Exception {
        entityManager.flush();
        entityManager.clear();
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        JsonNode page = json(list(target.getId(), null, recruiter).andExpect(status().isOk()));

        long count = statistics.getPrepareStatementCount();
        Set<Long> authors = new HashSet<>();
        page.get("content").forEach(n -> authors.add(n.get("author").get("id").asLong()));
        assertThat(page.get("content").size()).isEqualTo(expectedNotes);
        assertThat(authors).hasSize(expectedAuthors);
        return count;
    }

    // ---- delete ----

    @ParameterizedTest
    @EnumSource(Role.class)
    void authorDeletesTheirOwnNote(Role role) throws Exception {
        Note note = note(application, user(role), "mine");

        remove(note.getId(), user(role))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(exists(note)).isFalse();
    }

    @Test
    void adminDeletesSomeoneElsesNote() throws Exception {
        Note note = note(application, recruiter, "recruiter's");

        remove(note.getId(), admin).andExpect(status().isNoContent());

        assertThat(exists(note)).isFalse();
    }

    @Test
    void recruiterCannotDeleteSomeoneElsesNote() throws Exception {
        Note byRecruiter = note(application, recruiter, "recruiter's");
        Note byAdmin = note(application, admin, "admin's");

        ResultActions result = remove(byRecruiter.getId(), otherRecruiter);
        assertProblem(result, 403);
        result.andExpect(jsonPath("$.detail").value("Access denied"));
        assertProblem(remove(byAdmin.getId(), recruiter), 403);

        assertThat(exists(byRecruiter)).isTrue();
        assertThat(exists(byAdmin)).isTrue();
    }

    @Test
    void deleteOfAMissingNoteIs404BeforeTheAuthorCheck() throws Exception {
        assertProblem(remove(Long.MAX_VALUE, recruiter), 404);
        assertProblem(remove(Long.MAX_VALUE, admin), 404);
    }

    @Test
    void deleteIsAllowedOnAClosedJob() throws Exception {
        Application closed = application(JobStatus.CLOSED);
        Note note = note(closed, recruiter, "archived");

        remove(note.getId(), recruiter).andExpect(status().isNoContent());

        assertThat(exists(note)).isFalse();
    }

    // ---- authentication ----

    @Test
    void everyNoteEndpointRequiresToken() throws Exception {
        Note note = note(application, recruiter, "kept");

        assertProblem(create(application.getId(), Map.of("content", "x"), null), 401);
        assertProblem(list(application.getId(), null, null), 401);
        assertProblem(remove(note.getId(), null), 401);

        assertThat(exists(note)).isTrue();
        assertThat(notesOf(application)).isEqualTo(1);
    }
}
