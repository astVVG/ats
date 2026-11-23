package io.github.astvvg.ats.job.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;

/** The user who created a job, without any other account data. */
public record JobCreatorResponse(
        @Schema(requiredMode = REQUIRED) Long id, @Schema(requiredMode = REQUIRED) String name) {}
