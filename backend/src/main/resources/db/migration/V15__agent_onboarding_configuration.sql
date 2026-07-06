ALTER TABLE agents ADD COLUMN business_type VARCHAR(100);
ALTER TABLE agents ADD COLUMN primary_use_case VARCHAR(100);
ALTER TABLE agents ADD COLUMN business_website VARCHAR(500);
ALTER TABLE agents ADD COLUMN bookable_services TEXT;
ALTER TABLE agents ADD COLUMN calendar_provider VARCHAR(100);
ALTER TABLE agents ADD COLUMN routing_policy VARCHAR(100);
ALTER TABLE agents ADD COLUMN voice_profile VARCHAR(200);
