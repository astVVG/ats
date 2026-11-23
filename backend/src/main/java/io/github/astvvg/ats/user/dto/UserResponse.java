package io.github.astvvg.ats.user.dto;

import io.github.astvvg.ats.user.domain.Role;
import java.time.Instant;

public record UserResponse(
        Long id, String name, String email, Role role, boolean active, Instant createdAt) {}
