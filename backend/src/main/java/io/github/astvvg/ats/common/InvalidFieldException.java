package io.github.astvvg.ats.common;

/** A request field that is well formed but invalid for the current data; answered as a 400. */
public class InvalidFieldException extends RuntimeException {

    private final String field;

    public InvalidFieldException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
