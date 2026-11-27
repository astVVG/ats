package io.github.astvvg.ats.application.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.github.astvvg.ats.application.domain.Stage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record ApplicationResponse(
        @Schema(requiredMode = REQUIRED) Long id,
        @Schema(requiredMode = REQUIRED) Long jobId,
        @Schema(requiredMode = REQUIRED) Long candidateId,
        @Schema(requiredMode = REQUIRED) Stage stage,
        @Schema(requiredMode = REQUIRED, description = "Zero-based order inside its stage column")
                int position,
        @Schema(nullable = true, minimum = "1", maximum = "5") Integer rating,
        @Schema(requiredMode = REQUIRED) Instant createdAt,
        @Schema(requiredMode = REQUIRED) Instant updatedAt) {}
