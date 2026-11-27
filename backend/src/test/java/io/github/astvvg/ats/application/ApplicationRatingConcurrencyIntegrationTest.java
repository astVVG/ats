package io.github.astvvg.ats.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.astvvg.ats.application.domain.Application;
import io.github.astvvg.ats.application.domain.Stage;
import io.github.astvvg.ats.application.repository.ApplicationRepository;
import io.github.astvvg.ats.candidate.domain.Candidate;
import io.github.astvvg.ats.candidate.repository.CandidateRepository;
import io.github.astvvg.ats.job.domain.Job;
import io.github.astvvg.ats.job.repository.JobRepository;
import io.github.astvvg.ats.support.TestUsers;
import io.github.astvvg.ats.support.TestcontainersConfiguration;
import io.github.astvvg.ats.user.domain.Role;
import io.github.astvvg.ats.user.domain.User;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Not transactional: every request commits on its own connection, as concurrent clients do. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TestUsers.class})
class ApplicationRatingConcurrencyIntegrationTest {

    // Below the connection pool size, so requests wait on the job lock and not on the pool.
    private static final int THREADS = 6;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TestUsers testUsers;
    @Autowired private JobRepository jobRepository;
    @Autowired private CandidateRepository candidateRepository;
    @Autowired private ApplicationRepository applicationRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    private Job job;
    private final List<String> tokens = new ArrayList<>();

    @BeforeEach
    void setUp() {
        User creator = testUsers.create(Role.RECRUITER);
        job = jobRepository.saveAndFlush(new Job("Concurrent rating", null, null, null, creator));
        tokens.clear();
        for (int i = 0; i < THREADS; i++) {
            tokens.add(
                    testUsers.bearer(testUsers.create(i % 2 == 0 ? Role.RECRUITER : Role.ADMIN)));
        }
    }

    private List<Application> column(Stage stage, int size) {
        List<Application> cards = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            Candidate candidate =
                    candidateRepository.saveAndFlush(
                            new Candidate("Cand", TestUsers.uniqueEmail("rateconc"), null, null));
            cards.add(
                    applicationRepository.saveAndFlush(new Application(job, candidate, stage, i)));
        }
        return cards;
    }

    private record Request(String path, String body) {}

    private static Request move(Application application, Stage stage, int position) {
        return new Request(
                "/api/applications/" + application.getId() + "/move",
                "{\"stage\":\"" + stage + "\",\"position\":" + position + "}");
    }

    private static Request rate(Application application, int rating) {
        return new Request(
                "/api/applications/" + application.getId() + "/rating",
                "{\"rating\":" + rating + "}");
    }

    private Callable<MockHttpServletResponse> call(Request request, String auth) {
        return () ->
                mockMvc.perform(
                                patch(request.path())
                                        .header(HttpHeaders.AUTHORIZATION, auth)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(request.body()))
                        .andReturn()
                        .getResponse();
    }

    /** Starts every request at the same time, each with its own token, and returns the statuses. */
    private List<Integer> runConcurrently(List<Request> requests) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(requests.size());
        try {
            List<Future<MockHttpServletResponse>> results = new ArrayList<>();
            for (int i = 0; i < requests.size(); i++) {
                Callable<MockHttpServletResponse> task =
                        call(requests.get(i), tokens.get(i % tokens.size()));
                results.add(
                        executor.submit(() -> {
                            start.await();
                            return task.call();
                        }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<MockHttpServletResponse> result : results) {
                statuses.add(result.get(30, TimeUnit.SECONDS).getStatus());
            }
            return statuses;
        } finally {
            executor.shutdownNow();
        }
    }

    /** Asserts every column is a permutation of 0..n-1 and returns the size of each one. */
    private Map<Stage, Integer> assertContiguousColumns() {
        Map<Stage, Integer> sizes = new EnumMap<>(Stage.class);
        for (Stage stage : Stage.values()) {
            List<Integer> positions =
                    jdbcTemplate.queryForList(
                            "SELECT position FROM applications WHERE job_id = ? AND stage = ?"
                                    + " ORDER BY position",
                            Integer.class,
                            job.getId(),
                            stage.name());
            assertThat(positions)
                    .as("positions of %s", stage)
                    .isEqualTo(IntStream.range(0, positions.size()).boxed().toList());
            sizes.put(stage, positions.size());
        }
        return sizes;
    }

    private Map<String, Object> row(Application application) {
        return jdbcTemplate.queryForMap(
                "SELECT stage, position, rating FROM applications WHERE id = ?",
                application.getId());
    }

    private int historyCount(Application application) {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM stage_history WHERE application_id = ?",
                        Integer.class,
                        application.getId());
        return count == null ? 0 : count;
    }

    @Test
    void ratingAndMovingTheSameCardAtOnceKeepsBothChanges() throws Exception {
        List<Application> applied = column(Stage.APPLIED, 4);
        column(Stage.OFFER, 2);
        List<Request> requests = new ArrayList<>();
        for (int i = 0; i < THREADS / 2; i++) {
            requests.add(move(applied.get(i), Stage.OFFER, 0));
            requests.add(rate(applied.get(i), i + 2));
        }

        assertThat(runConcurrently(requests)).containsOnly(200);

        Map<Stage, Integer> sizes = assertContiguousColumns();
        assertThat(sizes.get(Stage.APPLIED)).isEqualTo(1);
        assertThat(sizes.get(Stage.OFFER)).isEqualTo(2 + THREADS / 2);
        for (int i = 0; i < THREADS / 2; i++) {
            Map<String, Object> row = row(applied.get(i));
            assertThat(row.get("stage")).isEqualTo("OFFER");
            assertThat(row.get("rating")).isEqualTo(i + 2);
            assertThat(historyCount(applied.get(i))).isEqualTo(1);
        }
        Map<String, Object> untouched = row(applied.get(3));
        assertThat(untouched.get("stage")).isEqualTo("APPLIED");
        assertThat(untouched.get("position")).isEqualTo(0);
        assertThat(untouched.get("rating")).isNull();
    }

    @Test
    void ratingCardsShiftedByAReindexKeepsTheirNewPositions() throws Exception {
        List<Application> applied = column(Stage.APPLIED, THREADS / 2);
        List<Application> interview = column(Stage.INTERVIEW, THREADS / 2 + 1);
        List<Request> requests = new ArrayList<>();
        for (int i = 0; i < THREADS / 2; i++) {
            // Each insertion at 0 shifts every card already in INTERVIEW down one place.
            requests.add(move(applied.get(i), Stage.INTERVIEW, 0));
            requests.add(rate(interview.get(i), i + 1));
        }

        assertThat(runConcurrently(requests)).containsOnly(200);

        Map<Stage, Integer> sizes = assertContiguousColumns();
        assertThat(sizes.get(Stage.APPLIED)).isZero();
        assertThat(sizes.get(Stage.INTERVIEW)).isEqualTo(THREADS + 1);
        for (int i = 0; i < interview.size(); i++) {
            Map<String, Object> row = row(interview.get(i));
            assertThat(row.get("stage")).isEqualTo("INTERVIEW");
            // The moved cards all went above them, so the original cards keep their order.
            assertThat(row.get("position")).as("position of card %d", i).isEqualTo(THREADS / 2 + i);
            assertThat(row.get("rating"))
                    .as("rating of card %d", i)
                    .isEqualTo(i < THREADS / 2 ? (Object) (i + 1) : null);
            assertThat(historyCount(interview.get(i))).isZero();
        }
    }

    @Test
    void ratingWaitsForTheJobLockAndReadsTheApplicationAfterIt() throws Exception {
        Application card = column(Stage.APPLIED, 1).getFirst();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            Future<MockHttpServletResponse> rating =
                    transaction.execute(status -> {
                        // Hold the job lock as a move would while it rewrites the board.
                        jdbcTemplate.queryForObject(
                                "SELECT id FROM jobs WHERE id = ? FOR UPDATE",
                                Long.class,
                                job.getId());
                        Future<MockHttpServletResponse> pending =
                                executor.submit(call(rate(card, 4), tokens.getFirst()));
                        assertThatThrownBy(() -> pending.get(500, TimeUnit.MILLISECONDS))
                                .isInstanceOf(TimeoutException.class);
                        jdbcTemplate.update(
                                "UPDATE applications SET stage = 'OFFER', position = 0"
                                        + " WHERE id = ?",
                                card.getId());
                        return pending;
                    });

            MockHttpServletResponse response = rating.get(30, TimeUnit.SECONDS);

            assertThat(response.getStatus()).isEqualTo(200);
            JsonNode body = objectMapper.readTree(response.getContentAsString());
            assertThat(body.get("stage").asText()).isEqualTo("OFFER");
            assertThat(body.get("position").asInt()).isZero();
            assertThat(body.get("rating").asInt()).isEqualTo(4);
            Map<String, Object> row = row(card);
            assertThat(row.get("stage")).isEqualTo("OFFER");
            assertThat(row.get("position")).isEqualTo(0);
            assertThat(row.get("rating")).isEqualTo(4);
        } finally {
            executor.shutdownNow();
        }
    }
}
