package com.omyfish.shop.domain.model;

import com.omyfish.shop.domain.exception.InvalidOrderStateException;
import com.omyfish.shop.domain.model.valueobject.OrderLine;
import com.omyfish.shop.domain.model.valueobject.ShippingAddress;
import com.omyfish.shared.domain.Entity;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class Order extends Entity<UUID> {

    public static final Duration RETURN_WINDOW = Duration.ofDays(30);

    private final UUID userId;
    private final String guestEmail;
    private final List<OrderLine> lines;
    private final long totalCents;
    private OrderStatus status;
    private String stripeSessionId;
    private String stripePaymentIntentId;
    // Which PaymentPort adapter owns this order's ids above — those fields keep their Stripe
    // name for the same reason identity-service's Subscription.stripeCustomerId/
    // stripeSubscriptionId do post-multi-acquirer: no behavioral upside to renaming them, this
    // field is what actually disambiguates whose id format they hold (PayPal order/capture id,
    // Adyen reference/pspReference, etc).
    private String paymentProcessor;
    private final Instant createdAt;
    private Instant paidAt;
    private Instant returnRequestedAt;
    private Instant returnedAt;
    private String returnReason;
    private ShippingAddress shippingAddress;

    private Order(UUID id, UUID userId, String guestEmail, List<OrderLine> lines, long totalCents, OrderStatus status,
                  String stripeSessionId, String stripePaymentIntentId, String paymentProcessor, Instant createdAt,
                  Instant paidAt, Instant returnRequestedAt, Instant returnedAt, String returnReason,
                  ShippingAddress shippingAddress) {
        super(id);
        if ((userId == null) == (guestEmail == null)) {
            throw new IllegalArgumentException("Order requires exactly one of userId or guestEmail");
        }
        this.userId = userId;
        this.guestEmail = guestEmail;
        this.lines = lines;
        this.totalCents = totalCents;
        this.status = status;
        this.stripeSessionId = stripeSessionId;
        this.stripePaymentIntentId = stripePaymentIntentId;
        this.paymentProcessor = paymentProcessor;
        this.createdAt = createdAt;
        this.paidAt = paidAt;
        this.returnRequestedAt = returnRequestedAt;
        this.returnedAt = returnedAt;
        this.returnReason = returnReason;
        this.shippingAddress = shippingAddress;
    }

    public static Order createPendingPayment(UUID userId, String guestEmail, List<OrderLine> lines) {
        long total = lines.stream().mapToLong(OrderLine::subtotalCents).sum();
        return new Order(UUID.randomUUID(), userId, guestEmail, lines, total, OrderStatus.PENDING_PAYMENT,
            null, null, null, Instant.now(), null, null, null, null, null);
    }

    public static Order reconstitute(UUID id, UUID userId, String guestEmail, List<OrderLine> lines, long totalCents,
                                      OrderStatus status, String stripeSessionId, String stripePaymentIntentId,
                                      String paymentProcessor, Instant createdAt, Instant paidAt,
                                      Instant returnRequestedAt, Instant returnedAt, String returnReason,
                                      ShippingAddress shippingAddress) {
        return new Order(id, userId, guestEmail, lines, totalCents, status, stripeSessionId, stripePaymentIntentId,
            paymentProcessor, createdAt, paidAt, returnRequestedAt, returnedAt, returnReason, shippingAddress);
    }

    public void attachCheckoutSession(String stripeSessionId, String paymentProcessor) {
        this.stripeSessionId = stripeSessionId;
        this.paymentProcessor = paymentProcessor;
    }

    public void markPaid(String stripePaymentIntentId, ShippingAddress shippingAddress) {
        requireStatus(OrderStatus.PENDING_PAYMENT);
        this.status = OrderStatus.PAID;
        this.stripePaymentIntentId = stripePaymentIntentId;
        this.paidAt = Instant.now();
        this.shippingAddress = shippingAddress;
    }

    public void markCancelled() {
        requireStatus(OrderStatus.PENDING_PAYMENT);
        this.status = OrderStatus.CANCELLED;
    }

    public void requestReturn(String reason) {
        requireStatus(OrderStatus.PAID);
        if (paidAt == null || Instant.now().isAfter(paidAt.plus(RETURN_WINDOW))) {
            throw new InvalidOrderStateException(getId(), "return window has expired");
        }
        this.status = OrderStatus.RETURN_REQUESTED;
        this.returnRequestedAt = Instant.now();
        this.returnReason = reason;
    }

    public void approveReturn() {
        requireStatus(OrderStatus.RETURN_REQUESTED);
        this.status = OrderStatus.RETURNED;
        this.returnedAt = Instant.now();
    }

    private void requireStatus(OrderStatus required) {
        if (status != required) {
            throw new InvalidOrderStateException(getId(),
                "expected status " + required + " but was " + status);
        }
    }

    public UUID getUserId() { return userId; }
    public String getGuestEmail() { return guestEmail; }
    public List<OrderLine> getLines() { return lines; }
    public long getTotalCents() { return totalCents; }
    public OrderStatus getStatus() { return status; }
    public String getStripeSessionId() { return stripeSessionId; }
    public String getStripePaymentIntentId() { return stripePaymentIntentId; }
    public String getPaymentProcessor() { return paymentProcessor; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPaidAt() { return paidAt; }
    public Instant getReturnRequestedAt() { return returnRequestedAt; }
    public Instant getReturnedAt() { return returnedAt; }
    public String getReturnReason() { return returnReason; }
    public ShippingAddress getShippingAddress() { return shippingAddress; }
}
