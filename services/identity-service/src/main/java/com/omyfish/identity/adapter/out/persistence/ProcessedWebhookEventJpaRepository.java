package com.omyfish.identity.adapter.out.persistence;

import com.omyfish.identity.domain.model.ProcessedWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedWebhookEventJpaRepository extends JpaRepository<ProcessedWebhookEvent, String> {
}
