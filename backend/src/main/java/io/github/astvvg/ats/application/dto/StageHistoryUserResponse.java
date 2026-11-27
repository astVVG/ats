package io.github.astvvg.ats.application.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;

/** The user who changed the stage, without any other account data. */
public record StageHistoryUserResponse(
        @Schema(requiredMode = REQUIRED) Long id, @Schema(requiredMode = REQUIRED) String name) {}
