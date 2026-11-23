package io.github.astvvg.ats.user.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.github.astvvg.ats.user.domain.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record UserResponse(
        @Schema(requiredMode = REQUIRED) Long id,
        @Schema(requiredMode = REQUIRED) String name,
        @Schema(requiredMode = REQUIRED) String email,
        @Schema(requiredMode = REQUIRED) Role role,
        @Schema(requiredMode = REQUIRED) boolean active,
        @Schema(requiredMode = REQUIRED) Instant createdAt) {}
