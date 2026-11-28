package io.github.astvvg.ats.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.astvvg.ats.application.domain.Application;
import io.github.astvvg.ats.application.domain.Stage;
import io.github.astvvg.ats.application.domain.StageHistory;
import io.github.astvvg.ats.application.repository.ApplicationRepository;
import io.github.astvvg.ats.application.repository.StageHistoryRepository;
import io.github.astvvg.ats.candidate.domain.Candidate;
import io.github.astvvg.ats.candidate.repository.CandidateRepository;
import io.github.astvvg.ats.job.domain.Job;
import io.github.astvvg.ats.job.repository.JobRepository;
import io.github.astvvg.ats.note.domain.Note;
import io.github.astvvg.ats.note.repository.NoteRepository;
import io.github.astvvg.ats.support.TestUsers;
import io.github.astvvg.ats.support.TestcontainersConfiguration;
import io.github.astvvg.ats.user.domain.Role;
import io.github.astvvg.ats.user.domain.User;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * The permission matrix of the API. A new endpoint under /api fails {@link
 * #everyApiMappingIsClassified()} until it is added to {@link Endpoint}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TestUsers.class})
@Transactional
class AuthorizationMatrixIntegrationTest {

    enum Access {
        PUBLIC,
        ADMIN_ONLY,
        ANY_ROLE
    }

    /** Every endpoint with a valid request body and the status an allowed caller gets. */
    enum Endpoint {
        LOGIN("POST", "/api/auth/login", Access.PUBLIC, 200),
        ME("GET", "/api/me", Access.ANY_ROLE, 200),
        LIST_USERS("GET", "/api/users", Access.ADMIN_ONLY, 200),
        CREATE_USER("POST", "/api/users", Access.ADMIN_ONLY, 201),
        UPDATE_USER("PATCH", "/api/users/{id}", Access.ADMIN_ONLY, 200),
        LIST_JOBS("GET", "/api/jobs", Access.ANY_ROLE, 200),
        CREATE_JOB("POST", "/api/jobs", Access.ANY_ROLE, 201),
        GET_JOB("GET", "/api/jobs/{id}", Access.ANY_ROLE, 200),
        UPDATE_JOB("PUT", "/api/jobs/{id}", Access.ANY_ROLE, 200),
        UPDATE_JOB_STATUS("PATCH", "/api/jobs/{id}/status", Access.ANY_ROLE, 200),
        JOB_BOARD("GET", "/api/jobs/{id}/board", Access.ANY_ROLE, 200),
        LIST_CANDIDATES("GET", "/api/candidates", Access.ANY_ROLE, 200),
        CREATE_CANDIDATE("POST", "/api/candidates", Access.ANY_ROLE, 201),
        GET_CANDIDATE("GET", "/api/candidates/{id}", Access.ANY_ROLE, 200),
        UPDATE_CANDIDATE("PUT", "/api/candidates/{id}", Access.ANY_ROLE, 200),
        DELETE_CANDIDATE("DELETE", "/api/candidates/{id}", Access.ANY_ROLE, 204),
        UPLOAD_RESUME("POST", "/api/candidates/{id}/resume", Access.ANY_ROLE, 200),
        DOWNLOAD_RESUME("GET", "/api/candidates/{id}/resume", Access.ANY_ROLE, 200),
        CREATE_APPLICATION("POST", "/api/applications", Access.ANY_ROLE, 201),
        MOVE_APPLICATION("PATCH", "/api/applications/{id}/move", Access.ANY_ROLE, 200),
        RATE_APPLICATION("PATCH", "/api/applications/{id}/rating", Access.ANY_ROLE, 200),
        APPLICATION_HISTORY("GET", "/api/applications/{id}/history", Access.ANY_ROLE, 200),
        LIST_NOTES("GET", "/api/applications/{id}/notes", Access.ANY_ROLE, 200),
        CREATE_NOTE("POST", "/api/applications/{id}/notes", Access.ANY_ROLE, 201),
        DELETE_NOTE("DELETE", "/api/notes/{id}", Access.ANY_ROLE, 204),
        FUNNEL("GET", "/api/metrics/funnel", Access.ANY_ROLE, 200),
        TIME_IN_STAGE("GET", "/api/metrics/time-in-stage", Access.ANY_ROLE, 200),
        TIME_TO_HIRE("GET", "/api/metrics/time-to-hire", Access.ANY_ROLE, 200);

        private final String method;
        private final String pattern;
        private final Access access;
        private final int success;

        Endpoint(String method, String pattern, Access access, int success) {
            this.method = method;
            this.pattern = pattern;
            this.access = access;
            this.success = success;
        }
    }

    private static final byte[] PDF = "%PDF-1.4\nresume\n%%EOF".getBytes(StandardCharsets.US_ASCII);

    @TempDir static Path uploads;

    @DynamicPropertySource
    static void uploadsDir(DynamicPropertyRegistry registry) {
        registry.add("app.uploads.dir", uploads::toString);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private TestUsers testUsers;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;
    @Autowired private JobRepository jobRepository;
    @Autowired private CandidateRepository candidateRepository;
    @Autowired private ApplicationRepository applicationRepository;
    @Autowired private StageHistoryRepository stageHistoryRepository;
    @Autowired private NoteRepository noteRepository;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    private User admin;
    private User recruiter;
    private User target;
    private Job job;
    private Candidate applicant;
    private Candidate unlinked;
    private Application application;

    @BeforeEach
    void setUp() {
        admin = testUsers.create(Role.ADMIN);
        recruiter = testUsers.create(Role.RECRUITER);
        target = testUsers.create(Role.RECRUITER);
        job = jobRepository.save(new Job("Matrix job", null, null, null, admin));
        applicant = candidate("Applicant");
        unlinked = candidate("Unlinked");
        application = applicationRepository.save(new Application(job, applicant, Stage.APPLIED, 0));
        stageHistoryRepository.save(new StageHistory(application, null, Stage.APPLIED, admin));
        entityManager.flush();
    }

    private Candidate candidate(String name) {
        return candidateRepository.save(
                new Candidate(name, TestUsers.uniqueEmail("matrix"), null, null));
    }

    private static Stream<Endpoint> protectedEndpoints() {
        return Stream.of(Endpoint.values()).filter(e -> e.access != Access.PUBLIC);
    }

    private static Stream<Endpoint> adminEndpoints() {
        return Stream.of(Endpoint.values()).filter(e -> e.access == Access.ADMIN_ONLY);
    }

    private static Stream<Endpoint> sharedEndpoints() {
        return Stream.of(Endpoint.values()).filter(e -> e.access == Access.ANY_ROLE);
    }

    /** A request that succeeds for an allowed user; {@code actor} authors the note to delete. */
    private MockHttpServletRequestBuilder valid(Endpoint endpoint, User actor) throws Exception {
        Long id =
                switch (endpoint) {
                    case UPDATE_USER -> target.getId();
                    case GET_JOB, UPDATE_JOB, UPDATE_JOB_STATUS, JOB_BOARD -> job.getId();
                    case GET_CANDIDATE, UPDATE_CANDIDATE, DELETE_CANDIDATE, UPLOAD_RESUME ->
                            unlinked.getId();
                    case DOWNLOAD_RESUME -> candidateWithResume();
                    case DELETE_NOTE -> note(actor).getId();
                    default -> application.getId();
                };
        String path = endpoint.pattern.replace("{id}", String.valueOf(id));
        if (endpoint == Endpoint.UPLOAD_RESUME) {
            return multipart(path)
                    .file(new MockMultipartFile("file", "cv.pdf", "application/pdf", PDF));
        }
        MockHttpServletRequestBuilder builder = request(HttpMethod.valueOf(endpoint.method), path);
        String body =
                switch (endpoint) {
                    case LOGIN ->
                            "{\"email\":\"%s\",\"password\":\"%s\"}"
                                    .formatted(actor.getEmail(), TestUsers.PASSWORD);
                    case CREATE_USER ->
                            "{\"name\":\"New\",\"email\":\"%s\",\"password\":\"%s\",\"role\":\"RECRUITER\"}"
                                    .formatted(TestUsers.uniqueEmail("new"), TestUsers.PASSWORD);
                    case UPDATE_USER -> "{\"active\":false}";
                    case CREATE_JOB, UPDATE_JOB -> "{\"title\":\"Changed\"}";
                    case UPDATE_JOB_STATUS -> "{\"status\":\"CLOSED\"}";
                    case CREATE_CANDIDATE, UPDATE_CANDIDATE ->
                            "{\"fullName\":\"Changed\",\"email\":\"%s\"}"
                                    .formatted(TestUsers.uniqueEmail("changed"));
                    case CREATE_APPLICATION ->
                            "{\"jobId\":%d,\"candidateId\":%d}"
                                    .formatted(job.getId(), unlinked.getId());
                    case MOVE_APPLICATION -> "{\"stage\":\"INTERVIEW\",\"position\":0}";
                    case RATE_APPLICATION -> "{\"rating\":3}";
                    case CREATE_NOTE -> "{\"content\":\"Note\"}";
                    default -> null;
                };
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return builder;
    }

    private Long candidateWithResume() throws Exception {
        Candidate withResume = candidate("With resume");
        entityManager.flush();
        mockMvc.perform(
                        multipart("/api/candidates/" + withResume.getId() + "/resume")
                                .file(
                                        new MockMultipartFile(
                                                "file", "cv.pdf", "application/pdf", PDF))
                                .with(bearer(testUsers.bearer(admin))))
                .andExpect(status().isOk());
        return withResume.getId();
    }

    private Note note(User author) {
        return noteRepository.saveAndFlush(new Note(application, author, "Existing note"));
    }

    private static RequestPostProcessor bearer(String authorization) {
        return servletRequest -> {
            if (authorization != null) {
                servletRequest.addHeader(HttpHeaders.AUTHORIZATION, authorization);
            }
            return servletRequest;
        };
    }

    private int call(MockHttpServletRequestBuilder builder, String authorization) throws Exception {
        return mockMvc.perform(builder.with(bearer(authorization)))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private Map<String, List<Map<String, Object>>> snapshot() {
        entityManager.flush();
        return Stream.of("users", "jobs", "candidates", "applications", "stage_history", "notes")
                .collect(
                        Collectors.toMap(
                                table -> table,
                                table ->
                                        jdbcTemplate.queryForList(
                                                "select * from " + table + " order by id")));
    }

    @Test
    void everyApiMappingIsClassified() {
        List<String> registered = new ArrayList<>();
        handlerMapping
                .getHandlerMethods()
                .keySet()
                .forEach(info ->
                        info.getPatternValues().stream()
                                .filter(pattern ->
                                        pattern.equals("/api") || pattern.startsWith("/api/"))
                                .forEach(pattern -> {
                                    var methods = info.getMethodsCondition().getMethods();
                                    if (methods.isEmpty()) {
                                        registered.add("ANY " + pattern);
                                    }
                                    methods.forEach(m -> registered.add(m.name() + " " + pattern));
                                }));

        assertThat(registered)
                .containsExactlyInAnyOrderElementsOf(
                        Stream.of(Endpoint.values()).map(e -> e.method + " " + e.pattern).toList());
    }

    @ParameterizedTest
    @MethodSource("protectedEndpoints")
    void rejectsMissingAndUnusableTokensWithoutChangingData(Endpoint endpoint) throws Exception {
        User deactivated = testUsers.create(Role.ADMIN);
        String deactivatedToken = testUsers.bearer(deactivated);
        deactivated.setActive(false);
        MockHttpServletRequestBuilder[] requests = new MockHttpServletRequestBuilder[6];
        String[] tokens = {
            null,
            "Bearer not-a-token",
            "Bearer "
                    + jwtService("another-secret-of-at-least-32-characters", 1)
                            .generateToken(admin),
            "Bearer " + jwtService(jwtSecret, -1).generateToken(admin),
            deactivatedToken,
            "Basic YWRtaW46cGFzc3dvcmQ="
        };
        for (int i = 0; i < tokens.length; i++) {
            requests[i] = valid(endpoint, admin);
        }
        var before = snapshot();

        for (int i = 0; i < tokens.length; i++) {
            assertThat(call(requests[i], tokens[i])).as("token %d", i).isEqualTo(401);
        }

        assertThat(snapshot()).isEqualTo(before);
    }

    private static JwtService jwtService(String secret, int hours) {
        return new JwtService(new JwtProperties(secret, Duration.ofHours(hours)));
    }

    @ParameterizedTest
    @MethodSource("protectedEndpoints")
    void anonymousInvalidInputIsUnauthorizedNotBadRequest(Endpoint endpoint) throws Exception {
        MockHttpServletRequestBuilder invalid =
                request(
                                HttpMethod.valueOf(endpoint.method),
                                endpoint.pattern.replace("{id}", "abc") + "?page=x&from=x")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{");

        assertThat(call(invalid, null)).isEqualTo(401);
    }

    @ParameterizedTest
    @MethodSource("adminEndpoints")
    void recruiterIsForbiddenFromAdminEndpoints(Endpoint endpoint) throws Exception {
        MockHttpServletRequestBuilder valid = valid(endpoint, recruiter);
        MockHttpServletRequestBuilder invalid =
                request(
                                HttpMethod.valueOf(endpoint.method),
                                endpoint.pattern.replace("{id}", "abc"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{");
        MockHttpServletRequestBuilder invalidFields =
                request(
                                HttpMethod.valueOf(endpoint.method),
                                endpoint.pattern.replace("{id}", "999999"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}");
        String token = testUsers.bearer(recruiter);
        var before = snapshot();

        assertThat(call(valid, token)).isEqualTo(403);
        assertThat(call(invalid, token)).isEqualTo(403);
        assertThat(call(invalidFields, token)).isEqualTo(403);
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @MethodSource("adminEndpoints")
    void adminReachesAdminEndpoints(Endpoint endpoint) throws Exception {
        assertThat(call(valid(endpoint, admin), testUsers.bearer(admin)))
                .isEqualTo(endpoint.success);
    }

    @ParameterizedTest
    @MethodSource("sharedEndpoints")
    void adminReachesSharedEndpoints(Endpoint endpoint) throws Exception {
        assertThat(call(valid(endpoint, admin), testUsers.bearer(admin)))
                .isEqualTo(endpoint.success);
    }

    @ParameterizedTest
    @MethodSource("sharedEndpoints")
    void recruiterReachesSharedEndpoints(Endpoint endpoint) throws Exception {
        assertThat(call(valid(endpoint, recruiter), testUsers.bearer(recruiter)))
                .isEqualTo(endpoint.success);
    }

    @Test
    void publicEndpointsNeedNoToken() throws Exception {
        assertThat(call(valid(Endpoint.LOGIN, recruiter), null)).isEqualTo(200);
        assertThat(call(request(HttpMethod.GET, "/actuator/health"), null)).isEqualTo(200);
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void onlyAdminsMoveAnApplicationOutOfHired(Role role) throws Exception {
        assertThat(call(valid(Endpoint.MOVE_APPLICATION, admin), testUsers.bearer(admin)))
                .isEqualTo(200);
        assertThat(call(move(Stage.HIRED), testUsers.bearer(recruiter))).isEqualTo(200);
        var before = snapshot();

        int result =
                call(move(Stage.OFFER), testUsers.bearer(role == Role.ADMIN ? admin : recruiter));

        if (role == Role.ADMIN) {
            assertThat(result).isEqualTo(200);
        } else {
            assertThat(result).isEqualTo(403);
            assertThat(snapshot()).isEqualTo(before);
        }
    }

    private MockHttpServletRequestBuilder move(Stage stage) {
        return request(HttpMethod.PATCH, "/api/applications/" + application.getId() + "/move")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"stage\":\"" + stage + "\",\"position\":0}");
    }

    @Test
    void onlyTheAuthorOrAnAdminDeletesANote() throws Exception {
        Note note = note(recruiter);
        String path = "/api/notes/" + note.getId();
        var before = snapshot();

        assertThat(call(request(HttpMethod.DELETE, path), testUsers.bearer(target))).isEqualTo(403);
        assertThat(snapshot()).isEqualTo(before);
        assertThat(call(request(HttpMethod.DELETE, path), testUsers.bearer(recruiter)))
                .isEqualTo(204);
        assertThat(
                        call(
                                request(HttpMethod.DELETE, "/api/notes/" + note(recruiter).getId()),
                                testUsers.bearer(admin)))
                .isEqualTo(204);
    }
}
