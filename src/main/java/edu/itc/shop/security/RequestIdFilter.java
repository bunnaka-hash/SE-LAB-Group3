package edu.itc.shop.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every request an id (MDC key "rid", response header X-Request-Id) so the
 * log lines of one request can be correlated. NOT a @Component: it is created
 * inside the security chain only, otherwise it would also run as a servlet filter.
 * The id is always generated server-side; a client-supplied value is ignored so
 * nobody can inject text into our logs.
 */
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String MDC_KEY = "rid";
    public static final String HEADER = "X-Request-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String rid = UUID.randomUUID().toString().substring(0, 8);
        MDC.put(MDC_KEY, rid);
        response.setHeader(HEADER, rid);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
