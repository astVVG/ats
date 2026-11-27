package io.github.astvvg.ats.metrics.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record FunnelResponse(
        @Schema(requiredMode = REQUIRED, description = "Always the five stages, in stage order")
                List<FunnelStageResponse> stages) {}
