package io.github.astvvg.ats.security.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record LoginResponse(
        String accessToken,
        @Schema(example = "Bearer") String tokenType,
        @Schema(description = "Token lifetime in seconds", example = "7200") long expiresIn) {

    public static LoginResponse bearer(String accessToken, long expiresIn) {
        return new LoginResponse(accessToken, "Bearer", expiresIn);
    }
}
