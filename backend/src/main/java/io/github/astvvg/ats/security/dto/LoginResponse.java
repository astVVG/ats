package io.github.astvvg.ats.security.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;

public record LoginResponse(
        @Schema(requiredMode = REQUIRED) String accessToken,
        @Schema(requiredMode = REQUIRED, example = "Bearer") String tokenType,
        @Schema(
                        requiredMode = REQUIRED,
                        description = "Token lifetime in seconds",
                        example = "7200")
                long expiresIn) {

    public static LoginResponse bearer(String accessToken, long expiresIn) {
        return new LoginResponse(accessToken, "Bearer", expiresIn);
    }
}
