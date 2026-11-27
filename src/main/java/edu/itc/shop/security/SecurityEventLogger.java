package edu.itc.shop.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authorization.event.AuthorizationDeniedEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.stereotype.Component;

/** Task 5: authentication / authorization audit trail on the logger named SECURITY. */
@Component
public class SecurityEventLogger {

    private static final Logger log = LoggerFactory.getLogger("SECURITY");

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent e) {
        Authentication a = e.getAuthentication();
        if (a instanceof UsernamePasswordAuthenticationToken) {
            log.info("login.ok user={} type={}", a.getName(), a.getClass().getSimpleName());
        } else {
            // Bearer-token authentication happens on every API call: keep it out of INFO
            log.debug("token.ok user={} type={}", a.getName(), a.getClass().getSimpleName());
        }
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent e) {
        Authentication a = e.getAuthentication();
        // never log a bearer token: its "name" is the raw token string
        String user = a instanceof BearerTokenAuthenticationToken ? "<bearer-token>" : a.getName();
        log.warn("login.failed user={} reason={}", user, e.getException().getClass().getSimpleName());
    }

    @EventListener
    public void onDenied(AuthorizationDeniedEvent<?> e) {
        Authentication a = e.getAuthentication().get();
        log.warn("access.denied user={} object={}", a.getName(), describe(e.getObject()));
    }

    private static String describe(Object object) {
        if (object instanceof MethodInvocation mi) {
            // numbers (ids) are shown, anything else is elided so no user data reaches the log
            String args = Arrays.stream(mi.getArguments())
                    .map(arg -> arg instanceof Number ? String.valueOf(arg) : "…")
                    .collect(Collectors.joining(","));
            return mi.getMethod().getDeclaringClass().getSimpleName()
                    + "." + mi.getMethod().getName() + "(" + args + ")";
        }
        if (object instanceof HttpServletRequest req) {      // URL-level denial
            return req.getMethod() + " " + req.getRequestURI();
        }
        return object == null ? "null" : object.getClass().getSimpleName();
    }
}
