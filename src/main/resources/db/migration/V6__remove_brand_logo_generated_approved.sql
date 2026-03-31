ALTER TABLE brand.identity_profile
    DROP COLUMN IF EXISTS logo_restyle_generated,
    DROP COLUMN IF EXISTS logo_approved;
