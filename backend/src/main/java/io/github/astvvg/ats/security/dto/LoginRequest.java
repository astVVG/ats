package io.github.astvvg.ats.security.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @Schema(example = "admin@example.com") @NotBlank @Email String email,
        @NotBlank String password) {}
