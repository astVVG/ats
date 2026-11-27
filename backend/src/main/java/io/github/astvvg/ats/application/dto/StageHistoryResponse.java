package io.github.astvvg.ats.application.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.github.astvvg.ats.application.domain.Stage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record StageHistoryResponse(
        @Schema(requiredMode = REQUIRED) Long id,
        @Schema(
                        nullable = true,
                        description =
                                "Null for the initial entry, when the application was created")
                Stage fromStage,
        @Schema(requiredMode = REQUIRED) Stage toStage,
        @Schema(requiredMode = REQUIRED) StageHistoryUserResponse movedBy,
        @Schema(requiredMode = REQUIRED) Instant movedAt) {}
