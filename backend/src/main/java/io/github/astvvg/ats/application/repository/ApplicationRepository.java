package io.github.astvvg.ats.application.repository;

import io.github.astvvg.ats.application.domain.Application;
import io.github.astvvg.ats.application.domain.Stage;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
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

    /** Every application of a job with its candidate, in one query. */
    @Query(
            "select a from Application a join fetch a.candidate"
                    + " where a.job.id = :jobId order by a.position, a.id")
    List<Application> findBoardByJobId(@Param("jobId") Long jobId);
}
