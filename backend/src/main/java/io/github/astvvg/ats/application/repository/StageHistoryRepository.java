package io.github.astvvg.ats.application.repository;

import io.github.astvvg.ats.application.domain.StageHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StageHistoryRepository extends JpaRepository<StageHistory, Long> {

    /** One page of an application's stage changes with who made them, paginated in the database. */
    @EntityGraph(attributePaths = "movedBy")
    Page<StageHistory> findByApplicationId(Long applicationId, Pageable pageable);
}
