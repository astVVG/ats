package io.github.astvvg.ats.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record CreateApplicationRequest(
        @Schema(example = "1") @NotNull Long jobId,
        @Schema(example = "1") @NotNull Long candidateId) {}
