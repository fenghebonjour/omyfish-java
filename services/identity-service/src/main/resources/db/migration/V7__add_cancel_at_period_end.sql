-- Stripe's default portal cancellation schedules cancellation at period end rather than
-- canceling immediately: the subscription stays status=active until then. Without this
-- flag the app can't tell that apart from a subscription that will actually renew.
ALTER TABLE identity.subscriptions
    ADD COLUMN cancel_at_period_end BOOLEAN NOT NULL DEFAULT FALSE;
