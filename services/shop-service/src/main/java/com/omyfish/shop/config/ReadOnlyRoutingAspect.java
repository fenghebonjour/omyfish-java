package com.omyfish.shop.config;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedRuntimeException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Marks the current thread for {@link ReadWriteRoutingDataSource} before the ReadOnly*UseCase
 * wrapper's own {@code @Transactional(readOnly = true)} advice runs — {@code
 * @Order(HIGHEST_PRECEDENCE)} makes this the outermost advisor, wrapping around Spring's
 * transaction interceptor (default order is lowest precedence) rather than inside it. That
 * ordering is what makes this reliable where reading {@code TransactionSynchronizationManager
 * .isCurrentTransactionReadOnly()} from inside {@link ReadWriteRoutingDataSource} is not, for
 * JPA specifically: JpaTransactionManager acquires the physical connection (to apply the
 * readOnly hint to it) during doBegin(), which runs before AbstractPlatformTransactionManager
 * sets that flag — so by the time the routing DataSource would see it, it's already too late.
 *
 * <p>Scoped to exactly the two ReadOnly*UseCase wrapper classes by execution pointcut rather
 * than matching every {@code @Transactional} method in the app (tried that first — it also
 * intercepted Spring Data JPA's own repository proxy methods and failed there with an AspectJ
 * argument-binding error) — this is deliberately narrow, not a general "any readOnly
 * transaction routes to the replica" mechanism.
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class ReadOnlyRoutingAspect {

    private static final Logger log = LoggerFactory.getLogger(ReadOnlyRoutingAspect.class);

    @Around("execution(* com.omyfish.shop.config.ReadOnly*UseCase.*(..))")
    public Object markReplicaForReadOnlyUseCases(ProceedingJoinPoint pjp) throws Throwable {
        ReadWriteRoutingDataSource.markReplicaForThisThread();
        try {
            return pjp.proceed();
        } catch (NestedRuntimeException replicaFailure) {
            // Spring's transaction (CannotCreateTransactionException) and DAO exception
            // hierarchies both extend NestedRuntimeException, so this only catches genuine
            // connection/query failures against the replica — not a business exception like
            // ProductNotFoundException, which is a plain RuntimeException and passes straight
            // through. A dead replica degrades to the primary picking up cache-miss traffic
            // instead of failing the request outright.
            log.warn("Replica unavailable ({}) — retrying this read against the primary",
                replicaFailure.getMessage());
            ReadWriteRoutingDataSource.clearForThisThread();
            return pjp.proceed();
        } finally {
            ReadWriteRoutingDataSource.clearForThisThread();
        }
    }
}
