package io.github.astvvg.ats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.astvvg.ats.support.TestcontainersConfiguration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Response schemas must list every non-null field as required, so generated clients agree. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpenApiSchemaTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private JsonNode schemas;

    @BeforeAll
    void loadSchemas() throws Exception {
        String json =
                mockMvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString();
        schemas = objectMapper.readTree(json).path("components").path("schemas");
    }

    static Stream<Arguments> responseSchemas() {
        Set<String> page = Set.of("content", "page", "size", "totalElements", "totalPages");
        return Stream.of(
                Arguments.of(
                        "UserResponse",
                        Set.of("id", "name", "email", "role", "active", "createdAt"),
                        Set.of()),
                Arguments.of(
                        "LoginResponse", Set.of("accessToken", "tokenType", "expiresIn"), Set.of()),
                Arguments.of("PageResponseUserResponse", page, Set.of()),
                Arguments.of(
                        "JobResponse",
                        Set.of("id", "title", "status", "createdBy", "createdAt", "updatedAt"),
                        Set.of("department", "location", "description")),
                Arguments.of("JobCreatorResponse", Set.of("id", "name"), Set.of()),
                Arguments.of("PageResponseJobResponse", page, Set.of()),
                Arguments.of(
                        "CandidateResponse",
                        Set.of("id", "fullName", "email", "hasResume", "createdAt", "updatedAt"),
                        Set.of("phone", "source")),
                Arguments.of("PageResponseCandidateResponse", page, Set.of()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("responseSchemas")
    void responseSchemaMarksNonNullFieldsAsRequired(
            String name, Set<String> required, Set<String> nullable) {
        List<String> available = new ArrayList<>();
        schemas.fieldNames().forEachRemaining(available::add);
        JsonNode schema = schemas.path(name);
        assertThat(schema.isMissingNode())
                .as("schema %s not found; available: %s", name, available)
                .isFalse();

        Set<String> properties = new HashSet<>();
        schema.path("properties").fieldNames().forEachRemaining(properties::add);
        Set<String> actualRequired = new HashSet<>();
        schema.path("required").forEach(field -> actualRequired.add(field.asText()));

        assertThat(properties).as("%s properties", name).isEqualTo(union(required, nullable));
        assertThat(actualRequired).as("%s required", name).isEqualTo(required);
        for (String field : nullable) {
            assertThat(isNullable(schema.path("properties").path(field)))
                    .as("%s.%s nullable", name, field)
                    .isTrue();
        }
    }

    // springdoc publishes OpenAPI 3.1, where a nullable property lists "null" among its types.
    private static boolean isNullable(JsonNode property) {
        for (JsonNode type : property.path("type")) {
            if ("null".equals(type.asText())) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        Set<String> all = new HashSet<>(a);
        all.addAll(b);
        return all;
    }
}
