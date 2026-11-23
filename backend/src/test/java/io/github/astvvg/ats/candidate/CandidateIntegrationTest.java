package io.github.astvvg.ats.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import org.springframework.mock.web.MockMultipartFile;
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
class CandidateIntegrationTest {

    private static final String CANDIDATES = "/api/candidates";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TestUsers testUsers;

    private String adminAuth;
    private String recruiterAuth;
    private String token;

    @BeforeEach
    void setUp() {
        adminAuth = testUsers.bearer(testUsers.create(Role.ADMIN));
        recruiterAuth = testUsers.bearer(testUsers.create(Role.RECRUITER));
        // Unique text so list assertions only see the candidates created by each test.
        token = "tok" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private String body(String fullName, String email, String phone, String source)
            throws Exception {
        Map<String, Object> json = new HashMap<>();
        json.put("fullName", fullName);
        json.put("email", email);
        json.put("phone", phone);
        json.put("source", source);
        return objectMapper.writeValueAsString(json);
    }

    private String email(String prefix) {
        return prefix + "-" + token + "@example.com";
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

    private long createCandidate(String auth, String fullName, String email) throws Exception {
        return json(send(post(CANDIDATES), auth, body(fullName, email, null, null))
                        .andExpect(status().isCreated()))
                .get("id")
                .asLong();
    }

    private JsonNode list(String auth, String... params) throws Exception {
        MockHttpServletRequestBuilder request = get(CANDIDATES);
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return json(perform(request, auth).andExpect(status().isOk()));
    }

    private static List<String> names(JsonNode page) {
        List<String> names = new ArrayList<>();
        page.get("content").forEach(c -> names.add(c.get("fullName").asText()));
        return names;
    }

    private static void assertProblem(ResultActions result, int status) throws Exception {
        result.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(status));
    }

    private static void assertNoResumePath(ResultActions result) throws Exception {
        assertThat(result.andReturn().getResponse().getContentAsString())
                .doesNotContainIgnoringCase("resumePath")
                .doesNotContainIgnoringCase("resume_path")
                .doesNotContain("resumes/");
    }

    // ---- POST /api/candidates ----

    @Test
    void createNormalizesEmailAndReturnsCandidate() throws Exception {
        String email = email("laura");
        ResultActions result =
                send(
                                post(CANDIDATES),
                                recruiterAuth,
                                body(
                                        "  Laura Gómez ",
                                        "  " + email.toUpperCase() + " ",
                                        " +52 777 ",
                                        "LinkedIn"))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.id").isNumber())
                        .andExpect(jsonPath("$.fullName").value("Laura Gómez"))
                        .andExpect(jsonPath("$.email").value(email))
                        .andExpect(jsonPath("$.phone").value("+52 777"))
                        .andExpect(jsonPath("$.source").value("LinkedIn"))
                        .andExpect(jsonPath("$.hasResume").value(false))
                        .andExpect(jsonPath("$.createdAt").isNotEmpty())
                        .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        assertThat(json(result).size()).isEqualTo(8);
        assertNoResumePath(result);
    }

    @Test
    void duplicateEmailInAnyCaseIsConflict() throws Exception {
        String email = email("dup");
        createCandidate(adminAuth, "First", email);

        for (String variant : List.of(email, email.toUpperCase(), " " + email + " ")) {
            ResultActions result =
                    send(post(CANDIDATES), recruiterAuth, body("Second", variant, null, null));
            assertProblem(result, 409);
            result.andExpect(
                    jsonPath("$.detail").value("A candidate with this email already exists"));
        }
    }

    static Stream<Arguments> invalidBodies() {
        return Stream.of(
                Arguments.of("{\"fullName\":\"\",\"email\":\"a@example.com\"}", "fullName"),
                Arguments.of("{\"email\":\"a@example.com\"}", "fullName"),
                Arguments.of(
                        "{\"fullName\":\"" + "a".repeat(201) + "\",\"email\":\"a@example.com\"}",
                        "fullName"),
                Arguments.of("{\"fullName\":\"Ana\"}", "email"),
                Arguments.of("{\"fullName\":\"Ana\",\"email\":\"not-an-email\"}", "email"),
                Arguments.of(
                        "{\"fullName\":\"Ana\",\"email\":\"" + "a".repeat(250) + "@example.com\"}",
                        "email"),
                Arguments.of(
                        "{\"fullName\":\"Ana\",\"email\":\"a@example.com\",\"phone\":\""
                                + "1".repeat(31)
                                + "\"}",
                        "phone"),
                Arguments.of(
                        "{\"fullName\":\"Ana\",\"email\":\"a@example.com\",\"source\":\""
                                + "s".repeat(101)
                                + "\"}",
                        "source"));
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    void invalidCreateBodyIsBadRequest(String jsonBody, String field) throws Exception {
        ResultActions result = send(post(CANDIDATES), adminAuth, jsonBody);
        assertProblem(result, 400);
        result.andExpect(jsonPath("$.errors[*].field", hasItem(field)));
    }

    // ---- GET /api/candidates/{id} ----

    @Test
    void getReturnsCandidateAndMissingIsNotFound() throws Exception {
        long id = createCandidate(adminAuth, "Get me", email("get"));

        ResultActions result =
                perform(get(CANDIDATES + "/" + id), recruiterAuth)
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.id").value(id))
                        .andExpect(jsonPath("$.fullName").value("Get me"))
                        .andExpect(jsonPath("$.hasResume").value(false));
        assertNoResumePath(result);
        assertProblem(perform(get(CANDIDATES + "/" + Long.MAX_VALUE), adminAuth), 404);
    }

    // ---- PUT /api/candidates/{id} ----

    @Test
    void updateReplacesFieldsAndKeepsOwnEmail() throws Exception {
        String email = email("own");
        long id = createCandidate(adminAuth, "Before", email);
        JsonNode before = json(perform(get(CANDIDATES + "/" + id), adminAuth));

        ResultActions result =
                send(
                                put(CANDIDATES + "/" + id),
                                recruiterAuth,
                                body("After", email.toUpperCase(), "555", "Referral"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.fullName").value("After"))
                        .andExpect(jsonPath("$.email").value(email))
                        .andExpect(jsonPath("$.phone").value("555"))
                        .andExpect(jsonPath("$.source").value("Referral"));

        JsonNode after = json(result);
        assertThat(after.get("createdAt").asText()).isEqualTo(before.get("createdAt").asText());
        assertThat(Instant.parse(after.get("updatedAt").asText()))
                .isAfter(Instant.parse(before.get("updatedAt").asText()));
        assertNoResumePath(result);
    }

    @Test
    void updateCanClearOptionalFieldsAndChangeEmail() throws Exception {
        long id = createCandidate(adminAuth, "Ana", email("old"));
        send(put(CANDIDATES + "/" + id), adminAuth, body("Ana", email("new"), "1", "x"))
                .andExpect(status().isOk());

        send(put(CANDIDATES + "/" + id), adminAuth, body("Ana", email("new"), null, " "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email("new")))
                .andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.source").doesNotExist());
    }

    @Test
    void updateWithEmailOfAnotherCandidateIsConflict() throws Exception {
        String taken = email("taken");
        createCandidate(adminAuth, "Owner", taken);
        long id = createCandidate(adminAuth, "Other", email("other"));

        ResultActions result =
                send(
                        put(CANDIDATES + "/" + id),
                        recruiterAuth,
                        body("Other", taken.toUpperCase(), null, null));
        assertProblem(result, 409);
        perform(get(CANDIDATES + "/" + id), adminAuth)
                .andExpect(jsonPath("$.email").value(email("other")));
    }

    @Test
    void updateMissingCandidateIsNotFound() throws Exception {
        assertProblem(
                send(
                        put(CANDIDATES + "/" + Long.MAX_VALUE),
                        adminAuth,
                        body("Ana", email("missing"), null, null)),
                404);
    }

    @Test
    void updateWithInvalidBodyIsBadRequest() throws Exception {
        long id = createCandidate(adminAuth, "Ana", email("invalid"));
        ResultActions result =
                send(put(CANDIDATES + "/" + id), adminAuth, body("Ana", "nope", null, null));
        assertProblem(result, 400);
        result.andExpect(jsonPath("$.errors[*].field", hasItem("email")));
    }

    // ---- DELETE /api/candidates/{id} ----

    @Test
    void deleteRemovesCandidate() throws Exception {
        long id = createCandidate(adminAuth, "Bye", email("bye"));

        perform(delete(CANDIDATES + "/" + id), recruiterAuth).andExpect(status().isNoContent());

        assertProblem(perform(get(CANDIDATES + "/" + id), adminAuth), 404);
        assertProblem(perform(delete(CANDIDATES + "/" + id), adminAuth), 404);
        // The email is free again.
        createCandidate(adminAuth, "Again", email("bye"));
    }

    // ---- GET /api/candidates ----

    @Test
    void listSearchesNameOrEmailAndPaginates() throws Exception {
        createCandidate(adminAuth, "Ana " + token, "ana-" + UUID.randomUUID() + "@example.com");
        createCandidate(adminAuth, "Luis", "luis-" + token + "@example.com");
        createCandidate(adminAuth, "Marta " + token.toUpperCase(), email("marta"));
        createCandidate(adminAuth, "Unrelated", "x-" + UUID.randomUUID() + "@example.com");

        JsonNode all = list(recruiterAuth, "q", token);
        assertThat(names(all))
                .containsExactly("Marta " + token.toUpperCase(), "Luis", "Ana " + token);
        assertThat(all.get("totalElements").asLong()).isEqualTo(3);
        assertThat(all.get("size").asInt()).isEqualTo(20);

        JsonNode second = list(adminAuth, "q", token.toUpperCase(), "size", "2", "page", "1");
        assertThat(names(second)).containsExactly("Ana " + token);
        assertThat(second.get("page").asInt()).isEqualTo(1);
        assertThat(second.get("totalPages").asInt()).isEqualTo(2);

        assertThat(names(list(adminAuth, "q", "luis-" + token))).containsExactly("Luis");
    }

    @Test
    void listSortsByAllowedProperties() throws Exception {
        createCandidate(adminAuth, "B " + token, "c-" + token + "@example.com");
        createCandidate(adminAuth, "C " + token, "a-" + token + "@example.com");
        createCandidate(adminAuth, "A " + token, "b-" + token + "@example.com");

        assertThat(names(list(adminAuth, "q", token, "sort", "fullName,asc")))
                .containsExactly("A " + token, "B " + token, "C " + token);
        assertThat(names(list(adminAuth, "q", token, "sort", "email,desc")))
                .containsExactly("B " + token, "A " + token, "C " + token);
        assertThat(names(list(adminAuth, "q", token, "sort", "createdAt,asc")))
                .containsExactly("B " + token, "C " + token, "A " + token);
    }

    @Test
    void listNeverExposesResumePath() throws Exception {
        createCandidate(adminAuth, "Ana " + token, email("ana"));
        ResultActions result = perform(get(CANDIDATES).param("q", token), adminAuth);
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].hasResume").value(false));
        assertNoResumePath(result);
    }

    @Test
    void listCapsPageSizeAt100() throws Exception {
        assertThat(list(adminAuth, "size", "500").get("size").asInt()).isEqualTo(100);
    }

    @ParameterizedTest
    @ValueSource(strings = {"resumePath", "phone", "id", "updatedAt", "unknown,desc"})
    void listRejectsUnsupportedSort(String sort) throws Exception {
        assertProblem(perform(get(CANDIDATES).param("sort", sort), adminAuth), 400);
    }

    // ---- Authentication ----

    static Stream<Arguments> endpoints() {
        String candidateBody = "{\"fullName\":\"x\",\"email\":\"x@example.com\"}";
        MockMultipartFile pdf =
                new MockMultipartFile(
                        "file", "cv.pdf", MediaType.APPLICATION_PDF_VALUE, "%PDF-1.4".getBytes());
        return Stream.of(
                Arguments.of(get(CANDIDATES), null),
                Arguments.of(post(CANDIDATES), candidateBody),
                Arguments.of(get(CANDIDATES + "/1"), null),
                Arguments.of(put(CANDIDATES + "/1"), candidateBody),
                Arguments.of(delete(CANDIDATES + "/1"), null),
                Arguments.of(multipart(CANDIDATES + "/1/resume").file(pdf), null),
                Arguments.of(get(CANDIDATES + "/1/resume"), null));
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
