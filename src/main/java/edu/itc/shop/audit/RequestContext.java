package edu.itc.shop.audit;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * Who is calling and which request this is; one instance per HTTP request.
 * <p>
 * The request id is the one {@link edu.itc.shop.config.RequestIdFilter} put into the MDC.
 * There is no login in this lab, so the user comes from the {@code X-User} header.
 */
@Component
@RequestScope
public class RequestContext {

    private final String requestId;
    private final String user;

    public RequestContext(HttpServletRequest request) {
        String id = MDC.get("requestId");
        this.requestId = id != null ? id : UUID.randomUUID().toString().substring(0, 8);

        String header = request.getHeader("X-User");
        this.user = header != null && !header.isBlank()
                ? header.trim()
                : "anonymous@" + request.getRemoteAddr();
    }

    public String requestId() {
        return requestId;
    }

    public String user() {
        return user;
    }
}
