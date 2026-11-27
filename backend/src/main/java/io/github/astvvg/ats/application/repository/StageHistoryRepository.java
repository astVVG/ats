package io.github.astvvg.ats.application.repository;

import io.github.astvvg.ats.application.domain.StageHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StageHistoryRepository extends JpaRepository<StageHistory, Long> {}
