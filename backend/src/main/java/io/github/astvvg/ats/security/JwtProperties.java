package io.github.astvvg.ats.security;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.jwt")
public record JwtProperties(String secret, @NotNull @DefaultValue("PT2H") Duration expiration) {

    static final int MIN_SECRET_LENGTH = 32;

    // Validated through a boolean property so a rejected secret never appears in startup errors.
    @AssertTrue(
            message =
                    "app.jwt.secret is missing or too short: set JWT_SECRET to a random value of"
                            + " at least 32 characters")
    public boolean isSecretValid() {
        return secret != null && secret.length() >= MIN_SECRET_LENGTH;
    }
}
