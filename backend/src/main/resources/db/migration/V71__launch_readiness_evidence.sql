ALTER TABLE platform_launch_readiness
    ADD COLUMN google_verification_reference VARCHAR(500);
ALTER TABLE platform_launch_readiness
    ADD COLUMN google_verified_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE platform_launch_readiness
    ADD COLUMN live_acceptance_evidence VARCHAR(2000);
ALTER TABLE platform_launch_readiness
    ADD COLUMN live_accepted_at TIMESTAMP WITH TIME ZONE;

-- Earlier releases stored only unexplained checkboxes. Reopen those manual
-- gates so an administrator must retain auditable evidence before GA approval.
UPDATE platform_launch_readiness
SET google_verification_completed = FALSE,
    live_acceptance_completed = FALSE,
    general_availability_approved = FALSE
WHERE google_verification_completed = TRUE
   OR live_acceptance_completed = TRUE
   OR general_availability_approved = TRUE;
