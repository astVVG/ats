package io.github.astvvg.ats.common;

import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hibernate.exception.ConstraintViolationException;

/** Tells which database constraint rejected a write, as MySQL reports it. */
public final class ConstraintViolations {

    // For foreign keys Hibernate extracts no name, so it only appears in the server message.
    private static final Pattern FOREIGN_KEY_NAME =
            Pattern.compile("CONSTRAINT `([^`]+)` FOREIGN KEY");

    private ConstraintViolations() {}

    public static boolean violates(Throwable ex, String constraint) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && constraint.equalsIgnoreCase(constraintName(violation))) {
                return true;
            }
        }
        return false;
    }

    private static String constraintName(ConstraintViolationException violation) {
        String name = violation.getConstraintName();
        if (name != null) {
            // MySQL qualifies unique keys with their table: "users.uq_users_email".
            return name.substring(name.lastIndexOf('.') + 1);
        }
        SQLException cause = violation.getSQLException();
        if (cause == null || cause.getMessage() == null) {
            return null;
        }
        Matcher foreignKey = FOREIGN_KEY_NAME.matcher(cause.getMessage());
        return foreignKey.find() ? foreignKey.group(1) : null;
    }
}
