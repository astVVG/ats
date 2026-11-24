package io.github.astvvg.ats.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Not transactional: every request commits on its own connection, as concurrent clients do. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TestUsers.class})
class ApplicationMoveConcurrencyIntegrationTest {

    // Below the connection pool size, so requests wait on the job lock and not on the pool.
    private static final int THREADS = 6;

    @Autowired private MockMvc mockMvc;
    @Autowired private TestUsers testUsers;
    @Autowired private JobRepository jobRepository;
    @Autowired private CandidateRepository candidateRepository;
    @Autowired private ApplicationRepository applicationRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Job job;
    private final List<String> tokens = new ArrayList<>();

    @BeforeEach
    void setUp() {
        User creator = testUsers.create(Role.RECRUITER);
        job = jobRepository.saveAndFlush(new Job("Concurrent board", null, null, null, creator));
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
                            new Candidate("Cand", TestUsers.uniqueEmail("concurrent"), null, null));
            cards.add(
                    applicationRepository.saveAndFlush(new Application(job, candidate, stage, i)));
        }
        return cards;
    }

    private record Move(Application application, Stage stage, int position) {}

    /** Starts every move at the same time, each with its own token, and returns the statuses. */
    private List<Integer> runConcurrently(List<Move> moves) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(moves.size());
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < moves.size(); i++) {
                Move move = moves.get(i);
                String auth = tokens.get(i % tokens.size());
                Callable<Integer> task =
                        () -> {
                            start.await();
                            return mockMvc.perform(
                                            patch(
                                                            "/api/applications/"
                                                                    + move.application().getId()
                                                                    + "/move")
                                                    .header(HttpHeaders.AUTHORIZATION, auth)
                                                    .contentType(MediaType.APPLICATION_JSON)
                                                    .content(
                                                            "{\"stage\":\""
                                                                    + move.stage()
                                                                    + "\",\"position\":"
                                                                    + move.position()
                                                                    + "}"))
                                    .andReturn()
                                    .getResponse()
                                    .getStatus();
                        };
                results.add(executor.submit(task));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get(30, TimeUnit.SECONDS));
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

    private int total(Map<Stage, Integer> sizes) {
        return sizes.values().stream().mapToInt(Integer::intValue).sum();
    }

    @Test
    void manyApplicationsMovedIntoTheSameColumnAtOnce() throws Exception {
        List<Application> applied = column(Stage.APPLIED, THREADS);
        column(Stage.INTERVIEW, 2);
        List<Move> moves = new ArrayList<>();
        for (Application application : applied) {
            moves.add(new Move(application, Stage.INTERVIEW, 0));
        }

        assertThat(runConcurrently(moves)).containsOnly(200);

        Map<Stage, Integer> sizes = assertContiguousColumns();
        assertThat(sizes.get(Stage.APPLIED)).isZero();
        assertThat(sizes.get(Stage.INTERVIEW)).isEqualTo(THREADS + 2);
        assertThat(total(sizes)).isEqualTo(THREADS + 2);
    }

    @Test
    void manyReordersInsideTheSameColumnAtOnce() throws Exception {
        List<Application> applied = column(Stage.APPLIED, THREADS);
        List<Move> moves = new ArrayList<>();
        for (int i = 0; i < applied.size(); i++) {
            moves.add(new Move(applied.get(i), Stage.APPLIED, i % 2 == 0 ? 0 : THREADS - 1));
        }

        assertThat(runConcurrently(moves)).containsOnly(200);

        Map<Stage, Integer> sizes = assertContiguousColumns();
        assertThat(sizes.get(Stage.APPLIED)).isEqualTo(THREADS);
        assertThat(total(sizes)).isEqualTo(THREADS);
    }

    @Test
    void theSameApplicationMovedTwiceAtOnce() throws Exception {
        column(Stage.APPLIED, 3);
        Application target = column(Stage.INTERVIEW, 2).get(1);
        column(Stage.OFFER, 2);

        List<Integer> statuses =
                runConcurrently(
                        List.of(
                                new Move(target, Stage.OFFER, 0),
                                new Move(target, Stage.APPLIED, 1)));

        assertThat(statuses).containsOnly(200);
        Map<Stage, Integer> sizes = assertContiguousColumns();
        assertThat(total(sizes)).isEqualTo(7);
        assertThat(sizes.get(Stage.INTERVIEW)).isEqualTo(1);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT count(*) FROM stage_history WHERE application_id = ?",
                                Integer.class,
                                target.getId()))
                .isEqualTo(2);
    }

    @Test
    void crossedMovesBetweenTwoColumnsAtOnce() throws Exception {
        List<Application> applied = column(Stage.APPLIED, THREADS / 2 + 1);
        List<Application> interview = column(Stage.INTERVIEW, THREADS / 2 + 1);
        List<Move> moves = new ArrayList<>();
        for (int i = 0; i < THREADS / 2; i++) {
            moves.add(new Move(applied.get(i), Stage.INTERVIEW, 0));
            moves.add(new Move(interview.get(i), Stage.APPLIED, 0));
        }

        assertThat(runConcurrently(moves)).containsOnly(200);

        Map<Stage, Integer> sizes = assertContiguousColumns();
        assertThat(sizes.get(Stage.APPLIED)).isEqualTo(THREADS / 2 + 1);
        assertThat(sizes.get(Stage.INTERVIEW)).isEqualTo(THREADS / 2 + 1);
        assertThat(total(sizes)).isEqualTo(THREADS + 2);
    }
}
