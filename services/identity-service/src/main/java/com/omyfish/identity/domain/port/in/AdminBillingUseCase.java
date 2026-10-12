package com.omyfish.identity.domain.port.in;

import com.omyfish.identity.application.service.BillingService;
import com.omyfish.identity.domain.model.Subscription;
import com.omyfish.identity.domain.port.out.PaymentPort;

import java.util.List;
import java.util.UUID;

public interface AdminBillingUseCase {

    BillingService.Stats stats();

    List<Subscription> allSubscriptions();

    Subscription grant(UUID userId, String plan, int days);

    Subscription revoke(UUID userId);

    PaymentPort.RefundResult refund(UUID userId, Long amountCents, String idempotencyKey);

    Subscription extendTrial(UUID userId, int days);
}
