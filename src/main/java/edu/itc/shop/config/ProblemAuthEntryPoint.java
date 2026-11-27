package edu.itc.shop.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class ProblemAuthEntryPoint implements AuthenticationEntryPoint {

    private final BearerTokenAuthenticationEntryPoint bearer = new BearerTokenAuthenticationEntryPoint();

    @Override
    public void commence(HttpServletRequest req, HttpServletResponse res,
                         AuthenticationException ex) throws IOException {
        bearer.commence(req, res, ex);   // status 401 + WWW-Authenticate header
        res.setContentType("application/problem+json");
        String detail = req.getHeader("Authorization") != null
                ? "Token expired or invalid" : "Authentication required";
        res.getWriter().write("""
            {"type":"about:blank","title":"Unauthorized","status":401,"detail":"%s","instance":"%s"}
            """.formatted(detail, req.getRequestURI()));
    }
}