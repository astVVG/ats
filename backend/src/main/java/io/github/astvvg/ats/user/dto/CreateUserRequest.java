package io.github.astvvg.ats.user.dto;

import io.github.astvvg.ats.common.validation.MaxUtf8Bytes;
import io.github.astvvg.ats.user.domain.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @Schema(example = "Rita Recruiter") @NotBlank @Size(max = 150) String name,
        @Schema(example = "rita@example.com") @NotBlank @Email @Size(max = 255) String email,
        @Schema(description = "Between 8 and 72 characters, at most 72 bytes in UTF-8")
                @NotBlank
                @Size(min = 8, max = 72)
                @MaxUtf8Bytes(MaxUtf8Bytes.BCRYPT_MAX_BYTES)
                String password,
        @NotNull Role role) {}
