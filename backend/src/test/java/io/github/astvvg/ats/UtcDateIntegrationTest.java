package io.github.astvvg.ats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.astvvg.ats.support.TestUsers;
import io.github.astvvg.ats.support.TestcontainersConfiguration;
import io.github.astvvg.ats.user.domain.Role;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.TimeZone;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.transaction.annotation.Transactional;

/** Dates are stored as UTC and read back unchanged, whatever the zone of the JVM. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TestUsers.class})
@Transactional
class UtcDateIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TestUsers testUsers;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;

    private TimeZone original;

    @BeforeEach
    void leaveUtc() {
        original = TimeZone.getDefault();
        // An offset with minutes, unlike both UTC and the zone of the test database server.
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
    }

    @AfterEach
    void restoreZone() {
        TimeZone.setDefault(original);
    }

    /** The column exactly as stored, without any driver conversion. */
    private LocalDateTime storedCreatedAt(long candidateId) {
        String text =
                jdbcTemplate.queryForObject(
                        "SELECT CAST(created_at AS CHAR) FROM candidates WHERE id = ?",
                        String.class,
                        candidateId);
        return LocalDateTime.parse(text.replace(' ', 'T'));
    }

    @Test
    void createdAtRoundTripsAndIsStoredInUtc() throws Exception {
        String auth = testUsers.bearer(testUsers.create(Role.RECRUITER));
        String request =
                objectMapper.writeValueAsString(
                        Map.of("fullName", "Zone", "email", TestUsers.uniqueEmail("zone")));
        Instant before = Instant.now().minusSeconds(1);

        String created =
                mockMvc.perform(
                                post("/api/candidates")
                                        .header(HttpHeaders.AUTHORIZATION, auth)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(request))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        JsonNode body = objectMapper.readTree(created);
        long id = body.get("id").asLong();
        Instant createdAt = Instant.parse(body.get("createdAt").asText());
        entityManager.flush();
        entityManager.clear();
        String read =
                mockMvc.perform(
                                get("/api/candidates/" + id)
                                        .header(HttpHeaders.AUTHORIZATION, auth))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        assertThat(createdAt).isBetween(before, Instant.now().plusSeconds(1));
        assertThat(Instant.parse(objectMapper.readTree(read).get("createdAt").asText()))
                .isEqualTo(createdAt);
        assertThat(storedCreatedAt(id))
                .isEqualTo(LocalDateTime.ofInstant(createdAt, ZoneOffset.UTC));
    }

    @Test
    void serverDefaultsAreUtcToo() {
        String email = TestUsers.uniqueEmail("zonedefault");
        jdbcTemplate.update("INSERT INTO candidates (full_name, email) VALUES ('Zone', ?)", email);
        long id =
                jdbcTemplate.queryForObject(
                        "SELECT id FROM candidates WHERE email = ?", Long.class, email);

        assertThat(storedCreatedAt(id))
                .isCloseTo(LocalDateTime.now(ZoneOffset.UTC), within(1, ChronoUnit.MINUTES));
    }
}
