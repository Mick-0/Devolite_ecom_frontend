alter table marketing.profile
    add column if not exists crm_system_url text,
    add column if not exists ga4_property_url text,
    add column if not exists google_ads_url text,
    add column if not exists meta_business_url text,
    add column if not exists tiktok_ads_url text,
    add column if not exists linkedin_ads_url text,
    add column if not exists amazon_store_url text,
    add column if not exists ebay_store_url text,
    add column if not exists manomano_store_url text,
    add column if not exists zalando_store_url text,
    add column if not exists other_marketing_links text,
    add column if not exists other_marketplace_links text;
