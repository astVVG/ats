package io.github.astvvg.ats.application.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.github.astvvg.ats.job.domain.JobStatus;
import io.swagger.v3.oas.annotations.media.Schema;

public record BoardJobResponse(
        @Schema(requiredMode = REQUIRED) Long id,
        @Schema(requiredMode = REQUIRED) String title,
        @Schema(requiredMode = REQUIRED) JobStatus status) {}
