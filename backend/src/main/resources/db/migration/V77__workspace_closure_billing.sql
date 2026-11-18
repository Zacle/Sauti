-- When a workspace closes, Sauti cancels its subscription and add-ons with the billing provider.
-- These record that the cancellation request was accepted, so a retry never sends it twice.
ALTER TABLE billing_subscriptions ADD COLUMN closure_cancel_requested_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE billing_add_on_subscriptions ADD COLUMN closure_cancel_requested_at TIMESTAMP WITH TIME ZONE;
