package io.github.astvvg.ats.job.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JobRequest(
        @Schema(example = "Backend Developer") @NotBlank @Size(max = 200) String title,
        @Schema(example = "Engineering") @Size(max = 100) String department,
        @Schema(example = "Remote") @Size(max = 100) String location,
        @Size(max = 5000) String description) {}
