package com.omyfish.notification.controller;

import com.omyfish.notification.model.Notification;
import com.omyfish.notification.repository.NotificationRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationRepository repository;

    public NotificationController(NotificationRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Notification> list(@RequestHeader("X-User-Id") String userIdHeader) {
        return repository.findByUserIdOrderByCreatedAtDesc(UUID.fromString(userIdHeader));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markRead(
        @PathVariable("id") UUID id,
        @RequestHeader("X-User-Id") String userIdHeader
    ) {
        var found = repository.findByIdAndUserId(id, UUID.fromString(userIdHeader));
        if (found.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Notification n = found.get();
        n.markRead();
        repository.save(n);
        return ResponseEntity.ok().build();
    }
}
