package io.github.astvvg.ats.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TestUsers.class})
@Transactional
class JobIntegrationTest {

    private static final String JOBS = "/api/jobs";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TestUsers testUsers;

    private User admin;
    private User recruiter;
    private String adminAuth;
    private String recruiterAuth;
    private String token;

    @BeforeEach
    void setUp() {
        admin = testUsers.create(Role.ADMIN);
        recruiter = testUsers.create(Role.RECRUITER);
        adminAuth = testUsers.bearer(admin);
        recruiterAuth = testUsers.bearer(recruiter);
        // Unique text so list assertions only see the jobs created by each test.
        token = "tok" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private String body(String title, String department, String location, String description)
            throws Exception {
        Map<String, Object> json = new HashMap<>();
        json.put("title", title);
        json.put("department", department);
        json.put("location", location);
        json.put("description", description);
        return objectMapper.writeValueAsString(json);
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String auth)
            throws Exception {
        if (auth != null) {
            request.header(HttpHeaders.AUTHORIZATION, auth);
        }
        return mockMvc.perform(request);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String auth, String jsonBody)
            throws Exception {
        return perform(request.contentType(MediaType.APPLICATION_JSON).content(jsonBody), auth);
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private long createJob(String auth, String title, String department) throws Exception {
        ResultActions result =
                send(post(JOBS), auth, body(title, department, null, null))
                        .andExpect(status().isCreated());
        return json(result).get("id").asLong();
    }

    private void setStatus(long id, String status) throws Exception {
        send(patch(JOBS + "/" + id + "/status"), adminAuth, "{\"status\":\"" + status + "\"}")
                .andExpect(status().isOk());
    }

    private JsonNode list(String auth, String... params) throws Exception {
        MockHttpServletRequestBuilder request = get(JOBS);
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return json(perform(request, auth).andExpect(status().isOk()));
    }

    private static List<String> titles(JsonNode page) {
        List<String> titles = new ArrayList<>();
        page.get("content").forEach(job -> titles.add(job.get("title").asText()));
        return titles;
    }

    private static void assertProblem(ResultActions result, int status) throws Exception {
        result.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(status));
    }

    // ---- POST /api/jobs ----

    @Test
    void adminCreatesOpenJobOwnedByTokenUser() throws Exception {
        ResultActions result =
                send(
                                post(JOBS),
                                adminAuth,
                                body("  Backend Developer  ", "Engineering", "Remote", "Java"))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.id").isNumber())
                        .andExpect(jsonPath("$.title").value("Backend Developer"))
                        .andExpect(jsonPath("$.department").value("Engineering"))
                        .andExpect(jsonPath("$.location").value("Remote"))
                        .andExpect(jsonPath("$.description").value("Java"))
                        .andExpect(jsonPath("$.status").value("OPEN"))
                        .andExpect(jsonPath("$.createdBy.id").value(admin.getId()))
                        .andExpect(jsonPath("$.createdBy.name").value(admin.getName()))
                        .andExpect(jsonPath("$.createdAt").isNotEmpty())
                        .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        JsonNode job = json(result);
        assertThat(job.size()).isEqualTo(9);
        assertThat(job.get("createdBy").size()).isEqualTo(2);
        assertThat(job.get("updatedAt").asText()).isEqualTo(job.get("createdAt").asText());
    }

    @Test
    void recruiterCreatesJobWithOnlyTitle() throws Exception {
        send(post(JOBS), recruiterAuth, "{\"title\":\"QA\",\"department\":\"  \"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.department").doesNotExist())
                .andExpect(jsonPath("$.createdBy.id").value(recruiter.getId()));
    }

    @Test
    void statusInCreateBodyIsIgnored() throws Exception {
        send(post(JOBS), adminAuth, "{\"title\":\"QA\",\"status\":\"CLOSED\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    static Stream<Arguments> invalidBodies() {
        return Stream.of(
                Arguments.of("{\"title\":\"\"}", "title"),
                Arguments.of("{\"title\":\"   \"}", "title"),
                Arguments.of("{}", "title"),
                Arguments.of("{\"title\":\"" + "a".repeat(201) + "\"}", "title"),
                Arguments.of(
                        "{\"title\":\"ok\",\"department\":\"" + "a".repeat(101) + "\"}",
                        "department"),
                Arguments.of(
                        "{\"title\":\"ok\",\"location\":\"" + "a".repeat(101) + "\"}", "location"),
                Arguments.of(
                        "{\"title\":\"ok\",\"description\":\"" + "a".repeat(5001) + "\"}",
                        "description"));
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    void invalidCreateBodyIsBadRequest(String jsonBody, String field) throws Exception {
        ResultActions result = send(post(JOBS), adminAuth, jsonBody);
        assertProblem(result, 400);
        result.andExpect(jsonPath("$.errors[*].field", hasItem(field)));
    }

    // ---- GET /api/jobs/{id} ----

    @Test
    void getReturnsJobAndMissingIsNotFound() throws Exception {
        long id = createJob(recruiterAuth, "Get me", null);

        perform(get(JOBS + "/" + id), adminAuth)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.title").value("Get me"));
        assertProblem(perform(get(JOBS + "/" + Long.MAX_VALUE), recruiterAuth), 404);
    }

    // ---- PUT /api/jobs/{id} ----

    @Test
    void updateChangesFieldsButNotStatusOrCreator() throws Exception {
        long id = createJob(adminAuth, "Old title", "Old dept");
        setStatus(id, "CLOSED");
        JsonNode before = json(perform(get(JOBS + "/" + id), adminAuth));

        ResultActions result =
                send(put(JOBS + "/" + id), recruiterAuth, body("New title", null, "Madrid", "Desc"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.title").value("New title"))
                        .andExpect(jsonPath("$.department").doesNotExist())
                        .andExpect(jsonPath("$.location").value("Madrid"))
                        .andExpect(jsonPath("$.description").value("Desc"))
                        .andExpect(jsonPath("$.status").value("CLOSED"))
                        .andExpect(jsonPath("$.createdBy.id").value(admin.getId()));

        JsonNode after = json(result);
        assertThat(after.get("createdAt").asText()).isEqualTo(before.get("createdAt").asText());
        assertThat(Instant.parse(after.get("updatedAt").asText()))
                .isAfter(Instant.parse(before.get("updatedAt").asText()));
    }

    @Test
    void updateMissingJobIsNotFound() throws Exception {
        assertProblem(
                send(put(JOBS + "/" + Long.MAX_VALUE), adminAuth, body("T", null, null, null)),
                404);
    }

    @Test
    void updateWithBlankTitleIsBadRequest() throws Exception {
        long id = createJob(adminAuth, "Keep", null);
        ResultActions result = send(put(JOBS + "/" + id), adminAuth, "{\"title\":\"\"}");
        assertProblem(result, 400);
        result.andExpect(jsonPath("$.errors[*].field", hasItem("title")));
    }

    // ---- PATCH /api/jobs/{id}/status ----

    @Test
    void closingIsIdempotentAndClosedJobStaysVisible() throws Exception {
        long id = createJob(adminAuth, token + " closing", null);

        for (int i = 0; i < 2; i++) {
            send(patch(JOBS + "/" + id + "/status"), recruiterAuth, "{\"status\":\"CLOSED\"}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id))
                    .andExpect(jsonPath("$.status").value("CLOSED"));
        }

        perform(get(JOBS + "/" + id), adminAuth)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
        assertThat(titles(list(adminAuth, "q", token))).containsExactly(token + " closing");
    }

    @Test
    void closedJobCanBeReopened() throws Exception {
        long id = createJob(adminAuth, "Reopen", null);
        setStatus(id, "CLOSED");

        send(patch(JOBS + "/" + id + "/status"), adminAuth, "{\"status\":\"OPEN\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));
        send(patch(JOBS + "/" + id + "/status"), adminAuth, "{\"status\":\"OPEN\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"status\":null}", "{\"status\":\"ARCHIVED\"}"})
    void invalidStatusBodyIsBadRequest(String jsonBody) throws Exception {
        long id = createJob(adminAuth, "Status", null);
        ResultActions result = send(patch(JOBS + "/" + id + "/status"), adminAuth, jsonBody);
        assertProblem(result, 400);
        result.andExpect(jsonPath("$.errors[*].field", hasItem("status")));
    }

    @Test
    void statusOfMissingJobIsNotFound() throws Exception {
        assertProblem(
                send(
                        patch(JOBS + "/" + Long.MAX_VALUE + "/status"),
                        adminAuth,
                        "{\"status\":\"CLOSED\"}"),
                404);
    }

    // ---- GET /api/jobs ----

    @Test
    void listFiltersByStatusAndText() throws Exception {
        long open = createJob(adminAuth, token + " Open", "Sales");
        long closed = createJob(adminAuth, token + " Closed", "Sales");
        createJob(recruiterAuth, "By department", "Dept " + token.toUpperCase());
        createJob(adminAuth, "Unrelated " + UUID.randomUUID(), "Other");
        setStatus(closed, "CLOSED");

        assertThat(titles(list(adminAuth, "q", token)))
                .containsExactlyInAnyOrder(token + " Open", token + " Closed", "By department");
        assertThat(titles(list(recruiterAuth, "q", token.toUpperCase(), "status", "CLOSED")))
                .containsExactly(token + " Closed");
        assertThat(titles(list(adminAuth, "q", token, "status", "OPEN")))
                .containsExactlyInAnyOrder(token + " Open", "By department");
        assertThat(open).isNotEqualTo(closed);
    }

    @Test
    void listWithStatusClosedReturnsOnlyClosedJobs() throws Exception {
        setStatus(createJob(adminAuth, token + " c", null), "CLOSED");
        createJob(adminAuth, token + " o", null);

        JsonNode page = list(adminAuth, "status", "CLOSED", "size", "100");
        page.get("content")
                .forEach(job -> assertThat(job.get("status").asText()).isEqualTo("CLOSED"));
        assertThat(titles(page)).contains(token + " c").doesNotContain(token + " o");
    }

    @Test
    void listDefaultsToNewestFirstWithPageShape() throws Exception {
        createJob(adminAuth, token + " 1", null);
        createJob(adminAuth, token + " 2", null);
        createJob(adminAuth, token + " 3", null);

        JsonNode page = list(adminAuth, "q", token);
        assertThat(titles(page)).containsExactly(token + " 3", token + " 2", token + " 1");
        assertThat(page.get("page").asInt()).isZero();
        assertThat(page.get("size").asInt()).isEqualTo(20);
        assertThat(page.get("totalElements").asLong()).isEqualTo(3);
        assertThat(page.get("totalPages").asInt()).isEqualTo(1);
    }

    @Test
    void listSortsByTitleAndPaginates() throws Exception {
        createJob(adminAuth, token + " b", null);
        createJob(adminAuth, token + " c", null);
        createJob(adminAuth, token + " a", null);

        assertThat(titles(list(adminAuth, "q", token, "sort", "title,asc")))
                .containsExactly(token + " a", token + " b", token + " c");
        JsonNode second =
                list(adminAuth, "q", token, "sort", "title,desc", "size", "2", "page", "1");
        assertThat(titles(second)).containsExactly(token + " a");
        assertThat(second.get("totalPages").asInt()).isEqualTo(2);
    }

    @Test
    void listCapsPageSizeAt100() throws Exception {
        assertThat(list(adminAuth, "size", "500").get("size").asInt()).isEqualTo(100);
    }

    @ParameterizedTest
    @ValueSource(strings = {"status", "id", "createdBy", "description,asc", "unknown,desc"})
    void listRejectsUnsupportedSort(String sort) throws Exception {
        assertProblem(perform(get(JOBS).param("sort", sort), adminAuth), 400);
    }

    @Test
    void listRejectsUnknownStatus() throws Exception {
        assertProblem(perform(get(JOBS).param("status", "ARCHIVED"), adminAuth), 400);
    }

    // ---- Authentication ----

    static Stream<Arguments> endpoints() {
        String jobBody = "{\"title\":\"x\"}";
        return Stream.of(
                Arguments.of(get(JOBS), null),
                Arguments.of(post(JOBS), jobBody),
                Arguments.of(get(JOBS + "/1"), null),
                Arguments.of(put(JOBS + "/1"), jobBody),
                Arguments.of(patch(JOBS + "/1/status"), "{\"status\":\"CLOSED\"}"));
    }

    @ParameterizedTest
    @MethodSource("endpoints")
    void endpointsRequireToken(MockHttpServletRequestBuilder request, String jsonBody)
            throws Exception {
        if (jsonBody != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(jsonBody);
        }
        assertProblem(mockMvc.perform(request), 401);
    }
}
