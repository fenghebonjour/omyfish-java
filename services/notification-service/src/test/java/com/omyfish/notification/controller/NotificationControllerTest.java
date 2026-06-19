package com.omyfish.notification.controller;

import com.omyfish.notification.model.Notification;
import com.omyfish.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = NotificationController.class,
    excludeAutoConfiguration = RabbitAutoConfiguration.class)
@TestPropertySource(properties = {
    "spring.flyway.enabled=false",
    "spring.rabbitmq.host=localhost"
})
class NotificationControllerTest {

    @Autowired MockMvc mvc;
    @MockBean NotificationRepository repository;

    private final UUID userId = UUID.randomUUID();

    @Test
    void list_returnsUsersNotifications() throws Exception {
        Notification n = new Notification(userId, "OBSERVATION_CREATED",
            "Fish identified: Atlantic Salmon", "Your observation was recorded.");
        when(repository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(n));

        mvc.perform(get("/api/v1/notifications")
                .header("X-User-Id", userId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].type").value("OBSERVATION_CREATED"))
            .andExpect(jsonPath("$[0].title").value("Fish identified: Atlantic Salmon"))
            .andExpect(jsonPath("$[0].read").value(false));
    }

    @Test
    void markRead_found_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        Notification n = new Notification(userId, "OBSERVATION_CREATED", "title", "body");
        when(repository.findByIdAndUserId(id, userId)).thenReturn(Optional.of(n));
        when(repository.save(any())).thenReturn(n);

        mvc.perform(put("/api/v1/notifications/{id}/read", id)
                .header("X-User-Id", userId.toString()))
            .andExpect(status().isOk());
    }

    @Test
    void markRead_notFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndUserId(id, userId)).thenReturn(Optional.empty());

        mvc.perform(put("/api/v1/notifications/{id}/read", id)
                .header("X-User-Id", userId.toString()))
            .andExpect(status().isNotFound());
    }
}
