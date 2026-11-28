package io.github.astvvg.ats.metrics.repository;

import io.github.astvvg.ats.application.domain.Stage;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.hibernate.query.NativeQuery;
import org.hibernate.type.StandardBasicTypes;
import org.springframework.stereotype.Repository;

/** Aggregates in MySQL; each method runs exactly one query whatever the amount of data. */
@Repository
public class MetricsRepository {

    /** Sum of the finished stays of one stage, in seconds, and how many there were. */
    public record StageDurations(BigDecimal totalSeconds, long samples) {}

    private static final String FILTER =
            """
            (:jobId IS NULL OR a.job_id = :jobId)
            AND (:createdFrom IS NULL OR a.created_at >= :createdFrom)
            AND (:createdBefore IS NULL OR a.created_at < :createdBefore)
            """;

    private static final String REACHED =
            """
            SELECT h.to_stage, COUNT(DISTINCT h.application_id)
            FROM stage_history h
            JOIN applications a ON a.id = h.application_id
            WHERE %s
            GROUP BY h.to_stage
            """
                    .formatted(FILTER);

    // The filter selects whole applications, so every window sees their complete history.
    private static final String FINISHED_STAYS =
            """
            SELECT s.stage, SUM(TIMESTAMPDIFF(MICROSECOND, s.entered_at, s.left_at)), COUNT(*)
            FROM (
                SELECT h.to_stage AS stage,
                       h.moved_at AS entered_at,
                       LEAD(h.moved_at) OVER (
                           PARTITION BY h.application_id ORDER BY h.moved_at, h.id) AS left_at
                FROM stage_history h
                JOIN applications a ON a.id = h.application_id
                WHERE %s
            ) s
            WHERE s.left_at IS NOT NULL
            GROUP BY s.stage
            """
                    .formatted(FILTER);

    private static final String TIME_TO_HIRE =
            """
            SELECT SUM(TIMESTAMPDIFF(MICROSECOND, t.created_at, t.first_hired_at)), COUNT(*)
            FROM (
                SELECT a.created_at, MIN(h.moved_at) AS first_hired_at
                FROM applications a
                JOIN stage_history h ON h.application_id = a.id
                WHERE h.to_stage = 'HIRED' AND %s
                GROUP BY a.id, a.created_at
            ) t
            """
                    .formatted(FILTER);

    private final EntityManager entityManager;

    public MetricsRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /** Distinct applications that ever entered each stage; stages nobody reached are absent. */
    public Map<Stage, Long> reachedByStage(MetricsFilter filter) {
        Map<Stage, Long> reached = new EnumMap<>(Stage.class);
        for (Object[] row : rows(REACHED, filter)) {
            reached.put(Stage.valueOf((String) row[0]), ((Number) row[1]).longValue());
        }
        return reached;
    }

    /** Finished stays per stage; the current stay of each application is left out. */
    public Map<Stage, StageDurations> finishedStays(MetricsFilter filter) {
        Map<Stage, StageDurations> stays = new EnumMap<>(Stage.class);
        for (Object[] row : rows(FINISHED_STAYS, filter)) {
            stays.put(
                    Stage.valueOf((String) row[0]),
                    new StageDurations(seconds(row[1]), ((Number) row[2]).longValue()));
        }
        return stays;
    }

    /** Seconds from creation to the first arrival in HIRED, summed over hired applications. */
    public StageDurations timeToHire(MetricsFilter filter) {
        Object[] row = rows(TIME_TO_HIRE, filter).getFirst();
        return new StageDurations(seconds(row[0]), ((Number) row[1]).longValue());
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> rows(String sql, MetricsFilter filter) {
        NativeQuery<Object[]> query =
                entityManager.createNativeQuery(sql).unwrap(NativeQuery.class);
        query.setParameter("jobId", filter.jobId(), StandardBasicTypes.LONG);
        query.setParameter("createdFrom", filter.createdFrom(), StandardBasicTypes.INSTANT);
        query.setParameter("createdBefore", filter.createdBefore(), StandardBasicTypes.INSTANT);
        return query.getResultList();
    }

    // The database sums whole microseconds so that no fraction of a second is lost.
    private static BigDecimal seconds(Object microseconds) {
        if (microseconds == null) {
            return null;
        }
        return new BigDecimal(microseconds.toString()).movePointLeft(6);
    }
}
