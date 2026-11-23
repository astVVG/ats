package io.github.astvvg.ats.common;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    static final String VALIDATION_TITLE = "Validation failed";
    static final String VALIDATION_DETAIL = "One or more fields are invalid";
    static final String UPLOAD_TOO_LARGE_DETAIL = "The upload exceeds the maximum size of 5 MB";

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
        // Well-formed JSON with a value of the wrong type (e.g. an unknown enum constant).
        if (ex.getCause() instanceof InvalidFormatException invalid) {
            FieldViolation violation =
                    new FieldViolation(jsonPath(invalid), invalidValueMessage(invalid));
            return handleExceptionInternal(
                    ex, validationProblem(List.of(violation)), headers, status, request);
        }
        return super.handleHttpMessageNotReadable(ex, headers, status, request);
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

    private static String jsonPath(InvalidFormatException ex) {
        return ex.getPath().stream()
                .map(ref ->
                        ref.getFieldName() != null
                                ? ref.getFieldName()
                                : "[" + ref.getIndex() + "]")
                .collect(Collectors.joining("."));
    }

    private static String invalidValueMessage(InvalidFormatException ex) {
        Class<?> target = ex.getTargetType();
        if (target != null && target.isEnum()) {
            return "must be one of "
                    + Arrays.stream(target.getEnumConstants())
                            .map(Object::toString)
                            .collect(Collectors.joining(", "));
        }
        return "has an invalid value";
    }

    private static String leafName(Path path) {
        String name = null;
        for (Path.Node node : path) {
            name = node.getName();
        }
        return name != null ? name : path.toString();
    }
}
