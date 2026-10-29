package edu.itc.shop.common;

import java.net.URI;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Problem details for the customer and order controllers.
 * <p>
 * Runs before {@link edu.itc.shop.web.ApiExceptionHandler}, which still handles everything
 * not listed here (body validation, insufficient stock, unknown product, ...). It is limited
 * to the two feature packages so the product endpoints keep their existing behaviour.
 */
@RestControllerAdvice(basePackages = {"edu.itc.shop.order", "edu.itc.shop.customer"})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CommonExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ProblemDetail> notFound(
            NotFoundException ex, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ProblemDetail> conflict(
            ConflictException ex, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ProblemDetail> invalidRequest(
            InvalidRequestException ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    /** {@code @Min} / {@code @Max} on request parameters, e.g. {@code size=500}. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ProblemDetail> parameterValidation(
            HandlerMethodValidationException ex, HttpServletRequest request) {
        List<String> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> result.getMethodParameter().getParameterName()
                                + ": " + error.getDefaultMessage()))
                .toList();

        ResponseEntity<ProblemDetail> response =
                error(HttpStatus.BAD_REQUEST, "Request validation failed", request);
        response.getBody().setProperty("errors", errors);
        return response;
    }

    /** A parameter that cannot be converted, e.g. {@code status=SHIPPED}. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> typeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST,
                "Invalid value '" + ex.getValue() + "' for parameter '" + ex.getName() + "'", request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> unreadableBody(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "Request body is missing or is not valid JSON", request);
    }

    private ResponseEntity<ProblemDetail> error(
            HttpStatus status, String message, HttpServletRequest request) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, message);
        body.setTitle(status.getReasonPhrase());
        body.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(status).body(body);
    }
}
