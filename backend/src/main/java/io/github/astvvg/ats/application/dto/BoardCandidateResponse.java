package io.github.astvvg.ats.application.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;

public record BoardCandidateResponse(
        @Schema(requiredMode = REQUIRED) Long id,
        @Schema(requiredMode = REQUIRED) String fullName,
        @Schema(requiredMode = REQUIRED) String email) {}
