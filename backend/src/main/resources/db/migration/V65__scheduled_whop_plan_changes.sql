ALTER TABLE billing_plan_change_requests
    ADD COLUMN provider_invoice_id VARCHAR(100);

ALTER TABLE billing_plan_change_requests
    ADD COLUMN provider_target_plan_id VARCHAR(100);

ALTER TABLE billing_plan_change_requests
    ADD COLUMN provider_generated_plan_id VARCHAR(100);

CREATE UNIQUE INDEX idx_billing_plan_change_requests_generated_plan
    ON billing_plan_change_requests (provider_generated_plan_id);
