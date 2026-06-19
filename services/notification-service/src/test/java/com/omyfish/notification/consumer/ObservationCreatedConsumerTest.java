package com.omyfish.notification.consumer;

import com.omyfish.notification.model.Notification;
import com.omyfish.notification.repository.NotificationRepository;
import com.omyfish.shared.events.ObservationCreatedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ObservationCreatedConsumerTest {

    @Mock NotificationRepository repository;
    @InjectMocks ObservationCreatedConsumer consumer;

    @Test
    void handle_persistsNotificationForUser() {
        UUID userId = UUID.randomUUID();
        ObservationCreatedEvent event = new ObservationCreatedEvent(
            UUID.randomUUID(), userId, "Atlantic Salmon", null, null,
            "fish-images/test.jpg", Instant.now()
        );

        consumer.handle(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());

        Notification saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getType()).isEqualTo("OBSERVATION_CREATED");
        assertThat(saved.getTitle()).contains("Atlantic Salmon");
        assertThat(saved.isRead()).isFalse();
        assertThat(saved.getId()).isNotNull();
    }
}
