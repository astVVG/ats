package io.github.astvvg.ats.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.astvvg.ats.user.domain.Role;
import io.github.astvvg.ats.user.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.util.ReflectionTestUtils;

class JwtServiceTest {

    private static final String SECRET = "test-only-jwt-secret-not-for-real-use-0123456789";
    private static final String OTHER_SECRET = "another-test-only-secret-for-signing-9876543210";

    private final JwtService jwtService =
            new JwtService(new JwtProperties(SECRET, Duration.ofHours(2)));

    private static User user(long id, Role role) {
        User user = new User("Test", "jwt-" + id + "@example.com", "hash", role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Test
    void validTokenReturnsUserId() {
        String token = jwtService.generateToken(user(42L, Role.RECRUITER));

        assertThat(jwtService.parseUserId(token)).contains(42L);
        assertThat(jwtService.expirationSeconds()).isEqualTo(7200);
    }

    @Test
    void tokenCarriesRoleClaim() {
        String token = jwtService.generateToken(user(7L, Role.ADMIN));

        Claims claims =
                Jwts.parser()
                        .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();
        assertThat(claims.getSubject()).isEqualTo("7");
        assertThat(claims.get(JwtService.ROLE_CLAIM, String.class)).isEqualTo("ADMIN");
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService expiredIssuer =
                new JwtService(new JwtProperties(SECRET, Duration.ofMinutes(-5)));
        String token = expiredIssuer.generateToken(user(1L, Role.ADMIN));

        assertThat(jwtService.parseUserId(token)).isEmpty();
    }

    @Test
    void tamperedTokenIsRejected() {
        String[] parts = jwtService.generateToken(user(1L, Role.RECRUITER)).split("\\.");
        String forgedPayload =
                Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(
                                "{\"sub\":\"2\",\"role\":\"ADMIN\"}"
                                        .getBytes(StandardCharsets.UTF_8));

        assertThat(jwtService.parseUserId(parts[0] + "." + forgedPayload + "." + parts[2]))
                .isEmpty();
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        JwtService otherIssuer =
                new JwtService(new JwtProperties(OTHER_SECRET, Duration.ofHours(2)));

        assertThat(jwtService.parseUserId(otherIssuer.generateToken(user(1L, Role.ADMIN))))
                .isEmpty();
    }

    @Test
    void malformedTokenIsRejected() {
        assertThat(jwtService.parseUserId("not-a-jwt")).isEmpty();
        assertThat(jwtService.parseUserId("")).isEmpty();
    }

    @Configuration
    @EnableConfigurationProperties(JwtProperties.class)
    static class PropertiesConfig {}

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(PropertiesConfig.class);

    @Test
    void missingSecretFailsStartupMentioningJwtSecret() {
        contextRunner.run(context ->
                assertThat(context.getStartupFailure())
                        .isNotNull()
                        .rootCause()
                        .hasMessageContaining("JWT_SECRET"));
    }

    @Test
    void shortSecretFailsStartupWithoutLeakingIt() {
        contextRunner
                .withPropertyValues("app.jwt.secret=short-secret-value")
                .run(context ->
                        assertThat(context.getStartupFailure())
                                .isNotNull()
                                .rootCause()
                                .hasMessageContaining("JWT_SECRET")
                                .hasMessageNotContaining("short-secret-value"));
    }

    @Test
    void validSecretUsesTwoHourDefault() {
        contextRunner
                .withPropertyValues("app.jwt.secret=" + SECRET)
                .run(context ->
                        assertThat(context.getBean(JwtProperties.class).expiration())
                                .isEqualTo(Duration.ofHours(2)));
    }
}
