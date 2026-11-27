package io.github.astvvg.ats.note.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record NoteResponse(
        @Schema(requiredMode = REQUIRED) Long id,
        @Schema(requiredMode = REQUIRED) Long applicationId,
        @Schema(requiredMode = REQUIRED) String content,
        @Schema(requiredMode = REQUIRED) NoteAuthorResponse author,
        @Schema(requiredMode = REQUIRED) Instant createdAt) {}
