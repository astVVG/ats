package io.github.astvvg.ats.job.repository;

import io.github.astvvg.ats.common.LikePatterns;
import io.github.astvvg.ats.job.domain.Job;
import io.github.astvvg.ats.job.domain.JobStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.util.StringUtils;

public interface JobRepository extends JpaRepository<Job, Long>, JpaSpecificationExecutor<Job> {

    @Override
    @EntityGraph(attributePaths = "createdBy")
    Optional<Job> findById(Long id);

    @Override
    @EntityGraph(attributePaths = "createdBy")
    Page<Job> findAll(Specification<Job> spec, Pageable pageable);

    /** Locks the job row until the transaction ends; serializes writes to its board. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from Job j where j.id = :id")
    Optional<Job> findByIdForUpdate(@Param("id") Long id);

    /**
     * Optional filters: {@code status} matches exactly; {@code q} is a case-insensitive "contains"
     * on title or department where % and _ are literal. Null or blank means no filter.
     */
    static Specification<Job> matching(JobStatus status, String q) {
        return (root, query, cb) -> {
            var predicate = cb.conjunction();
            if (status != null) {
                predicate = cb.and(predicate, cb.equal(root.get("status"), status));
            }
            if (StringUtils.hasText(q)) {
                String pattern = LikePatterns.containsIgnoreCase(q.trim());
                predicate =
                        cb.and(
                                predicate,
                                cb.or(
                                        cb.like(
                                                cb.lower(root.get("title")),
                                                pattern,
                                                LikePatterns.ESCAPE),
                                        cb.like(
                                                cb.lower(root.get("department")),
                                                pattern,
                                                LikePatterns.ESCAPE)));
            }
            return predicate;
        };
    }
}
