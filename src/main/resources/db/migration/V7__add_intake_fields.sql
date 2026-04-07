ALTER TABLE anagrafica.company_profile
    ADD COLUMN IF NOT EXISTS has_physical_store BOOLEAN;

ALTER TABLE sito.site_brief
    ADD COLUMN IF NOT EXISTS has_existing_ecommerce BOOLEAN,
    ADD COLUMN IF NOT EXISTS existing_ecommerce_url TEXT;

ALTER TABLE ecommerce.store_setup
    ADD COLUMN IF NOT EXISTS product_count INTEGER,
    ADD COLUMN IF NOT EXISTS has_product_variants BOOLEAN;
