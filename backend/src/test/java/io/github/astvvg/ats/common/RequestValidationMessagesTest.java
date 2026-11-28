package io.github.astvvg.ats.common;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.astvvg.ats.application.domain.Stage;
import io.github.astvvg.ats.application.dto.CreateApplicationRequest;
import io.github.astvvg.ats.application.dto.MoveApplicationRequest;
import io.github.astvvg.ats.application.dto.UpdateRatingRequest;
import io.github.astvvg.ats.candidate.dto.CandidateRequest;
import io.github.astvvg.ats.job.dto.JobRequest;
import io.github.astvvg.ats.job.dto.JobStatusRequest;
import io.github.astvvg.ats.note.dto.CreateNoteRequest;
import io.github.astvvg.ats.security.dto.LoginRequest;
import io.github.astvvg.ats.user.domain.Role;
import io.github.astvvg.ats.user.dto.CreateUserRequest;
import io.github.astvvg.ats.user.dto.UpdateUserStatusRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Boundaries of every request field, with the JVM in English: messages stay in Spanish. */
class RequestValidationMessagesTest {

    private static final String REQUIRED = "es obligatorio";
    private static final String BLANK = "no debe estar vacío";
    private static final String EMAIL = "debe ser un correo electrónico válido";

    private static Locale originalLocale;
    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void englishJvm() {
        originalLocale = Locale.getDefault();
        Locale.setDefault(Locale.ENGLISH);
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void restoreLocale() {
        factory.close();
        Locale.setDefault(originalLocale);
    }

    private static String size(int min, int max) {
        return "debe tener entre " + min + " y " + max + " caracteres";
    }

    private static String text(int length) {
        return "a".repeat(length);
    }

    /** A syntactically valid address of exactly {@code length} characters (194 or more). */
    private static String email(int length) {
        String head = text(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + ".";
        return head + "d".repeat(length - head.length());
    }

    private static Stream<Arguments> textFields() {
        Function<String, Object> userName =
                v -> new CreateUserRequest(v, "a@example.com", "password-1", Role.ADMIN);
        Function<String, Object> jobTitle = v -> new JobRequest(v, null, null, null);
        Function<String, Object> department = v -> new JobRequest("t", v, null, null);
        Function<String, Object> location = v -> new JobRequest("t", null, v, null);
        Function<String, Object> description = v -> new JobRequest("t", null, null, v);
        Function<String, Object> fullName =
                v -> new CandidateRequest(v, "a@example.com", null, null);
        Function<String, Object> phone = v -> new CandidateRequest("n", "a@example.com", v, null);
        Function<String, Object> source = v -> new CandidateRequest("n", "a@example.com", null, v);
        Function<String, Object> note = CreateNoteRequest::new;
        Function<String, Object> loginPassword = v -> new LoginRequest("a@example.com", v);
        return Stream.of(
                Arguments.of("name", userName, true, 150, size(0, 150)),
                Arguments.of("title", jobTitle, true, 200, size(0, 200)),
                Arguments.of("department", department, false, 100, size(0, 100)),
                Arguments.of("location", location, false, 100, size(0, 100)),
                Arguments.of("description", description, false, 5000, size(0, 5000)),
                Arguments.of("fullName", fullName, true, 200, size(0, 200)),
                Arguments.of("phone", phone, false, 30, size(0, 30)),
                Arguments.of("source", source, false, 100, size(0, 100)),
                Arguments.of("content", note, true, 2000, size(0, 2000)),
                Arguments.of("password", loginPassword, true, 128, size(0, 128)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("textFields")
    void textFieldBoundaries(
            String field,
            Function<String, Object> request,
            boolean required,
            int max,
            String tooLong) {
        assertThat(violations(request.apply(text(max)))).isEmpty();
        assertThat(violations(request.apply(text(max + 1))))
                .containsExactly(field + ": " + tooLong);
        for (String missing : new String[] {null, "", "   "}) {
            if (required) {
                assertThat(violations(request.apply(missing)))
                        .containsExactly(field + ": " + BLANK);
            } else {
                assertThat(violations(request.apply(missing))).isEmpty();
            }
        }
    }

    @Test
    void emailFields() {
        Function<String, Object> user =
                v -> new CreateUserRequest("n", v, "password-1", Role.ADMIN);
        Function<String, Object> candidate = v -> new CandidateRequest("n", v, null, null);
        Function<String, Object> login = v -> new LoginRequest(v, "p");

        assertThat(violations(user.apply(email(255)))).isEmpty();
        assertThat(violations(user.apply(email(256)))).contains("email: " + size(0, 255));
        assertThat(violations(candidate.apply(email(254)))).isEmpty();
        assertThat(violations(candidate.apply(email(255)))).contains("email: " + size(0, 254));
        for (Function<String, Object> request : java.util.List.of(user, candidate, login)) {
            assertThat(violations(request.apply(null))).containsExactly("email: " + BLANK);
            assertThat(violations(request.apply(""))).containsExactly("email: " + BLANK);
            assertThat(violations(request.apply("   "))).contains("email: " + BLANK);
            assertThat(violations(request.apply("not-an-email")))
                    .containsExactly("email: " + EMAIL);
        }
    }

    @Test
    void newPasswordLengthAndBytes() {
        Function<String, Object> user =
                v -> new CreateUserRequest("n", "a@example.com", v, Role.ADMIN);

        assertThat(violations(user.apply(text(8)))).isEmpty();
        assertThat(violations(user.apply(text(72)))).isEmpty();
        assertThat(violations(user.apply(text(7)))).containsExactly("password: " + size(8, 72));
        assertThat(violations(user.apply(text(73))))
                .containsExactlyInAnyOrder(
                        "password: " + size(8, 72),
                        "password: debe ocupar como máximo 72 bytes en UTF-8");
        // 36 two-byte characters fill the limit; one more exceeds it while within 72 characters.
        assertThat(violations(user.apply("ñ".repeat(36)))).isEmpty();
        assertThat(violations(user.apply("ñ".repeat(37))))
                .containsExactly("password: debe ocupar como máximo 72 bytes en UTF-8");
        assertThat(violations(user.apply(null))).containsExactly("password: " + BLANK);
    }

    @Test
    void noteLimitCountsUtf16UnitsAfterStripping() {
        assertThat(violations(new CreateNoteRequest("  " + text(2000) + "  "))).isEmpty();
        assertThat(violations(new CreateNoteRequest("😀".repeat(1000)))).isEmpty();
        assertThat(violations(new CreateNoteRequest("😀".repeat(1000) + "a")))
                .containsExactly("content: " + size(0, 2000));
    }

    @Test
    void requiredValuesAndNumericRanges() {
        assertThat(violations(new CreateUserRequest("n", "a@example.com", "password-1", null)))
                .containsExactly("role: " + REQUIRED);
        assertThat(violations(new UpdateUserStatusRequest(null)))
                .containsExactly("active: " + REQUIRED);
        assertThat(violations(new JobStatusRequest(null))).containsExactly("status: " + REQUIRED);
        assertThat(violations(new CreateApplicationRequest(null, null)))
                .containsExactlyInAnyOrder("jobId: " + REQUIRED, "candidateId: " + REQUIRED);
        assertThat(violations(new MoveApplicationRequest(null, null)))
                .containsExactlyInAnyOrder("stage: " + REQUIRED, "position: " + REQUIRED);
        assertThat(violations(new MoveApplicationRequest(Stage.APPLIED, 0))).isEmpty();
        assertThat(violations(new MoveApplicationRequest(Stage.APPLIED, -1)))
                .containsExactly("position: debe ser mayor o igual que 0");
        assertThat(violations(new UpdateRatingRequest(null))).isEmpty();
        assertThat(violations(new UpdateRatingRequest(1))).isEmpty();
        assertThat(violations(new UpdateRatingRequest(5))).isEmpty();
        assertThat(violations(new UpdateRatingRequest(0)))
                .containsExactly("rating: debe ser mayor o igual que 1");
        assertThat(violations(new UpdateRatingRequest(6)))
                .containsExactly("rating: debe ser menor o igual que 5");
    }

    private static java.util.List<String> violations(Object request) {
        return validator.validate(request).stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .toList();
    }
}
