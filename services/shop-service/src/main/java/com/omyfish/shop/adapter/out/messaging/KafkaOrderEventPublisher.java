package com.omyfish.shop.adapter.out.messaging;

import com.omyfish.shared.events.OrderPlacedEvent;
import com.omyfish.shop.domain.model.Order;
import com.omyfish.shop.domain.port.out.OrderEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaOrderEventPublisher implements OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaOrderEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public KafkaOrderEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publishOrderPlaced(Order order) {
        OrderPlacedEvent event = new OrderPlacedEvent(
            order.getId(), order.getUserId(), order.getGuestEmail(), order.getTotalCents(),
            order.getLines().stream()
                .map(l -> new OrderPlacedEvent.Line(l.productId(), l.productName(), l.quantity(), l.unitPriceCents()))
                .toList()
        );
        // The webhook that confirms payment has already saved the order by the time this runs —
        // the confirmation email and audit ledger are side effects of that, not preconditions
        // for it, so a publish failure here is logged and swallowed rather than failing the
        // webhook response.
        try {
            kafkaTemplate.send(OrderPlacedEvent.TOPIC, event.partitionKey().toString(), event);
        } catch (RuntimeException e) {
            log.error("Failed to publish OrderPlacedEvent for order {}: {}", order.getId(), e.getMessage(), e);
        }
    }
}
