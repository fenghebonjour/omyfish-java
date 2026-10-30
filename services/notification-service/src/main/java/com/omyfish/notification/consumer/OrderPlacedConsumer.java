package com.omyfish.notification.consumer;

import com.omyfish.notification.model.Notification;
import com.omyfish.notification.repository.NotificationRepository;
import com.omyfish.shared.events.OrderPlacedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderPlacedConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderPlacedConsumer.class);

    private final NotificationRepository repository;

    public OrderPlacedConsumer(NotificationRepository repository) {
        this.repository = repository;
    }

    @KafkaListener(topics = OrderPlacedEvent.TOPIC, groupId = "notification-service")
    public void handle(OrderPlacedEvent event) {
        if (event.userId() == null) {
            // Guest orders (guestEmail, no account) have no user to attach an in-app
            // notification row to — this model is scoped to account notifications, not a
            // general email-delivery service. Emailing a guest directly is a separate concern
            // outside this demo's scope.
            log.info("Order {} is a guest checkout — skipping in-app notification", event.orderId());
            return;
        }

        if (repository.existsBySourceEventId(event.eventId())) {
            log.info("Duplicate delivery for event {} — already processed, skipping", event.eventId());
            return;
        }

        Notification notification = new Notification(
            event.userId(),
            "ORDER_PLACED",
            "Order confirmed",
            "Your order " + event.orderId() + " has been confirmed.",
            event.eventId()
        );
        repository.save(notification);
        log.info("Notification persisted: id={} userId={} orderId={}",
            notification.getId(), notification.getUserId(), event.orderId());
    }
}
