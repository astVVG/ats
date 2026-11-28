package io.github.astvvg.ats.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.astvvg.ats.support.TestUsers;
import io.github.astvvg.ats.support.TestcontainersConfiguration;
import io.github.astvvg.ats.user.domain.Role;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TestUsers.class})
@Transactional
class ValidationIntegrationTest {

    private static final String UNREADABLE = "Request body is empty or is not valid JSON";
    private static final String BLANK = "no debe estar vacío";
    private static final String REQUIRED = "es obligatorio";
    private static final String INVALID = "tiene un valor inválido";
    private static final String INTEGER = "debe ser un número entero válido";
    private static final String DATE = "debe ser una fecha válida con formato AAAA-MM-DD";
    private static final String STAGES =
            "debe ser uno de: APPLIED, INTERVIEW, OFFER, HIRED, REJECTED";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TestUsers testUsers;

    private String adminAuth;

    private record Reply(int status, JsonNode body) {

        List<String> errors() {
            List<String> errors = new ArrayList<>();
            body.path("errors")
                    .forEach(e ->
                            errors.add(
                                    e.path("field").asText() + ": " + e.path("message").asText()));
            return errors;
        }
    }

    @BeforeEach
    void setUp() {
        adminAuth = testUsers.bearer(testUsers.create(Role.ADMIN));
    }

    private Reply send(String method, String path, String body, String acceptLanguage)
            throws Exception {
        var builder =
                request(HttpMethod.valueOf(method), URI.create(path))
                        .header(HttpHeaders.AUTHORIZATION, adminAuth)
                        .contentType(MediaType.APPLICATION_JSON);
        if (body != null) {
            builder.content(body);
        }
        if (acceptLanguage != null) {
            builder.header(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguage);
        }
        var response = mockMvc.perform(builder).andReturn().getResponse();
        return new Reply(
                response.getStatus(),
                objectMapper.readTree(response.getContentAsString(StandardCharsets.UTF_8)));
    }

    private Reply send(String method, String path, String body) throws Exception {
        return send(method, path, body, null);
    }

    private static void assertValidationFailed(Reply reply, String... errors) {
        assertThat(reply.status()).isEqualTo(400);
        assertThat(reply.body().path("title").asText()).isEqualTo("Validation failed");
        assertThat(reply.body().path("detail").asText())
                .isEqualTo("One or more fields are invalid");
        assertThat(reply.errors()).containsExactlyInAnyOrder(errors);
    }

    @ParameterizedTest
    @CsvSource({"en,en", "es,en", "fr,en", ",en", "en,es", "es,es", ",fr"})
    void messagesDoNotDependOnAcceptLanguageOrJvmLocale(String acceptLanguage, String jvmLocale)
            throws Exception {
        Locale original = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag(jvmLocale));
        try {
            assertValidationFailed(
                    send("POST", "/api/jobs", "{\"title\":\"\"}", acceptLanguage),
                    "title: " + BLANK);
            assertValidationFailed(
                    send(
                            "POST",
                            "/api/users",
                            "{\"name\":\"n\",\"email\":\"x\",\"password\":\"short\"}",
                            acceptLanguage),
                    "email: debe ser un correo electrónico válido",
                    "password: debe tener entre 8 y 72 caracteres",
                    "role: " + REQUIRED);
            assertValidationFailed(
                    send(
                            "GET",
                            "/api/metrics/funnel?from=2026-02-02&to=2026-02-01&jobId=999999",
                            null,
                            acceptLanguage),
                    "from: no debe ser posterior a to");
            assertThat(send("GET", "/api/jobs/abc", null, acceptLanguage).errors())
                    .containsExactly("id: " + INTEGER);
        } finally {
            Locale.setDefault(original);
        }
    }

    private static Stream<Arguments> emptyObjectBodies() {
        return Stream.of(
                Arguments.of("POST", "/api/auth/login", List.of("email", "password"), BLANK),
                Arguments.of("POST", "/api/jobs", List.of("title"), BLANK),
                Arguments.of("PUT", "/api/jobs/999999", List.of("title"), BLANK),
                Arguments.of("PATCH", "/api/jobs/999999/status", List.of("status"), REQUIRED),
                Arguments.of("POST", "/api/candidates", List.of("fullName", "email"), BLANK),
                Arguments.of("PUT", "/api/candidates/999999", List.of("fullName", "email"), BLANK),
                Arguments.of(
                        "POST", "/api/applications", List.of("jobId", "candidateId"), REQUIRED),
                Arguments.of(
                        "PATCH",
                        "/api/applications/999999/move",
                        List.of("stage", "position"),
                        REQUIRED),
                Arguments.of("POST", "/api/applications/999999/notes", List.of("content"), BLANK),
                Arguments.of("PATCH", "/api/users/999999", List.of("active"), REQUIRED));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("emptyObjectBodies")
    void everyBodyEndpointValidatesItsRequest(
            String method, String path, List<String> fields, String message) throws Exception {
        assertValidationFailed(
                send(method, path, "{}"),
                fields.stream().map(f -> f + ": " + message).toArray(String[]::new));
    }

    @Test
    void emptyRatingBodyStaysValid() throws Exception {
        assertThat(send("PATCH", "/api/applications/999999/rating", "{}").status()).isEqualTo(404);
    }

    private static Stream<Arguments> wrongBodyValues() {
        String move = "/api/applications/999999/move";
        return Stream.of(
                Arguments.of("PATCH", move, "{\"stage\":\"X\"}", "stage: " + STAGES),
                Arguments.of("PATCH", move, "{\"stage\":99}", "stage: " + STAGES),
                Arguments.of("PATCH", move, "{\"stage\":{}}", "stage: " + STAGES),
                Arguments.of("PATCH", move, "{\"position\":\"x\"}", "position: " + INVALID),
                Arguments.of("PATCH", move, "{\"position\":99999999999}", "position: " + INVALID),
                Arguments.of(
                        "POST",
                        "/api/applications",
                        "{\"jobId\":99999999999999999999}",
                        "jobId: " + INVALID),
                Arguments.of("POST", "/api/applications", "{\"jobId\":[]}", "jobId: " + INTEGER),
                Arguments.of("POST", "/api/jobs", "{\"title\":{}}", "title: " + INVALID),
                Arguments.of(
                        "PATCH",
                        "/api/jobs/999999/status",
                        "{\"status\":\"\"}",
                        "status: debe ser uno de: OPEN, CLOSED"),
                Arguments.of(
                        "POST",
                        "/api/users",
                        "{\"role\":\"BOSS\"}",
                        "role: debe ser uno de: ADMIN, RECRUITER"),
                Arguments.of(
                        "PATCH", "/api/users/999999", "{\"active\":\"yes\"}", "active: " + INVALID),
                Arguments.of(
                        "PATCH",
                        "/api/applications/999999/rating",
                        "{\"rating\":3.5}",
                        "rating: " + INVALID),
                Arguments.of(
                        "PATCH",
                        "/api/applications/999999/rating",
                        "{\"rating\":99999999999}",
                        "rating: " + INVALID));
    }

    @ParameterizedTest(name = "{1} {2}")
    @MethodSource("wrongBodyValues")
    void wrongBodyValueIsReportedOnItsField(String method, String path, String body, String error)
            throws Exception {
        assertValidationFailed(send(method, path, body), error);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "null",
                "{",
                "{\"title\":",
                "{\"title\":\"ab",
                "{\"title\":\"a\",",
                "{title}",
                "not json"
            })
    void emptyOrBrokenJsonHasNoFieldErrors(String body) throws Exception {
        Reply reply = send("POST", "/api/jobs", body);

        assertThat(reply.status()).isEqualTo(400);
        assertThat(reply.body().path("title").asText()).isEqualTo("Bad Request");
        assertThat(reply.body().path("detail").asText()).isEqualTo(UNREADABLE);
        assertThat(reply.body().has("errors")).isFalse();
    }

    @Test
    void brokenNumberInsideAFieldIsStillBrokenJson() throws Exception {
        Reply reply = send("POST", "/api/applications", "{\"jobId\":1x}");

        assertThat(reply.body().path("detail").asText()).isEqualTo(UNREADABLE);
        assertThat(reply.body().has("errors")).isFalse();
    }

    @Test
    void validJsonOfTheWrongShapeKeepsTheGenericDetail() throws Exception {
        Reply reply = send("POST", "/api/jobs", "[]");

        assertThat(reply.status()).isEqualTo(400);
        assertThat(reply.body().path("detail").asText()).isEqualTo("Failed to read request");
        assertThat(reply.body().has("errors")).isFalse();
    }

    private static Stream<Arguments> badParameters() {
        return Stream.of(
                Arguments.of("GET", "/api/jobs/abc", "id", "abc", INTEGER),
                Arguments.of("GET", "/api/jobs/1.5", "id", "1.5", INTEGER),
                Arguments.of(
                        "GET",
                        "/api/jobs/99999999999999999999",
                        "id",
                        "99999999999999999999",
                        INTEGER),
                Arguments.of("GET", "/api/jobs/abc/board", "id", "abc", INTEGER),
                Arguments.of("GET", "/api/candidates/abc/resume", "id", "abc", INTEGER),
                Arguments.of("GET", "/api/applications/abc/history", "id", "abc", INTEGER),
                Arguments.of("GET", "/api/applications/abc/notes", "id", "abc", INTEGER),
                Arguments.of("DELETE", "/api/notes/abc", "id", "abc", INTEGER),
                Arguments.of("DELETE", "/api/candidates/abc", "id", "abc", INTEGER),
                Arguments.of(
                        "GET",
                        "/api/jobs?status=nope",
                        "status",
                        "nope",
                        "debe ser uno de: OPEN, CLOSED"),
                Arguments.of("GET", "/api/metrics/funnel?jobId=x", "jobId", "x", INTEGER),
                Arguments.of(
                        "GET",
                        "/api/metrics/time-in-stage?from=2026-13-01",
                        "from",
                        "2026-13-01",
                        DATE),
                Arguments.of("GET", "/api/metrics/time-to-hire?to=hoy", "to", "hoy", DATE));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("badParameters")
    void badPathOrQueryValueIsReportedOnTheParameter(
            String method, String path, String field, String value, String message)
            throws Exception {
        Reply reply = send(method, path, null);

        assertThat(reply.status()).isEqualTo(400);
        assertThat(reply.body().path("title").asText()).isEqualTo("Bad Request");
        assertThat(reply.body().path("detail").asText())
                .isEqualTo("Failed to convert '" + field + "' with value: '" + value + "'");
        assertThat(reply.errors()).containsExactly(field + ": " + message);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/jobs", "/api/candidates", "/api/users"})
    void unreachablePageIsABadRequest(String path) throws Exception {
        assertValidationFailed(
                send("GET", path + "?page=2000000000&size=100", null), "page: está fuera de rango");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/jobs", "/api/candidates", "/api/users"})
    void unsupportedSortKeepsItsDetailAndNamesTheParameter(String path) throws Exception {
        Reply reply = send("GET", path + "?sort=nope", null);

        assertThat(reply.status()).isEqualTo(400);
        assertThat(reply.body().path("title").asText()).isEqualTo("Bad Request");
        assertThat(reply.body().path("detail").asText())
                .isEqualTo("Unsupported sort property: nope");
        assertThat(reply.errors()).containsExactly("sort: propiedad de ordenamiento no admitida");
    }
}
