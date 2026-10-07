package com.omyfish.identity.adapter.out.scheduling;

import com.omyfish.identity.application.service.ReconciliationService;
import com.omyfish.identity.application.service.ReconciliationService.ReconciliationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Periodically runs ReconciliationService (BACKLOG item I.3) so a crash between a processor
 * call succeeding and the local save persisting its id gets repaired without an operator having
 * to notice and trigger AdminController's equivalent endpoint by hand.
 *
 * Single instance only, same assumption observation-service's OutboxPublisherJob makes — no
 * claim/lock step, so a second replica would run this concurrently. Lower-stakes here than
 * outbox's version of that caveat, since reconciling is idempotent either way.
 */
@Component
public class ReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationJob.class);

    private final ReconciliationService reconciliation;
    private final long lookbackHours;

    public ReconciliationJob(
        ReconciliationService reconciliation,
        @Value("${reconciliation.lookback-hours:24}") long lookbackHours
    ) {
        this.reconciliation = reconciliation;
        this.lookbackHours = lookbackHours;
    }

    @Scheduled(fixedDelayString = "${reconciliation.interval-ms:1800000}")
    public void run() {
        ReconciliationResult result = reconciliation.reconcile(Instant.now().minus(lookbackHours, ChronoUnit.HOURS));
        log.info("Reconciliation: checked {}, repaired {}, {} error(s)",
            result.checked(), result.repaired().size(), result.errors().size());
        result.errors().forEach(e -> log.warn("Reconciliation error: {}", e));
    }
}
