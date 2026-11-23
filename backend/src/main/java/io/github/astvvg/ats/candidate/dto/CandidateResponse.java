package io.github.astvvg.ats.candidate.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record CandidateResponse(
        @Schema(requiredMode = REQUIRED) Long id,
        @Schema(requiredMode = REQUIRED) String fullName,
        @Schema(requiredMode = REQUIRED) String email,
        @Schema(nullable = true) String phone,
        @Schema(nullable = true) String source,
        @Schema(requiredMode = REQUIRED, description = "Whether a resume PDF has been uploaded")
                boolean hasResume,
        @Schema(requiredMode = REQUIRED) Instant createdAt,
        @Schema(requiredMode = REQUIRED) Instant updatedAt) {}
