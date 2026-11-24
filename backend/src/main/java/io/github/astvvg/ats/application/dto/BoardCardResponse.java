package io.github.astvvg.ats.application.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** One application as shown on the board. */
public record BoardCardResponse(
        @Schema(requiredMode = REQUIRED) Long id,
        @Schema(requiredMode = REQUIRED) int position,
        @Schema(nullable = true, minimum = "1", maximum = "5") Integer rating,
        @Schema(requiredMode = REQUIRED) BoardCandidateResponse candidate,
        @Schema(requiredMode = REQUIRED) Instant updatedAt) {}
