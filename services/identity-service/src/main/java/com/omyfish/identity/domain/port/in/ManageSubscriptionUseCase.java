package com.omyfish.identity.domain.port.in;

import com.omyfish.identity.domain.model.Subscription;
import com.omyfish.identity.domain.port.out.PaymentPort;

import java.util.Optional;
import java.util.UUID;

public interface ManageSubscriptionUseCase {

    Subscription mySubscription(UUID userId);

    /** Empty when the subscription's processor has no portal (PayPal/Adyen) or isn't configured. */
    Optional<String> createPortalSession(UUID userId, String returnUrl);

    /** Empty when no payment processor is configured. */
    Optional<PaymentPort.SubscriptionIntent> startCheckout(UUID userId, String plan, String idempotencyKey);

    /** Empty when no payment processor is configured. */
    Optional<PaymentPort.SetupIntentResult> startPaymentMethodSetup(UUID userId);
}
