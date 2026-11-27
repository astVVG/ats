package io.github.astvvg.ats.metrics.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.github.astvvg.ats.application.domain.Stage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

public record StageTimeResponse(
        @Schema(requiredMode = REQUIRED) Stage stage,
        @Schema(
                        nullable = true,
                        example = "26.0",
                        description =
                                "Average length of the finished stays in this stage, in hours,"
                                        + " rounded half-up to 2 decimals. Null without samples.")
                BigDecimal averageHours,
        @Schema(
                        requiredMode = REQUIRED,
                        description =
                                "Finished stays; the current stay of each application is not"
                                        + " counted and a stage entered again counts again")
                long samples) {}
