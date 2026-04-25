-- E-commerce: variants details + e-commerce admin panel info

ALTER TABLE ecommerce.store_setup
    ADD COLUMN IF NOT EXISTS variant_management_mode TEXT
        CHECK (variant_management_mode IS NULL OR variant_management_mode IN ('product_with_variants', 'separate_products')),
    ADD COLUMN IF NOT EXISTS variant_axes TEXT,
    ADD COLUMN IF NOT EXISTS variant_total_sku_count INTEGER
        CHECK (variant_total_sku_count IS NULL OR variant_total_sku_count >= 0),
    ADD COLUMN IF NOT EXISTS variant_separate_product_count INTEGER
        CHECK (variant_separate_product_count IS NULL OR variant_separate_product_count >= 0),
    ADD COLUMN IF NOT EXISTS variants_affect_price BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS variants_affect_stock BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS ecom_panel_platform TEXT,
    ADD COLUMN IF NOT EXISTS ecom_panel_url TEXT,
    ADD COLUMN IF NOT EXISTS ecom_panel_has_credentials BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS ecom_panel_credential_email TEXT,
    ADD COLUMN IF NOT EXISTS ecom_panel_credential_username TEXT,
    ADD COLUMN IF NOT EXISTS ecom_panel_credential_secret TEXT,
    ADD COLUMN IF NOT EXISTS ecom_panel_two_factor_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS ecom_panel_notes TEXT;

