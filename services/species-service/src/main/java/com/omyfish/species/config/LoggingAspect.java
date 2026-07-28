package com.omyfish.species.config;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;

// Classic cross-cutting logging via Spring AOP: wraps every web controller and
// application-service method to log entry (with args), exit (with result + elapsed
// ms), and thrown exceptions — without any log.* calls leaking into the business
// code. The pointcuts target the adapter.in.web and application.service packages
// so domain/ stays framework-free (the aspect proxies those beans from here).
@Aspect
@Component
public class LoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(LoggingAspect.class);

    @Pointcut("within(com.omyfish.species.adapter.in.web..*) "
        + "|| within(com.omyfish.species.application.service..*)")
    public void loggable() {}

    @Around("loggable()")
    public Object logAround(ProceedingJoinPoint pjp) throws Throwable {
        String target = pjp.getSignature().getDeclaringType().getSimpleName()
            + "." + pjp.getSignature().getName();

        if (log.isDebugEnabled()) {
            log.debug("→ {}({})", target, Arrays.toString(pjp.getArgs()));
        }

        long start = System.nanoTime();
        try {
            Object result = pjp.proceed();
            long ms = (System.nanoTime() - start) / 1_000_000;
            log.info("← {} completed in {}ms", target, ms);
            return result;
        } catch (Throwable ex) {
            long ms = (System.nanoTime() - start) / 1_000_000;
            log.error("✗ {} failed after {}ms: {}", target, ms, ex.toString());
            throw ex;
        }
    }
}
