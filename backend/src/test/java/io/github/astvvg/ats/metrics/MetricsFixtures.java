package io.github.astvvg.ats.metrics;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import io.github.astvvg.ats.application.domain.Stage;
import io.github.astvvg.ats.support.TestUsers;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Inserts applications and their stage history with JDBC so created_at and moved_at are exact.
 * Meant for transactional tests: everything it writes is rolled back with the test.
 */
final class MetricsFixtures {

    /** An application being built; {@code stage} is where its last history row left it. */
    static final class Card {
        final long id;
        Stage stage;

        Card(long id, Stage stage) {
            this.id = id;
            this.stage = stage;
        }
    }

    private final JdbcTemplate jdbcTemplate;
    private final long userId;

    MetricsFixtures(JdbcTemplate jdbcTemplate, long userId) {
        this.jdbcTemplate = jdbcTemplate;
        this.userId = userId;
    }

    long job(String status) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO jobs (title, status, created_by) VALUES ('Metrics', ?, ?) RETURNING id",
                Long.class,
                status,
                userId);
    }

    /** Creates an application in APPLIED at {@code createdAt}, with its initial history row. */
    Card apply(long jobId, String createdAt) {
        return apply(jobId, createdAt, createdAt);
    }

    /** Same, with an initial moved_at that may differ slightly from created_at. */
    Card apply(long jobId, String createdAt, String initialMovedAt) {
        long candidateId =
                jdbcTemplate.queryForObject(
                        "INSERT INTO candidates (full_name, email) VALUES ('Cand', ?) RETURNING id",
                        Long.class,
                        TestUsers.uniqueEmail("metrics"));
        Timestamp created = at(createdAt);
        long id =
                jdbcTemplate.queryForObject(
                        "INSERT INTO applications"
                                + " (job_id, candidate_id, stage, position, created_at, updated_at)"
                                + " VALUES (?, ?, 'APPLIED', 0, ?, ?) RETURNING id",
                        Long.class,
                        jobId,
                        candidateId,
                        created,
                        created);
        Card card = new Card(id, null);
        history(card, Stage.APPLIED, initialMovedAt);
        return card;
    }

    /** Records a stage change of {@code card} at {@code movedAt}. */
    Card move(Card card, Stage to, String movedAt) {
        history(card, to, movedAt);
        jdbcTemplate.update("UPDATE applications SET stage = ? WHERE id = ?", to.name(), card.id);
        return card;
    }

    private void history(Card card, Stage to, String movedAt) {
        jdbcTemplate.update(
                "INSERT INTO stage_history (application_id, from_stage, to_stage, moved_by, moved_at)"
                        + " VALUES (?, ?, ?, ?, ?)",
                card.id,
                card.stage == null ? null : card.stage.name(),
                to.name(),
                userId,
                at(movedAt));
        card.stage = to;
    }

    /**
     * The reference data set: A, B and C go through the pipeline in January 2026 and D stays in
     * APPLIED from February.
     */
    void reference(long jobId) {
        Card a = apply(jobId, "2026-01-10T12:00:00Z");
        move(a, Stage.INTERVIEW, "2026-01-11T12:00:00Z");
        move(a, Stage.OFFER, "2026-01-13T12:00:00Z");
        move(a, Stage.HIRED, "2026-01-14T12:00:00Z");

        Card b = apply(jobId, "2026-01-15T12:00:00Z");
        move(b, Stage.INTERVIEW, "2026-01-17T12:00:00Z");
        move(b, Stage.REJECTED, "2026-01-18T12:00:00Z");

        Card c = apply(jobId, "2026-01-20T12:00:00Z");
        move(c, Stage.OFFER, "2026-01-20T18:00:00Z");
        move(c, Stage.HIRED, "2026-01-22T12:00:00Z");

        apply(jobId, "2026-02-05T12:00:00Z");
    }

    private static Timestamp at(String instant) {
        return Timestamp.from(Instant.parse(instant));
    }

    /** GET on a metrics endpoint with a fresh persistence context, as in production. */
    static ResultActions metrics(
            MockMvc mockMvc, EntityManager entityManager, String path, String query, String auth)
            throws Exception {
        entityManager.flush();
        entityManager.clear();
        var request = get("/api/metrics/" + path + (query == null ? "" : query));
        if (auth != null) {
            request.header(HttpHeaders.AUTHORIZATION, auth);
        }
        return mockMvc.perform(request);
    }

    /** Reads decimals exactly as written, so 26.00 keeps its scale instead of becoming 26.0. */
    static JsonNode json(ObjectMapper objectMapper, ResultActions result) throws Exception {
        return objectMapper
                .reader()
                .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .without(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
                .readTree(result.andReturn().getResponse().getContentAsString());
    }
}
