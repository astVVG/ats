package io.github.astvvg.ats.candidate.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CandidateRequest(
        @Schema(example = "Laura Gómez") @NotBlank @Size(max = 200) String fullName,
        @Schema(example = "laura@example.com") @NotBlank @Email @Size(max = 254) String email,
        @Schema(example = "+52 777 123 4567") @Size(max = 30) String phone,
        @Schema(example = "LinkedIn") @Size(max = 100) String source) {

    public CandidateRequest {
        // Trimmed before validation, so surrounding spaces neither fail @Email nor count in @Size.
        email = email == null ? null : email.trim();
    }
}
