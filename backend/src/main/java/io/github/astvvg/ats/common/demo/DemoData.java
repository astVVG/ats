package io.github.astvvg.ats.common.demo;

import io.github.astvvg.ats.application.domain.Stage;
import io.github.astvvg.ats.job.domain.JobStatus;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.ArgumentPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

/**
 * The fictitious jobs, candidates and applications of the demo. Written with JDBC because the
 * entities stamp the current time on insert and the demo needs dates in the past. The caller
 * provides the transaction.
 */
final class DemoData {

    private record DemoJob(
            String title,
            String department,
            String location,
            String description,
            JobStatus status,
            int createdDaysAgo) {}

    private record Move(Stage to, int daysAgo) {}

    /** One candidate and their application to the job at index {@code job}. */
    private record DemoApplication(
            String fullName,
            String emailUser,
            String source,
            int job,
            int appliedDaysAgo,
            List<Move> moves) {}

    private static final List<DemoJob> JOBS =
            List.of(
                    new DemoJob(
                            "Desarrollador Backend Java",
                            "Tecnología",
                            "Cuernavaca",
                            "Vacante ficticia para la demostración.",
                            JobStatus.OPEN,
                            60),
                    new DemoJob(
                            "Diseñador UX",
                            "Producto",
                            "Remoto",
                            "Vacante ficticia para la demostración.",
                            JobStatus.OPEN,
                            45),
                    new DemoJob(
                            "Analista de datos",
                            "Operaciones",
                            "Ciudad de México",
                            "Vacante ficticia para la demostración.",
                            JobStatus.CLOSED,
                            90));

    private static final List<DemoApplication> APPLICATIONS =
            List.of(
                    application(
                            "Ana Torres",
                            "ana.torres",
                            "LinkedIn",
                            0,
                            55,
                            interview(50),
                            offer(42),
                            hired(38)),
                    application(
                            "Bruno Salas",
                            "bruno.salas",
                            "Referido",
                            0,
                            40,
                            interview(33),
                            offer(25)),
                    application(
                            "Carla Méndez",
                            "carla.mendez",
                            "Portal de empleo",
                            0,
                            30,
                            interview(24)),
                    application(
                            "Diego Ríos",
                            "diego.rios",
                            "LinkedIn",
                            0,
                            28,
                            interview(20),
                            rejected(15)),
                    application("Elena Cruz", "elena.cruz", "Referido", 0, 21, interview(14)),
                    application("Fabián Ortega", "fabian.ortega", "LinkedIn", 0, 12),
                    application("Gabriela Luna", "gabriela.luna", "Portal de empleo", 0, 6),
                    application("Héctor Vargas", "hector.vargas", "LinkedIn", 0, 3),
                    application(
                            "Irene Castro",
                            "irene.castro",
                            "Referido",
                            1,
                            41,
                            interview(35),
                            offer(27),
                            hired(22)),
                    application(
                            "Javier Peña",
                            "javier.pena",
                            "LinkedIn",
                            1,
                            32,
                            interview(26),
                            offer(18)),
                    application(
                            "Karla Núñez", "karla.nunez", "Portal de empleo", 1, 25, interview(19)),
                    application("Luis Ibarra", "luis.ibarra", "LinkedIn", 1, 18, rejected(13)),
                    application("María Solís", "maria.solis", "Referido", 1, 9),
                    application("Néstor Aguilar", "nestor.aguilar", "Portal de empleo", 1, 2),
                    application(
                            "Olivia Ramos",
                            "olivia.ramos",
                            "LinkedIn",
                            2,
                            85,
                            interview(78),
                            offer(70),
                            hired(64)),
                    application(
                            "Pablo Herrera",
                            "pablo.herrera",
                            "Referido",
                            2,
                            82,
                            interview(75),
                            offer(66),
                            rejected(60)),
                    application(
                            "Quetzali Flores",
                            "quetzali.flores",
                            "LinkedIn",
                            2,
                            80,
                            interview(72),
                            rejected(65)),
                    application(
                            "Raúl Medina", "raul.medina", "Portal de empleo", 2, 77, rejected(71)),
                    application(
                            "Sofía Navarro",
                            "sofia.navarro",
                            "LinkedIn",
                            2,
                            74,
                            interview(68),
                            offer(58)),
                    application(
                            "Tomás Guerrero", "tomas.guerrero", "Referido", 2, 69, interview(62)));

    private final JdbcTemplate jdbcTemplate;
    private final long authorId;
    private final Instant now;

    DemoData(JdbcTemplate jdbcTemplate, long authorId, Instant now) {
        this.jdbcTemplate = jdbcTemplate;
        this.authorId = authorId;
        this.now = now;
    }

    static int jobCount() {
        return JOBS.size();
    }

    static int applicationCount() {
        return APPLICATIONS.size();
    }

    void write() {
        List<Long> jobIds = new ArrayList<>();
        for (int i = 0; i < JOBS.size(); i++) {
            jobIds.add(insertJob(i));
        }
        // Next free position of each column, per job: positions start at 0 and stay contiguous.
        Map<Stage, int[]> positions = new EnumMap<>(Stage.class);
        for (Stage stage : Stage.values()) {
            positions.put(stage, new int[JOBS.size()]);
        }
        for (DemoApplication application : APPLICATIONS) {
            Stage current =
                    application.moves().isEmpty()
                            ? Stage.APPLIED
                            : application.moves().getLast().to();
            int position = positions.get(current)[application.job()]++;
            insertApplication(application, jobIds.get(application.job()), current, position);
        }
    }

    private long insertJob(int index) {
        DemoJob job = JOBS.get(index);
        Timestamp created = daysAgo(job.createdDaysAgo());
        // A closed job was last updated when it was closed, after its last movement.
        Timestamp updated = created;
        if (job.status() == JobStatus.CLOSED) {
            int lastMovement =
                    APPLICATIONS.stream()
                            .filter(a -> a.job() == index)
                            .flatMap(a -> a.moves().stream())
                            .mapToInt(Move::daysAgo)
                            .min()
                            .orElse(job.createdDaysAgo());
            updated = daysAgo(lastMovement - 1);
        }
        return insert(
                "INSERT INTO jobs (title, department, location, description, status, created_by,"
                        + " created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                job.title(),
                job.department(),
                job.location(),
                job.description(),
                job.status().name(),
                authorId,
                created,
                updated);
    }

    private void insertApplication(
            DemoApplication application, long jobId, Stage current, int position) {
        Timestamp applied = daysAgo(application.appliedDaysAgo());
        long candidateId =
                insert(
                        "INSERT INTO candidates (full_name, email, source, created_at, updated_at)"
                                + " VALUES (?, ?, ?, ?, ?)",
                        application.fullName(),
                        application.emailUser() + "@demo.test",
                        application.source(),
                        applied,
                        applied);
        Timestamp lastMovement =
                application.moves().isEmpty()
                        ? applied
                        : daysAgo(application.moves().getLast().daysAgo());
        long applicationId =
                insert(
                        "INSERT INTO applications (job_id, candidate_id, stage, position,"
                                + " created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                        jobId,
                        candidateId,
                        current.name(),
                        position,
                        applied,
                        lastMovement);
        insertHistory(applicationId, null, Stage.APPLIED, applied);
        Stage from = Stage.APPLIED;
        for (Move move : application.moves()) {
            insertHistory(applicationId, from, move.to(), daysAgo(move.daysAgo()));
            from = move.to();
        }
    }

    private void insertHistory(long applicationId, Stage from, Stage to, Timestamp movedAt) {
        jdbcTemplate.update(
                "INSERT INTO stage_history (application_id, from_stage, to_stage, moved_by,"
                        + " moved_at) VALUES (?, ?, ?, ?, ?)",
                applicationId,
                from == null ? null : from.name(),
                to.name(),
                authorId,
                movedAt);
    }

    private long insert(String sql, Object... args) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(
                connection -> {
                    PreparedStatement statement =
                            connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                    new ArgumentPreparedStatementSetter(args).setValues(statement);
                    return statement;
                },
                keys);
        return keys.getKey().longValue();
    }

    private Timestamp daysAgo(int days) {
        return Timestamp.from(now.minus(Duration.ofDays(days)));
    }

    private static DemoApplication application(
            String fullName,
            String emailUser,
            String source,
            int job,
            int appliedDaysAgo,
            Move... moves) {
        return new DemoApplication(
                fullName, emailUser, source, job, appliedDaysAgo, List.of(moves));
    }

    private static Move interview(int daysAgo) {
        return new Move(Stage.INTERVIEW, daysAgo);
    }

    private static Move offer(int daysAgo) {
        return new Move(Stage.OFFER, daysAgo);
    }

    private static Move hired(int daysAgo) {
        return new Move(Stage.HIRED, daysAgo);
    }

    private static Move rejected(int daysAgo) {
        return new Move(Stage.REJECTED, daysAgo);
    }
}
