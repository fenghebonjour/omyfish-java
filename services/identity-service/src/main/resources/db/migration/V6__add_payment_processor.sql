-- Multi-acquirer support: records which processor (stripe/paypal/adyen) owns a
-- subscription's stripe_customer_id/stripe_subscription_id, and the reference needed
-- to refund the last captured payment for processors with no subscription object of
-- their own (e.g. Adyen's pspReference).
ALTER TABLE identity.subscriptions
    ADD COLUMN payment_processor VARCHAR(20),
    ADD COLUMN last_payment_reference VARCHAR(255);

UPDATE identity.subscriptions
SET payment_processor = 'stripe'
WHERE stripe_customer_id IS NOT NULL;
