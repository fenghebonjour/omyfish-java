package com.omyfish.shop.domain.model.valueobject;

// Captured from Stripe Checkout's shipping-address collection once payment completes — not
// known at order-creation time, only once the buyer has filled it in on Stripe's hosted page.
public record ShippingAddress(
    String recipientName, String line1, String line2, String city, String state,
    String postalCode, String country
) {}
