package com.omyfish.shop.domain.port.in;

import java.util.Map;

public interface ConfirmPaymentWebhookUseCase {
    // Returns true if a known event was applied, false for an unrecognized/ignored event type.
    boolean handleWebhook(String processor, String payload, Map<String, String> headers);
}
