package edu.itc.shop.web;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import edu.itc.shop.service.ProductNotFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ProblemDetail> notFound(ProductNotFoundException ex, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, ex.getMessage(), request).build();
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> invalid(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> errors.putIfAbsent(e.getField(), e.getDefaultMessage()));
        ProblemBuilder b = problem(HttpStatus.BAD_REQUEST, "Validation failed", request);
        b.body.setProperty("errors", errors);
        return b.build();
    }

    /** Called by the security entry point (401). Keeps WWW-Authenticate so curl -u and browsers work. */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> unauthorized(AuthenticationException ex, HttpServletRequest request) {
        ProblemBuilder b = problem(HttpStatus.UNAUTHORIZED, "Authentication required", request);
        b.response.header(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"Realm\"");
        return b.build();
    }

    /** Called by the access-denied handler (403), and later by method security. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> forbidden(AccessDeniedException ex, HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, "Access denied", request).build();
    }

    private static ProblemBuilder problem(HttpStatus status, String detail, HttpServletRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(URI.create("about:blank"));
        pd.setInstance(URI.create(request.getRequestURI()));
        return new ProblemBuilder(pd, ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    private record ProblemBuilder(ProblemDetail body, ResponseEntity.BodyBuilder response) {
        ResponseEntity<ProblemDetail> build() { return response.body(body); }
    }
}
