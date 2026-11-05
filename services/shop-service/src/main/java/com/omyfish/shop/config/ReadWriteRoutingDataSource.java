package com.omyfish.shop.config;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Routes to the replica only when {@link ReadOnlyRoutingAspect} has explicitly marked the
 * current thread for it. That aspect, not {@link TransactionSynchronizationManager}'s own
 * readOnly flag, is the actual signal — see its class comment for why: JpaTransactionManager
 * acquires the physical connection (to apply the readOnly hint to it) during doBegin(), which
 * runs before AbstractPlatformTransactionManager sets that flag, so by the time this class
 * would see it, it's already too late. "No marker at all" (including Flyway's own migration
 * connections, which manage their transactions outside Spring entirely) deliberately defaults
 * to the primary — an ambiguous or absent context should never silently end up on the replica.
 */
class ReadWriteRoutingDataSource extends AbstractRoutingDataSource {

    static final String PRIMARY = "primary";
    static final String REPLICA = "replica";

    private static final ThreadLocal<Boolean> ROUTE_TO_REPLICA = new ThreadLocal<>();

    static void markReplicaForThisThread() {
        ROUTE_TO_REPLICA.set(Boolean.TRUE);
    }

    static void clearForThisThread() {
        ROUTE_TO_REPLICA.remove();
    }

    @Override
    protected Object determineCurrentLookupKey() {
        return Boolean.TRUE.equals(ROUTE_TO_REPLICA.get()) ? REPLICA : PRIMARY;
    }
}
