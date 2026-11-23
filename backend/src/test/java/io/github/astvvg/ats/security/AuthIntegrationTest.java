package io.github.astvvg.ats.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.astvvg.ats.support.TestcontainersConfiguration;
import io.github.astvvg.ats.user.domain.Role;
import io.github.astvvg.ats.user.domain.User;
import io.github.astvvg.ats.user.repository.UserRepository;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
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
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class AuthIntegrationTest {

    private static final String PASSWORD = "Correct-horse-42";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtProperties jwtProperties;

    private User createUser(Role role, boolean active) {
        User user =
                new User(
                        "Auth Test",
                        "auth-" + UUID.randomUUID() + "@example.com",
                        passwordEncoder.encode(PASSWORD),
                        role);
        user.setActive(active);
        return userRepository.saveAndFlush(user);
    }

    private MvcResult login(String email, String password) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("email", email, "password", password));
        return mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andReturn();
    }

    private String tokenFor(User user) throws Exception {
        MvcResult result = login(user.getEmail(), PASSWORD);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("accessToken")
                .asText();
    }

    private static void assertNoSecrets(MvcResult result, User user) throws Exception {
        String body = result.getResponse().getContentAsString();
        assertThat(body)
                .doesNotContainIgnoringCase("password")
                .doesNotContain(PASSWORD)
                .doesNotContain(user.getPasswordHash());
    }

    @Test
    void loginWithValidCredentialsReturnsToken() throws Exception {
        User user = createUser(Role.ADMIN, true);

        MvcResult result = login(user.getEmail(), PASSWORD);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("accessToken").asText()).isNotBlank();
        assertThat(json.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(json.get("expiresIn").asLong()).isEqualTo(7200);
        assertNoSecrets(result, user);
    }

    @Test
    void loginNormalizesEmail() throws Exception {
        User user = createUser(Role.RECRUITER, true);

        MvcResult result = login(user.getEmail().toUpperCase(), PASSWORD);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void passwordOverBcryptByteLimitIsRejectedAsBadCredentials() throws Exception {
        User user = createUser(Role.RECRUITER, true);
        // 40 characters, 80 bytes in UTF-8.
        String longPassword = "á".repeat(40);

        MvcResult known = login(user.getEmail(), longPassword);
        MvcResult unknown = login("nobody-" + UUID.randomUUID() + "@example.com", longPassword);

        assertThat(known.getResponse().getStatus()).isEqualTo(401);
        assertThat(unknown.getResponse().getStatus()).isEqualTo(401);
        assertThat(known.getResponse().getContentAsString())
                .isEqualTo(
                        login(user.getEmail(), "Wrong-password-1")
                                .getResponse()
                                .getContentAsString());
    }

    @Test
    void loginPasswordLongerThan128CharactersIsBadRequest() throws Exception {
        User user = createUser(Role.RECRUITER, true);

        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                Map.of(
                                                        "email",
                                                        user.getEmail(),
                                                        "password",
                                                        "a".repeat(129)))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[*].field", hasItem("password")));
    }

    @Test
    void failedLoginsAreIndistinguishable() throws Exception {
        User active = createUser(Role.RECRUITER, true);
        User inactive = createUser(Role.RECRUITER, false);

        MvcResult wrongPassword = login(active.getEmail(), "Wrong-password-1");
        MvcResult unknownEmail = login("nobody-" + UUID.randomUUID() + "@example.com", PASSWORD);
        MvcResult inactiveUser = login(inactive.getEmail(), PASSWORD);

        for (MvcResult result : new MvcResult[] {wrongPassword, unknownEmail, inactiveUser}) {
            assertThat(result.getResponse().getStatus()).isEqualTo(401);
            assertThat(result.getResponse().getContentType())
                    .startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        }
        String expected = wrongPassword.getResponse().getContentAsString();
        assertThat(objectMapper.readTree(expected).get("detail").asText())
                .isEqualTo("Authentication failed");
        assertThat(unknownEmail.getResponse().getContentAsString()).isEqualTo(expected);
        assertThat(inactiveUser.getResponse().getContentAsString()).isEqualTo(expected);
    }

    @Test
    void invalidLoginBodyReturnsValidationProblem() throws Exception {
        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"not-an-email\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[*].field", hasItem("email")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("password")));
    }

    @Test
    void meReturnsAuthenticatedUser() throws Exception {
        User user = createUser(Role.ADMIN, true);
        String token = tokenFor(user);

        MvcResult result =
                mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.id").value(user.getId()))
                        .andExpect(jsonPath("$.name").value("Auth Test"))
                        .andExpect(jsonPath("$.email").value(user.getEmail()))
                        .andExpect(jsonPath("$.role").value("ADMIN"))
                        .andExpect(jsonPath("$.active").value(true))
                        .andExpect(jsonPath("$.createdAt").isNotEmpty())
                        .andReturn();

        assertNoSecrets(result, user);
        assertThat(objectMapper.readTree(result.getResponse().getContentAsString()).size())
                .isEqualTo(6);
    }

    @Test
    void meWithoutTokenReturns401Problem() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value("Authentication failed"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.instance").value("/api/me"));
    }

    @Test
    void meWithTamperedTokenReturns401() throws Exception {
        String token = tokenFor(createUser(Role.RECRUITER, true));
        String tampered = token.substring(0, token.length() - 3) + "abc";

        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meWithExpiredTokenReturns401() throws Exception {
        User user = createUser(Role.RECRUITER, true);
        String expired =
                new JwtService(new JwtProperties(jwtProperties.secret(), Duration.ofMinutes(-5)))
                        .generateToken(user);

        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenOfDeactivatedUserReturns401() throws Exception {
        User user = createUser(Role.RECRUITER, true);
        String token = tokenFor(user);
        user.setActive(false);
        userRepository.saveAndFlush(user);

        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }
}
