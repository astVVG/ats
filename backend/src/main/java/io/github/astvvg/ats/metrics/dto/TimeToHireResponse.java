package io.github.astvvg.ats.metrics.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

public record TimeToHireResponse(
        @Schema(
                        nullable = true,
                        example = "3.0",
                        description =
                                "Average days of 24 hours from the application's creation to its"
                                        + " first arrival in HIRED, rounded half-up to 2 decimals."
                                        + " Null when nobody was hired.")
                BigDecimal averageDays,
        @Schema(
                        requiredMode = REQUIRED,
                        description =
                                "Applications that ever reached HIRED, each counted once even if"
                                        + " it left HIRED later")
                long hiredCount) {}
