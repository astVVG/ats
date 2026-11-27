package io.github.astvvg.ats.metrics.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.github.astvvg.ats.application.domain.Stage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

public record FunnelStageResponse(
        @Schema(requiredMode = REQUIRED) Stage stage,
        @Schema(
                        requiredMode = REQUIRED,
                        description = "Distinct applications that ever reached this stage")
                long count,
        @Schema(
                        nullable = true,
                        example = "0.5",
                        description =
                                "Ratio (not a percentage) of the next stage's count to this one"
                                        + " in APPLIED, INTERVIEW, OFFER, HIRED, rounded half-up to"
                                        + " 4 decimals. May exceed 1 when stages were skipped. Null"
                                        + " for HIRED, REJECTED and when count is 0.")
                BigDecimal conversionToNext) {}
