package io.github.astvvg.ats.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;

/** Limits the UTF-8 encoded length of a string; null is valid. */
@Documented
@Constraint(validatedBy = MaxUtf8Bytes.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxUtf8Bytes {

    /** BCrypt only uses the first 72 bytes of a password. */
    int BCRYPT_MAX_BYTES = 72;

    int value();

    String message() default "{io.github.astvvg.ats.common.validation.MaxUtf8Bytes.message}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<MaxUtf8Bytes, String> {

        private int maxBytes;

        public static boolean fits(String text, int maxBytes) {
            return text == null || text.getBytes(StandardCharsets.UTF_8).length <= maxBytes;
        }

        @Override
        public void initialize(MaxUtf8Bytes annotation) {
            this.maxBytes = annotation.value();
        }

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return fits(value, maxBytes);
        }
    }
}
