package com.omyfish.shop.adapter.in.messaging;

import com.omyfish.shared.events.OrderPlacedEvent;
import com.omyfish.shop.adapter.out.persistence.OrderEventLedgerJpaEntity;
import com.omyfish.shop.adapter.out.persistence.OrderEventLedgerJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Builds the replayable reservation ledger from shop-service's own OrderPlacedEvent stream —
 * a separate consumer group from notification-service's, so a slow/failing email send can never
 * hold up this audit write (and vice versa). One row per order line, since the event is keyed
 * and partitioned per productId (OrderPlacedEvent.partitionKey()) for the same reason.
 */
@Component
public class OrderEventLedgerConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventLedgerConsumer.class);

    private final OrderEventLedgerJpaRepository repository;

    public OrderEventLedgerConsumer(OrderEventLedgerJpaRepository repository) {
        this.repository = repository;
    }

    @KafkaListener(topics = OrderPlacedEvent.TOPIC, groupId = "shop-service-ledger")
    public void handle(OrderPlacedEvent event) {
        int inserted = 0;
        for (OrderPlacedEvent.Line line : event.lines()) {
            // Kafka's at-least-once delivery means this event can be redelivered — dedup per
            // (event, product) rather than skipping the whole event on a partial failure.
            if (repository.existsByEventIdAndProductId(event.eventId(), line.productId())) {
                continue;
            }
            repository.save(new OrderEventLedgerJpaEntity(
                event.eventId(), event.orderId(), line.productId(), line.productName(),
                line.quantity(), line.unitPriceCents()));
            inserted++;
        }
        if (inserted > 0) {
            log.info("Ledger recorded {} new line(s) for order {}", inserted, event.orderId());
        } else {
            log.info("Duplicate delivery for event {} — already recorded, skipping", event.eventId());
        }
    }
}
