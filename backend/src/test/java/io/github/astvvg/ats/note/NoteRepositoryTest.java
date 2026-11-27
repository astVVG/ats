package io.github.astvvg.ats.note;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.astvvg.ats.application.domain.Application;
import io.github.astvvg.ats.application.domain.Stage;
import io.github.astvvg.ats.application.repository.ApplicationRepository;
import io.github.astvvg.ats.candidate.domain.Candidate;
import io.github.astvvg.ats.candidate.repository.CandidateRepository;
import io.github.astvvg.ats.job.domain.Job;
import io.github.astvvg.ats.job.repository.JobRepository;
import io.github.astvvg.ats.note.domain.Note;
import io.github.astvvg.ats.note.repository.NoteRepository;
import io.github.astvvg.ats.support.TestcontainersConfiguration;
import io.github.astvvg.ats.user.domain.Role;
import io.github.astvvg.ats.user.domain.User;
import io.github.astvvg.ats.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class NoteRepositoryTest {

    @Autowired private NoteRepository noteRepository;
    @Autowired private ApplicationRepository applicationRepository;
    @Autowired private JobRepository jobRepository;
    @Autowired private CandidateRepository candidateRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;
    @Autowired private DataSource dataSource;

    private User author;

    @BeforeEach
    void setUp() {
        author = user("Author");
    }

    private User user(String name) {
        return userRepository.saveAndFlush(
                new User(
                        name,
                        "noterepo-" + UUID.randomUUID() + "@example.com",
                        "hash",
                        Role.RECRUITER));
    }

    private Application application() {
        Job job = jobRepository.saveAndFlush(new Job("Job", null, null, null, author));
        Candidate candidate =
                candidateRepository.saveAndFlush(
                        new Candidate(
                                "Cand",
                                "noterepo-" + UUID.randomUUID() + "@example.com",
                                null,
                                null));
        return applicationRepository.saveAndFlush(
                new Application(job, candidate, Stage.APPLIED, 0));
    }

    @Test
    void savesNoteWithCreationTime() {
        Note saved = noteRepository.saveAndFlush(new Note(application(), author, "First call"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getContent()).isEqualTo("First call");
    }

    @Test
    void findByApplicationIdPagesOnlyThatApplicationAndLoadsAuthors() {
        Application application = application();
        Application other = application();
        User second = user("Second");
        noteRepository.saveAndFlush(new Note(application, author, "a"));
        noteRepository.saveAndFlush(new Note(application, second, "b"));
        noteRepository.saveAndFlush(new Note(application, author, "c"));
        noteRepository.saveAndFlush(new Note(other, author, "other"));
        entityManager.clear();

        Page<Note> page =
                noteRepository.findByApplicationId(
                        application.getId(),
                        PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "createdAt", "id")));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent()).extracting(Note::getContent).containsExactly("c", "b");
        assertThat(page.getContent())
                .allSatisfy(note -> assertThat(Hibernate.isInitialized(note.getAuthor())).isTrue());
    }

    @Test
    void flywayCreatedNotesTableWithForeignKeysWithoutCascadeAndIndex() {
        assertThat(
                        jdbcTemplate.queryForList(
                                "SELECT constraint_name FROM information_schema.table_constraints"
                                        + " WHERE table_name = 'notes'",
                                String.class))
                .contains("fk_notes_application", "fk_notes_author");
        assertThat(
                        jdbcTemplate.queryForList(
                                "SELECT delete_rule FROM information_schema.referential_constraints"
                                        + " WHERE constraint_name IN"
                                        + " ('fk_notes_application', 'fk_notes_author')",
                                String.class))
                .containsOnly("NO ACTION");
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT count(*) FROM pg_indexes WHERE tablename = 'notes'"
                                        + " AND indexname = 'idx_notes_application_id'",
                                Integer.class))
                .isEqualTo(1);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT character_maximum_length FROM information_schema.columns"
                                        + " WHERE table_name = 'notes' AND column_name = 'content'",
                                Integer.class))
                .isEqualTo(Note.MAX_CONTENT_LENGTH);
    }

    @Test
    void ratingCheckFromV4IsTheOnlyOne() {
        List<String> checks =
                jdbcTemplate.queryForList(
                        "SELECT pg_get_constraintdef(c.oid) FROM pg_constraint c"
                                + " JOIN pg_class t ON t.oid = c.conrelid"
                                + " WHERE t.relname = 'applications' AND c.contype = 'c'"
                                + " AND pg_get_constraintdef(c.oid) LIKE '%rating%'",
                        String.class);

        assertThat(checks).hasSize(1);
    }

    // Each migration runs in its own schema, on its own connections, and is dropped afterwards.
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void v5AppliesOnAnEmptyDatabase() {
        String schema = "flyway_empty_" + UUID.randomUUID().toString().replace("-", "");
        try {
            flyway(schema, null).migrate();

            assertThat(currentVersion(schema)).isEqualTo("5");
            assertThat(tableExists(schema, "notes")).isTrue();
        } finally {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void v5AppliesOnADatabaseAlreadyAtV4AndKeepsItsData() {
        String schema = "flyway_v4_" + UUID.randomUUID().toString().replace("-", "");
        try {
            flyway(schema, "4").migrate();
            assertThat(currentVersion(schema)).isEqualTo("4");
            assertThat(tableExists(schema, "notes")).isFalse();
            jdbcTemplate.update(
                    "INSERT INTO "
                            + schema
                            + ".users (name, email, password_hash, role)"
                            + " VALUES ('U', 'u@example.com', 'hash', 'RECRUITER')");
            jdbcTemplate.update(
                    "INSERT INTO "
                            + schema
                            + ".jobs (title, status, created_by)"
                            + " SELECT 'Job', 'OPEN', id FROM "
                            + schema
                            + ".users");
            jdbcTemplate.update(
                    "INSERT INTO "
                            + schema
                            + ".candidates (full_name, email)"
                            + " VALUES ('C', 'c@example.com')");
            jdbcTemplate.update(
                    "INSERT INTO "
                            + schema
                            + ".applications"
                            + " (job_id, candidate_id, stage, position, rating)"
                            + " SELECT j.id, c.id, 'APPLIED', 0, 4 FROM "
                            + schema
                            + ".jobs j, "
                            + schema
                            + ".candidates c");

            flyway(schema, null).migrate();

            assertThat(currentVersion(schema)).isEqualTo("5");
            assertThat(
                            jdbcTemplate.queryForObject(
                                    "SELECT rating FROM " + schema + ".applications",
                                    Integer.class))
                    .isEqualTo(4);
            jdbcTemplate.update(
                    "INSERT INTO "
                            + schema
                            + ".notes (application_id, author_id, content, created_at)"
                            + " SELECT a.id, u.id, 'Kept', now() FROM "
                            + schema
                            + ".applications a, "
                            + schema
                            + ".users u");
            assertThat(
                            jdbcTemplate.queryForObject(
                                    "SELECT count(*) FROM " + schema + ".notes", Integer.class))
                    .isEqualTo(1);
        } finally {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    private Flyway flyway(String schema, String target) {
        var configuration =
                Flyway.configure()
                        .dataSource(dataSource)
                        .schemas(schema)
                        .createSchemas(true)
                        .locations("classpath:db/migration");
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private String currentVersion(String schema) {
        return jdbcTemplate.queryForObject(
                "SELECT version FROM "
                        + schema
                        + ".flyway_schema_history"
                        + " WHERE success ORDER BY installed_rank DESC LIMIT 1",
                String.class);
    }

    private boolean tableExists(String schema, String table) {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM information_schema.tables"
                                + " WHERE table_schema = ? AND table_name = ?",
                        Integer.class,
                        schema,
                        table);
        return count != null && count == 1;
    }

    // Must stay last in its test: the failed insert aborts the transaction.
    @Test
    void noteForAMissingApplicationViolatesTheForeignKey() {
        Application application = application();
        jdbcTemplate.update(
                "INSERT INTO notes (application_id, author_id, content, created_at)"
                        + " VALUES (?, ?, 'ok', now())",
                application.getId(),
                author.getId());

        assertThatThrownBy(() ->
                        jdbcTemplate.update(
                                "INSERT INTO notes (application_id, author_id, content, created_at)"
                                        + " VALUES (?, ?, 'x', now())",
                                Long.MAX_VALUE,
                                author.getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_notes_application");
    }
}
