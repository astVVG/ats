package io.github.astvvg.ats.common;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.exc.InputCoercionException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    static final String VALIDATION_TITLE = "Validation failed";
    static final String VALIDATION_DETAIL = "One or more fields are invalid";
    static final String UPLOAD_TOO_LARGE_DETAIL = "The upload exceeds the maximum size of 5 MB";
    static final String UNREADABLE_BODY_DETAIL = "Request body is empty or is not valid JSON";

    public record FieldViolation(String field, String message) {}

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        List<FieldViolation> errors = new ArrayList<>();
        ex.getBindingResult()
                .getFieldErrors()
                .forEach(e -> errors.add(new FieldViolation(e.getField(), e.getDefaultMessage())));
        ex.getBindingResult()
                .getGlobalErrors()
                .forEach(e ->
                        errors.add(new FieldViolation(e.getObjectName(), e.getDefaultMessage())));
        return handleExceptionInternal(ex, validationProblem(errors), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        List<FieldViolation> errors = new ArrayList<>();
        ex.getParameterValidationResults()
                .forEach(result -> {
                    if (result instanceof ParameterErrors parameterErrors) {
                        for (FieldError e : parameterErrors.getFieldErrors()) {
                            errors.add(new FieldViolation(e.getField(), e.getDefaultMessage()));
                        }
                    } else {
                        String name = result.getMethodParameter().getParameterName();
                        for (MessageSourceResolvable e : result.getResolvableErrors()) {
                            errors.add(new FieldViolation(name, e.getDefaultMessage()));
                        }
                    }
                });
        return handleExceptionInternal(
                ex, validationProblem(errors), headers, HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        Throwable cause = ex.getCause();
        // A missing body has no cause. Syntax is checked first because Jackson can report a cut
        // document against the field it was reading.
        if (cause == null || isSyntaxError(cause)) {
            ProblemDetail body =
                    ProblemDetail.forStatusAndDetail(
                            HttpStatus.BAD_REQUEST, UNREADABLE_BODY_DETAIL);
            body.setTitle("Bad Request");
            return handleExceptionInternal(ex, body, headers, status, request);
        }
        // Well-formed JSON with a wrong value in an identifiable field.
        if (cause instanceof JsonMappingException mapping && isWrongFieldValue(mapping)) {
            Class<?> target =
                    mapping instanceof MismatchedInputException mismatch
                            ? mismatch.getTargetType()
                            : null;
            FieldViolation violation =
                    new FieldViolation(jsonPath(mapping), invalidValueMessage(target));
            return handleExceptionInternal(
                    ex, validationProblem(List.of(violation)), headers, status, request);
        }
        return super.handleHttpMessageNotReadable(ex, headers, status, request);
    }

    // Keeps Spring's title and detail for a bad path or query value and adds which one failed.
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ResponseEntity<Object> response = super.handleTypeMismatch(ex, headers, status, request);
        if (ex instanceof MethodArgumentTypeMismatchException mismatch
                && response != null
                && response.getBody() instanceof ProblemDetail body) {
            FieldViolation violation =
                    new FieldViolation(
                            mismatch.getName(), invalidValueMessage(mismatch.getRequiredType()));
            body.setProperty("errors", List.of(violation));
        }
        return response;
    }

    // Spring answers 413 by default; uploads over the limit are a client error like any other.
    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ProblemDetail body =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, UPLOAD_TOO_LARGE_DETAIL);
        body.setTitle("Bad Request");
        return handleExceptionInternal(ex, body, headers, HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex) {
        List<FieldViolation> errors =
                ex.getConstraintViolations().stream()
                        .map(v -> new FieldViolation(leafName(v.getPropertyPath()), v.getMessage()))
                        .toList();
        return problem(validationProblem(errors));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(ResourceNotFoundException ex) {
        ProblemDetail body =
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        body.setTitle("Not Found");
        return problem(body);
    }

    @ExceptionHandler(InvalidFieldException.class)
    ResponseEntity<ProblemDetail> handleInvalidField(InvalidFieldException ex) {
        return problem(
                validationProblem(List.of(new FieldViolation(ex.getField(), ex.getMessage()))));
    }

    @ExceptionHandler(BusinessConflictException.class)
    ResponseEntity<ProblemDetail> handleConflict(BusinessConflictException ex) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        body.setTitle("Conflict");
        return problem(body);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) throws Exception {
        // Let the security filter chain resolve authentication and authorization failures.
        if (ex instanceof AccessDeniedException || ex instanceof AuthenticationException) {
            throw ex;
        }
        log.error("Unexpected error", ex);
        ProblemDetail body =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
        body.setTitle("Internal Server Error");
        return problem(body);
    }

    private static ProblemDetail validationProblem(List<FieldViolation> errors) {
        ProblemDetail body =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, VALIDATION_DETAIL);
        body.setTitle(VALIDATION_TITLE);
        body.setProperty("errors", errors);
        return body;
    }

    private static ResponseEntity<ProblemDetail> problem(ProblemDetail body) {
        return ResponseEntity.status(body.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }

    private static boolean isSyntaxError(Throwable cause) {
        return cause instanceof JsonParseException
                || cause.getCause() instanceof JsonParseException;
    }

    private static boolean isWrongFieldValue(JsonMappingException ex) {
        // An out-of-range number arrives wrapped instead of as a mismatched input.
        return !ex.getPath().isEmpty()
                && (ex instanceof MismatchedInputException
                        || ex.getCause() instanceof InputCoercionException);
    }

    private static String jsonPath(JsonMappingException ex) {
        return ex.getPath().stream()
                .map(ref ->
                        ref.getFieldName() != null
                                ? ref.getFieldName()
                                : "[" + ref.getIndex() + "]")
                .collect(Collectors.joining("."));
    }

    // Never names the Java target type: these messages go to the client.
    private static String invalidValueMessage(Class<?> target) {
        if (target != null && target.isEnum()) {
            return "debe ser uno de: "
                    + Arrays.stream(target.getEnumConstants())
                            .map(Object::toString)
                            .collect(Collectors.joining(", "));
        }
        if (target == Long.class || target == long.class) {
            return "debe ser un número entero válido";
        }
        if (target == LocalDate.class) {
            return "debe ser una fecha válida con formato AAAA-MM-DD";
        }
        return "tiene un valor inválido";
    }

    private static String leafName(Path path) {
        String name = null;
        for (Path.Node node : path) {
            name = node.getName();
        }
        return name != null ? name : path.toString();
    }
}
