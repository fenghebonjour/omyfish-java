package com.omyfish.identity.domain.port.out;

import com.omyfish.identity.domain.model.ProcessedWebhookEvent;

public interface ProcessedWebhookEventRepository {
    boolean existsById(String eventId);
    void save(ProcessedWebhookEvent event);
}
