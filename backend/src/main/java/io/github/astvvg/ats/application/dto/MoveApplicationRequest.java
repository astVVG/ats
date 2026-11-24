package io.github.astvvg.ats.application.dto;

import io.github.astvvg.ats.application.domain.Stage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record MoveApplicationRequest(
        @Schema(example = "INTERVIEW") @NotNull Stage stage,
        @Schema(
                        example = "0",
                        description =
                                "Zero-based target position. Within the same stage: 0 to n-1;"
                                        + " to another stage: 0 to n, where n is the number of"
                                        + " applications in the target stage and n means the end.")
                @NotNull
                @Min(0)
                Integer position) {}
