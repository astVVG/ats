package io.github.astvvg.ats.application.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.github.astvvg.ats.application.domain.Stage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record BoardColumnResponse(
        @Schema(requiredMode = REQUIRED) Stage stage,
        @Schema(requiredMode = REQUIRED, description = "Ordered by position, ascending")
                List<BoardCardResponse> applications) {}
