package io.github.astvvg.ats.security.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @Schema(example = "admin@example.com") @NotBlank @Email String email,
        @NotBlank @Size(max = 128) String password) {}
