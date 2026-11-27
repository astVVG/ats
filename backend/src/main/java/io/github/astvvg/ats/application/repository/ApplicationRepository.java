package io.github.astvvg.ats.application.repository;

import io.github.astvvg.ats.application.domain.Application;
import io.github.astvvg.ats.application.domain.Stage;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    boolean existsByJobIdAndCandidateId(Long jobId, Long candidateId);

    boolean existsByCandidateId(Long candidateId);

    /** Highest position in one column of a job's board; empty when the column has no cards. */
    @Query(
            "select max(a.position) from Application a"
                    + " where a.job.id = :jobId and a.stage = :stage")
    Optional<Integer> findMaxPosition(@Param("jobId") Long jobId, @Param("stage") Stage stage);

    long countByJobIdAndStage(Long jobId, Stage stage);

    /** Reads only the job id, so the application itself is not loaded before the job lock. */
    @Query("select a.job.id from Application a where a.id = :id")
    Optional<Long> findJobIdById(@Param("id") Long id);

    /** Moves up one place the cards of a column with {@code from < position <= to}. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            "update Application a set a.position = a.position - 1"
                    + " where a.job.id = :jobId and a.stage = :stage"
                    + " and a.position > :from and a.position <= :to")
    int decrementPositions(
            @Param("jobId") Long jobId,
            @Param("stage") Stage stage,
            @Param("from") int from,
            @Param("to") int to);

    /** Moves down one place the cards of a column with {@code from <= position < to}. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            "update Application a set a.position = a.position + 1"
                    + " where a.job.id = :jobId and a.stage = :stage"
                    + " and a.position >= :from and a.position < :to")
    int incrementPositions(
            @Param("jobId") Long jobId,
            @Param("stage") Stage stage,
            @Param("from") int from,
            @Param("to") int to);

    /** Writes only the rating and updated_at of one application; stage and position are kept. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            "update Application a set a.rating = :rating, a.updatedAt = :updatedAt"
                    + " where a.id = :id")
    int updateRating(
            @Param("id") Long id,
            @Param("rating") Short rating,
            @Param("updatedAt") Instant updatedAt);

    /** Every application of a job with its candidate, in one query. */
    @Query(
            "select a from Application a join fetch a.candidate"
                    + " where a.job.id = :jobId order by a.position, a.id")
    List<Application> findBoardByJobId(@Param("jobId") Long jobId);
}
