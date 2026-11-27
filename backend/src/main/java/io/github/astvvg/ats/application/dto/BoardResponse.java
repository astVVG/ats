package io.github.astvvg.ats.application.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record BoardResponse(
        @Schema(requiredMode = REQUIRED) BoardJobResponse job,
        @Schema(
                        requiredMode = REQUIRED,
                        description = "One column per stage, always all of them and in stage order")
                List<BoardColumnResponse> columns) {}
