package io.github.astvvg.ats.job.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.github.astvvg.ats.job.domain.JobStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record JobResponse(
        @Schema(requiredMode = REQUIRED) Long id,
        @Schema(requiredMode = REQUIRED) String title,
        @Schema(nullable = true) String department,
        @Schema(nullable = true) String location,
        @Schema(nullable = true) String description,
        @Schema(requiredMode = REQUIRED) JobStatus status,
        @Schema(requiredMode = REQUIRED) JobCreatorResponse createdBy,
        @Schema(requiredMode = REQUIRED) Instant createdAt,
        @Schema(requiredMode = REQUIRED) Instant updatedAt) {}
