package com.omyfish.identity.domain.port.in;

import com.omyfish.identity.domain.port.out.PaymentPort.PaymentEvent;

public interface ApplyPaymentEventUseCase {
    boolean applyEvent(PaymentEvent event);
}
