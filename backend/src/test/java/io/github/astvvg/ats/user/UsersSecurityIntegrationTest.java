package io.github.astvvg.ats.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.astvvg.ats.support.TestUsers;
import io.github.astvvg.ats.support.TestcontainersConfiguration;
import io.github.astvvg.ats.user.domain.Role;
import io.github.astvvg.ats.user.domain.User;
import io.github.astvvg.ats.user.repository.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TestUsers.class})
@Transactional
class UsersSecurityIntegrationTest {

    private static final String USERS = "/api/users";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TestUsers testUsers;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private User admin;
    private String adminAuth;
    private String recruiterAuth;

    @BeforeEach
    void setUp() {
        admin = testUsers.create(Role.ADMIN);
        adminAuth = testUsers.bearer(admin);
        recruiterAuth = testUsers.bearer(testUsers.create(Role.RECRUITER));
    }

    private String validCreateBody(String email) throws Exception {
        return objectMapper.writeValueAsString(
                Map.of(
                        "name", "Rita Recruiter",
                        "email", email,
                        "password", "Rita12345!",
                        "role", "RECRUITER"));
    }

    private static String statusBody(boolean active) {
        return "{\"active\":" + active + "}";
    }

    private ResultActions createUser(String auth, String body) throws Exception {
        var request = post(USERS).contentType(MediaType.APPLICATION_JSON).content(body);
        if (auth != null) {
            request.header(HttpHeaders.AUTHORIZATION, auth);
        }
        return mockMvc.perform(request);
    }

    private ResultActions updateStatus(String auth, Object id, String body) throws Exception {
        var request = patch(USERS + "/" + id).contentType(MediaType.APPLICATION_JSON).content(body);
        if (auth != null) {
            request.header(HttpHeaders.AUTHORIZATION, auth);
        }
        return mockMvc.perform(request);
    }

    private int loginStatus(String email, String password) throws Exception {
        return mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                Map.of("email", email, "password", password))))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private static void assertProblem(ResultActions result, int status) throws Exception {
        result.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(status));
    }

    private void assertNoSecrets(MvcResult result) throws Exception {
        assertThat(result.getResponse().getContentAsString())
                .doesNotContainIgnoringCase("password")
                .doesNotContain(admin.getPasswordHash());
    }

    // ---- GET /api/users ----

    @Test
    void listWithoutTokenIsUnauthorized() throws Exception {
        assertProblem(mockMvc.perform(get(USERS)), 401);
    }

    @Test
    void listAsRecruiterIsForbidden() throws Exception {
        assertProblem(
                mockMvc.perform(get(USERS).header(HttpHeaders.AUTHORIZATION, recruiterAuth)), 403);
    }

    @Test
    void listAsAdminReturnsPageSortedByNewestFirst() throws Exception {
        testUsers.create(Role.RECRUITER, false);

        MvcResult result =
                mockMvc.perform(get(USERS).header(HttpHeaders.AUTHORIZATION, adminAuth))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.content").isArray())
                        .andExpect(jsonPath("$.page").value(0))
                        .andExpect(jsonPath("$.size").value(20))
                        .andExpect(jsonPath("$.totalElements").isNumber())
                        .andExpect(jsonPath("$.totalPages").isNumber())
                        .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("totalElements").asLong()).isGreaterThanOrEqualTo(3);
        List<Instant> createdAt = new ArrayList<>();
        json.get("content").forEach(u -> createdAt.add(Instant.parse(u.get("createdAt").asText())));
        assertThat(createdAt).isSortedAccordingTo(Comparator.reverseOrder());
        assertNoSecrets(result);
    }

    @Test
    void listSortsByAllowedProperty() throws Exception {
        MvcResult result =
                mockMvc.perform(
                                get(USERS)
                                        .param("sort", "email,asc")
                                        .header(HttpHeaders.AUTHORIZATION, adminAuth))
                        .andExpect(status().isOk())
                        .andReturn();

        List<String> emails = new ArrayList<>();
        objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("content")
                .forEach(u -> emails.add(u.get("email").asText()));
        assertThat(emails).isSorted();
    }

    @Test
    void listCapsPageSizeAt100() throws Exception {
        mockMvc.perform(
                        get(USERS)
                                .param("size", "500")
                                .header(HttpHeaders.AUTHORIZATION, adminAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @ParameterizedTest
    @ValueSource(strings = {"passwordHash", "passwordHash,desc", "unknown", "name,sideways"})
    void listRejectsUnsupportedSort(String sort) throws Exception {
        ResultActions result =
                mockMvc.perform(
                        get(USERS)
                                .param("sort", sort)
                                .header(HttpHeaders.AUTHORIZATION, adminAuth));
        assertProblem(result, 400);
        assertThat(result.andReturn().getResponse().getContentAsString())
                .doesNotContain(admin.getPasswordHash());
    }

    // ---- POST /api/users ----

    @Test
    void createWithoutTokenIsUnauthorized() throws Exception {
        assertProblem(createUser(null, validCreateBody(TestUsers.uniqueEmail("anon"))), 401);
    }

    @Test
    void createAsRecruiterIsForbidden() throws Exception {
        assertProblem(
                createUser(recruiterAuth, validCreateBody(TestUsers.uniqueEmail("rec"))), 403);
    }

    @Test
    void adminCreatesUserThatCanLogIn() throws Exception {
        String email = TestUsers.uniqueEmail("rita");

        MvcResult result =
                createUser(adminAuth, validCreateBody(email.toUpperCase()))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.id").isNumber())
                        .andExpect(jsonPath("$.name").value("Rita Recruiter"))
                        .andExpect(jsonPath("$.email").value(email))
                        .andExpect(jsonPath("$.role").value("RECRUITER"))
                        .andExpect(jsonPath("$.active").value(true))
                        .andExpect(jsonPath("$.createdAt").isNotEmpty())
                        .andReturn();
        assertNoSecrets(result);

        User created = userRepository.findByEmail(email).orElseThrow();
        assertThat(created.getPasswordHash()).startsWith("$2");
        assertThat(passwordEncoder.matches("Rita12345!", created.getPasswordHash())).isTrue();
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain(created.getPasswordHash());
        assertThat(loginStatus(email, "Rita12345!")).isEqualTo(200);
    }

    static Stream<Arguments> invalidCreateBodies() {
        String longPassword = "a".repeat(73);
        return Stream.of(
                Arguments.of(
                        "{\"name\":\"Ana\",\"email\":\"not-an-email\",\"password\":\"Password1\",\"role\":\"RECRUITER\"}",
                        "email"),
                Arguments.of(
                        "{\"name\":\"Ana\",\"email\":\"ana@example.com\",\"password\":\"short\",\"role\":\"RECRUITER\"}",
                        "password"),
                Arguments.of(
                        "{\"name\":\"Ana\",\"email\":\"ana@example.com\",\"password\":\""
                                + longPassword
                                + "\",\"role\":\"RECRUITER\"}",
                        "password"),
                Arguments.of(
                        "{\"name\":\"Ana\",\"email\":\"ana@example.com\",\"password\":\"Password1\"}",
                        "role"),
                Arguments.of(
                        "{\"name\":\"Ana\",\"email\":\"ana@example.com\",\"password\":\"Password1\",\"role\":\"BOSS\"}",
                        "role"),
                Arguments.of(
                        "{\"name\":\" \",\"email\":\"ana@example.com\",\"password\":\"Password1\",\"role\":\"RECRUITER\"}",
                        "name"));
    }

    @Test
    void createWithPasswordOverBcryptByteLimitIsBadRequest() throws Exception {
        // 40 characters (within @Size) but 80 bytes in UTF-8.
        String body =
                objectMapper.writeValueAsString(
                        Map.of(
                                "name",
                                "Ana",
                                "email",
                                TestUsers.uniqueEmail("bytes"),
                                "password",
                                "á".repeat(40),
                                "role",
                                "RECRUITER"));

        ResultActions result = createUser(adminAuth, body);
        assertProblem(result, 400);
        result.andExpect(jsonPath("$.errors[*].field", hasItem("password")));
    }

    @Test
    void createAsRecruiterWithInvalidBodyIsForbidden() throws Exception {
        assertProblem(createUser(recruiterAuth, "{\"email\":\"not-an-email\"}"), 403);
    }

    @Test
    void updateStatusAsRecruiterWithInvalidBodyIsForbidden() throws Exception {
        User target = testUsers.create(Role.RECRUITER);
        assertProblem(updateStatus(recruiterAuth, target.getId(), "{}"), 403);
    }

    @ParameterizedTest
    @MethodSource("invalidCreateBodies")
    void createWithInvalidBodyIsBadRequest(String body, String field) throws Exception {
        ResultActions result = createUser(adminAuth, body);
        assertProblem(result, 400);
        result.andExpect(jsonPath("$.errors[*].field", hasItem(field)));
    }

    @Test
    void createWithDuplicateEmailInAnyCaseIsConflict() throws Exception {
        String email = TestUsers.uniqueEmail("dup");
        createUser(adminAuth, validCreateBody(email)).andExpect(status().isCreated());

        for (String variant : List.of(email, email.toUpperCase(), "Dup" + email.substring(3))) {
            ResultActions result = createUser(adminAuth, validCreateBody(variant));
            assertProblem(result, 409);
            result.andExpect(jsonPath("$.detail").value("Email already registered"));
        }
    }

    // ---- PATCH /api/users/{id} ----

    @Test
    void updateStatusWithoutTokenIsUnauthorized() throws Exception {
        User target = testUsers.create(Role.RECRUITER);
        assertProblem(updateStatus(null, target.getId(), statusBody(false)), 401);
    }

    @Test
    void updateStatusAsRecruiterIsForbidden() throws Exception {
        User target = testUsers.create(Role.RECRUITER);
        assertProblem(updateStatus(recruiterAuth, target.getId(), statusBody(false)), 403);
        assertThat(userRepository.findById(target.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void deactivationBlocksLoginAndExistingTokenUntilReactivated() throws Exception {
        User target = testUsers.create(Role.RECRUITER);
        String targetAuth = testUsers.bearer(target);
        assertThat(loginStatus(target.getEmail(), TestUsers.PASSWORD)).isEqualTo(200);

        MvcResult result =
                updateStatus(adminAuth, target.getId(), statusBody(false))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.id").value(target.getId()))
                        .andExpect(jsonPath("$.active").value(false))
                        .andReturn();
        assertNoSecrets(result);

        assertThat(loginStatus(target.getEmail(), TestUsers.PASSWORD)).isEqualTo(401);
        assertProblem(
                mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, targetAuth)), 401);

        updateStatus(adminAuth, target.getId(), statusBody(true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
        assertThat(loginStatus(target.getEmail(), TestUsers.PASSWORD)).isEqualTo(200);
    }

    @Test
    void settingSameStatusIsAccepted() throws Exception {
        updateStatus(adminAuth, admin.getId(), statusBody(true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void updateStatusOfMissingUserIsNotFound() throws Exception {
        assertProblem(updateStatus(adminAuth, Long.MAX_VALUE, statusBody(false)), 404);
    }

    @Test
    void selfDeactivationIsConflict() throws Exception {
        ResultActions result = updateStatus(adminAuth, admin.getId(), statusBody(false));
        assertProblem(result, 409);
        result.andExpect(jsonPath("$.detail").value("You cannot deactivate your own account"));
        assertThat(userRepository.findById(admin.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void updateStatusWithoutActiveIsBadRequest() throws Exception {
        User target = testUsers.create(Role.RECRUITER);
        ResultActions result = updateStatus(adminAuth, target.getId(), "{}");
        assertProblem(result, 400);
        result.andExpect(jsonPath("$.errors[*].field", hasItem("active")));
    }
}
