-- Multi-acquirer support: records which processor (stripe/paypal/adyen) owns an order's
-- stripe_session_id/stripe_payment_intent_id, matching identity-service's V6 for subscriptions.
ALTER TABLE shop.orders ADD COLUMN payment_processor VARCHAR(20);

UPDATE shop.orders
SET payment_processor = 'stripe'
WHERE stripe_session_id IS NOT NULL;
