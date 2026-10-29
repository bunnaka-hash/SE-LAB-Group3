package edu.itc.shop.service;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class TimingAspect {

    private static final Logger log = LoggerFactory.getLogger(TimingAspect.class);

    @Around("execution(public * edu.itc.shop.service..*(..)) && !within(edu.itc.shop.service.TimingAspect)")
    public Object time(ProceedingJoinPoint pjp) throws Throwable {
        if (!log.isDebugEnabled()) {        
            return pjp.proceed();
        }
        long start = System.nanoTime();
        try {
            return pjp.proceed();
        } finally {
            long micros = (System.nanoTime() - start) / 1_000;
            log.debug("{}.{}(..) took {} µs",
                    pjp.getSignature().getDeclaringType().getSimpleName(),
                    pjp.getSignature().getName(),
                    micros);
        }
    }
}