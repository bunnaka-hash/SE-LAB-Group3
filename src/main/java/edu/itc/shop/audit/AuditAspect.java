package edu.itc.shop.audit;

import java.lang.reflect.RecordComponent;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Logs who / what / when / outcome for every {@link Audited} method.
 * <p>
 * Highest precedence puts this advice outside the transaction, so {@code outcome=OK}
 * is only written once the commit has succeeded.
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

    // a scoped proxy: every call goes to the RequestContext of the current request
    private final RequestContext requestContext;
    private final Clock clock;

    public AuditAspect(RequestContext requestContext, Clock clock) {
        this.requestContext = requestContext;
        this.clock = clock;
    }

    @Around("@annotation(edu.itc.shop.audit.Audited)")
    public Object audit(ProceedingJoinPoint pjp) throws Throwable {
        boolean inRequest = RequestContextHolder.getRequestAttributes() != null;
        String who = inRequest ? requestContext.user() : "system";
        String request = inRequest ? requestContext.requestId() : "-";
        String what = pjp.getSignature().getDeclaringType().getSimpleName() + "."
                + pjp.getSignature().getName()
                + Arrays.stream(pjp.getArgs()).map(String::valueOf)
                        .collect(Collectors.joining(", ", "(", ")"));
        Instant when = clock.instant();
        long start = System.nanoTime();

        try {
            Object result = pjp.proceed();
            log.info("AUDIT who={} what={} when={} outcome=OK result={} ({} ms) request={}",
                    who, what, when, describe(result), millis(start), request);
            return result;
        } catch (Throwable ex) {
            log.warn("AUDIT who={} what={} when={} outcome=FAILED error={}: {} ({} ms) request={}",
                    who, what, when, ex.getClass().getSimpleName(), ex.getMessage(), millis(start), request);
            throw ex;
        }
    }

    /** {@code OrderDto(id=3)} for a record with an id, otherwise its toString(). */
    private static String describe(Object result) {
        if (result instanceof Record record) {
            for (RecordComponent component : record.getClass().getRecordComponents()) {
                if (component.getName().equals("id")) {
                    try {
                        return record.getClass().getSimpleName()
                                + "(id=" + component.getAccessor().invoke(record) + ")";
                    } catch (ReflectiveOperationException e) {
                        break;
                    }
                }
            }
        }
        return String.valueOf(result);
    }

    private static long millis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
