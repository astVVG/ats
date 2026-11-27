package io.github.astvvg.ats.note.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;

/** The user who wrote a note, without any other account data. */
public record NoteAuthorResponse(
        @Schema(requiredMode = REQUIRED) Long id, @Schema(requiredMode = REQUIRED) String name) {}
