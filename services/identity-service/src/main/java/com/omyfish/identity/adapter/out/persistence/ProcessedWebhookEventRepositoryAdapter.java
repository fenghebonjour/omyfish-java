package com.omyfish.identity.adapter.out.persistence;

import com.omyfish.identity.domain.model.ProcessedWebhookEvent;
import com.omyfish.identity.domain.port.out.ProcessedWebhookEventRepository;
import org.springframework.stereotype.Component;

@Component
public class ProcessedWebhookEventRepositoryAdapter implements ProcessedWebhookEventRepository {

    private final ProcessedWebhookEventJpaRepository jpa;

    public ProcessedWebhookEventRepositoryAdapter(ProcessedWebhookEventJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public boolean existsById(String eventId) {
        return jpa.existsById(eventId);
    }

    @Override
    public void save(ProcessedWebhookEvent event) {
        jpa.save(event);
    }
}
