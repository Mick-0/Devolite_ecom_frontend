BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE SCHEMA IF NOT EXISTS core;
CREATE SCHEMA IF NOT EXISTS media;
CREATE SCHEMA IF NOT EXISTS anagrafica;
CREATE SCHEMA IF NOT EXISTS crm;
CREATE SCHEMA IF NOT EXISTS onboarding;
CREATE SCHEMA IF NOT EXISTS legale;
CREATE SCHEMA IF NOT EXISTS brand;
CREATE SCHEMA IF NOT EXISTS marketing;
CREATE SCHEMA IF NOT EXISTS sito;
CREATE SCHEMA IF NOT EXISTS dominio;
CREATE SCHEMA IF NOT EXISTS local_business;
CREATE SCHEMA IF NOT EXISTS commerciale;
CREATE SCHEMA IF NOT EXISTS ai_ops;
CREATE SCHEMA IF NOT EXISTS integrazioni;
CREATE SCHEMA IF NOT EXISTS ecommerce;

-- =========================
-- core
-- =========================
CREATE TABLE IF NOT EXISTS core.client_company (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    legal_name TEXT NOT NULL,
    industry TEXT,
    vat_number TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_client_company_vat
    ON core.client_company (vat_number)
    WHERE vat_number IS NOT NULL;

CREATE TABLE IF NOT EXISTS core.web_project (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES core.client_company(id) ON DELETE CASCADE,
    project_name TEXT NOT NULL,
    project_kind TEXT NOT NULL CHECK (project_kind IN ('vetrina', 'ecommerce')),
    expected_outcome TEXT,
    source_channel TEXT,
    status TEXT NOT NULL DEFAULT 'in_discovery'
        CHECK (status IN ('in_discovery', 'onboarding', 'in_production', 'delivered', 'archived')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_web_project_company ON core.web_project(company_id);
CREATE INDEX IF NOT EXISTS ix_web_project_status ON core.web_project(status);

CREATE TABLE IF NOT EXISTS core.staff_user (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name TEXT NOT NULL,
    email TEXT NOT NULL UNIQUE,
    phone TEXT,
    role TEXT NOT NULL CHECK (role IN ('sales', 'project_manager', 'amministrazione', 'ai', 'altro')),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS core.project_assignment (
    project_id UUID NOT NULL REFERENCES core.web_project(id) ON DELETE CASCADE,
    staff_user_id UUID NOT NULL REFERENCES core.staff_user(id) ON DELETE RESTRICT,
    assignment_role TEXT NOT NULL CHECK (assignment_role IN ('sales', 'project_manager', 'amministrazione', 'ai', 'cliente_supporto')),
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (project_id, staff_user_id, assignment_role)
);

-- =========================
-- media (upload comuni: logo, visura, contratto, csv, ecc.)
-- =========================
CREATE TABLE IF NOT EXISTS media.asset (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES core.web_project(id) ON DELETE CASCADE,
    asset_category TEXT NOT NULL
        CHECK (asset_category IN ('logo', 'documento_legale', 'grafica', 'immagine_prodotto', 'contratto_pdf', 'csv_prodotti', 'altro')),
    original_name TEXT NOT NULL,
    storage_path TEXT NOT NULL,
    mime_type TEXT,
    size_bytes BIGINT CHECK (size_bytes IS NULL OR size_bytes >= 0),
    generated_by_ai BOOLEAN NOT NULL DEFAULT FALSE,
    comment TEXT,
    uploaded_by_user_id UUID REFERENCES core.staff_user(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_asset_project ON media.asset(project_id);
CREATE INDEX IF NOT EXISTS ix_asset_category ON media.asset(asset_category);

-- =========================
-- anagrafica
-- =========================
CREATE TABLE IF NOT EXISTS anagrafica.company_profile (
    company_id UUID PRIMARY KEY REFERENCES core.client_company(id) ON DELETE CASCADE,
    street TEXT,
    city TEXT,
    province TEXT,
    postal_code TEXT,
    country_code CHAR(2) DEFAULT 'IT',
    category TEXT,
    founder_years INTEGER CHECK (founder_years IS NULL OR founder_years >= 0),
    annual_revenue NUMERIC(15, 2) CHECK (annual_revenue IS NULL OR annual_revenue >= 0),
    referral_source TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS anagrafica.company_contact (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES core.client_company(id) ON DELETE CASCADE,
    full_name TEXT NOT NULL,
    phone TEXT,
    phone_secondary TEXT,
    email TEXT,
    email_secondary TEXT,
    role_title TEXT,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_company_contact_company ON anagrafica.company_contact(company_id);
CREATE UNIQUE INDEX IF NOT EXISTS ux_company_primary_contact
    ON anagrafica.company_contact(company_id)
    WHERE is_primary = TRUE;

-- =========================
-- crm / pipeline
-- =========================
CREATE TABLE IF NOT EXISTS crm.crm_contact (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES core.client_company(id) ON DELETE CASCADE,
    first_name TEXT,
    last_name TEXT,
    phone TEXT,
    email TEXT,
    email_secondary TEXT,
    list_name TEXT,
    company_name_snapshot TEXT,
    sector TEXT,
    vat_number TEXT,
    notes TEXT,
    interest_temperature TEXT,
    first_call_date DATE,
    second_call_date DATE,
    cta TEXT,
    current_stage TEXT NOT NULL DEFAULT 'lead'
        CHECK (current_stage IN ('lead', 'prospect', 'cliente')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (company_id)
);

CREATE INDEX IF NOT EXISTS ix_crm_contact_stage ON crm.crm_contact(current_stage);

CREATE TABLE IF NOT EXISTS crm.pipeline_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contact_id UUID NOT NULL REFERENCES crm.crm_contact(id) ON DELETE CASCADE,
    from_stage TEXT CHECK (from_stage IS NULL OR from_stage IN ('lead', 'prospect', 'cliente')),
    to_stage TEXT NOT NULL CHECK (to_stage IN ('lead', 'prospect', 'cliente')),
    changed_by_user_id UUID REFERENCES core.staff_user(id) ON DELETE SET NULL,
    note TEXT,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_pipeline_event_contact ON crm.pipeline_event(contact_id);

CREATE TABLE IF NOT EXISTS crm.contact_list (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    list_name TEXT NOT NULL,
    source_file_name TEXT,
    created_by_user_id UUID REFERENCES core.staff_user(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    imported_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS ix_contact_list_name ON crm.contact_list(list_name);

CREATE TABLE IF NOT EXISTS crm.contact_list_member (
    list_id UUID NOT NULL REFERENCES crm.contact_list(id) ON DELETE CASCADE,
    contact_id UUID NOT NULL REFERENCES crm.crm_contact(id) ON DELETE CASCADE,
    PRIMARY KEY (list_id, contact_id)
);

-- =========================
-- onboarding (stato step + risposte generiche)
-- =========================
CREATE TABLE IF NOT EXISTS onboarding.step_catalog (
    step_code TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    sequence_no INTEGER NOT NULL CHECK (sequence_no > 0),
    applicable_to TEXT NOT NULL CHECK (applicable_to IN ('vetrina', 'ecommerce', 'both')),
    owner_role TEXT
);

INSERT INTO onboarding.step_catalog (step_code, title, sequence_no, applicable_to, owner_role) VALUES
    ('aspetti_legali', 'Aspetti legali', 1, 'both', 'sales'),
    ('logo_colori', 'Logo e colori', 2, 'both', 'project_manager'),
    ('marketing', 'Marketing', 3, 'both', 'project_manager'),
    ('brainstorming_cliente', 'Brainstorming con cliente', 4, 'both', 'project_manager'),
    ('dominio', 'Dominio', 5, 'both', 'project_manager'),
    ('google_my_business', 'Google My Business', 6, 'both', 'ai'),
    ('genera_contratto', 'Genera contratto', 7, 'both', 'project_manager'),
    ('amministrazione_esterna', 'Step amministrativo esterno', 8, 'both', 'amministrazione'),
    ('avvio_lettura_ai', 'Avvio lettura AI', 9, 'both', 'ai'),
    ('banner_grafiche_preview', 'Banner e grafiche preview sito', 10, 'both', 'project_manager'),
    ('gestione_testi_preview', 'Gestione testi preview sito', 11, 'both', 'project_manager'),
    ('gestione_footer', 'Gestione footer', 12, 'both', 'project_manager'),
    ('configuratore_tool_connessi', 'Configuratore tool connessi', 13, 'both', 'project_manager'),
    ('gestione_indicizzazione_ai', 'Gestione indicizzazione AI', 14, 'both', 'ai'),
    ('configuratore_api_tool_vetrina', 'Configuratore API/TOOL', 15, 'vetrina', 'project_manager'),
    ('gestione_pagamenti', 'Gestione pagamenti su sito', 15, 'ecommerce', 'project_manager'),
    ('gestione_corrieri', 'Gestione corrieri per spedizioni', 16, 'ecommerce', 'project_manager'),
    ('gestione_categorie', 'Gestione categorie', 17, 'ecommerce', 'project_manager'),
    ('configuratore_api_tool_ecommerce', 'Configuratore API/TOOL', 18, 'ecommerce', 'project_manager')
ON CONFLICT (step_code) DO NOTHING;

CREATE UNIQUE INDEX IF NOT EXISTS ux_step_catalog_seq_app
    ON onboarding.step_catalog(sequence_no, applicable_to);

CREATE TABLE IF NOT EXISTS onboarding.project_step (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES core.web_project(id) ON DELETE CASCADE,
    step_code TEXT NOT NULL REFERENCES onboarding.step_catalog(step_code) ON DELETE RESTRICT,
    status TEXT NOT NULL DEFAULT 'todo'
        CHECK (status IN ('todo', 'in_progress', 'done', 'blocked')),
    owner_user_id UUID REFERENCES core.staff_user(id) ON DELETE SET NULL,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    note TEXT,
    UNIQUE (project_id, step_code),
    CHECK (completed_at IS NULL OR started_at IS NULL OR completed_at >= started_at)
);

CREATE INDEX IF NOT EXISTS ix_project_step_project ON onboarding.project_step(project_id);

CREATE TABLE IF NOT EXISTS onboarding.question_answer (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES core.web_project(id) ON DELETE CASCADE,
    step_code TEXT NOT NULL REFERENCES onboarding.step_catalog(step_code) ON DELETE RESTRICT,
    question_code TEXT NOT NULL,
    question_label TEXT NOT NULL,
    answer_text TEXT,
    answer_bool BOOLEAN,
    answer_json JSONB,
    answered_by_contact_id UUID REFERENCES anagrafica.company_contact(id) ON DELETE SET NULL,
    answered_by_user_id UUID REFERENCES core.staff_user(id) ON DELETE SET NULL,
    answered_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (answer_text IS NOT NULL OR answer_bool IS NOT NULL OR answer_json IS NOT NULL),
    UNIQUE (project_id, step_code, question_code)
);

CREATE INDEX IF NOT EXISTS ix_question_answer_project ON onboarding.question_answer(project_id);

-- =========================
-- legale
-- =========================
CREATE TABLE IF NOT EXISTS legale.legal_profile (
    project_id UUID PRIMARY KEY REFERENCES core.web_project(id) ON DELETE CASCADE,
    legal_support_mode TEXT
        CHECK (legal_support_mode IN ('consulente_cliente', 'integrazione_iubenda', 'da_definire')),
    vat_number TEXT,
    rea_number TEXT,
    share_capital NUMERIC(15, 2) CHECK (share_capital IS NULL OR share_capital >= 0),
    pec_email TEXT,
    privacy_page_completed BOOLEAN NOT NULL DEFAULT FALSE,
    terms_conditions_completed BOOLEAN NOT NULL DEFAULT FALSE,
    footer_cta TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS legale.legal_document (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES core.web_project(id) ON DELETE CASCADE,
    document_type TEXT NOT NULL
        CHECK (document_type IN ('visura', 'privacy_policy', 'termini_condizioni', 'altro')),
    asset_id UUID REFERENCES media.asset(id) ON DELETE SET NULL,
    notes TEXT,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_legal_document_project ON legale.legal_document(project_id);

-- =========================
-- branding / immagini / colori
-- =========================
CREATE TABLE IF NOT EXISTS brand.identity_profile (
    project_id UUID PRIMARY KEY REFERENCES core.web_project(id) ON DELETE CASCADE,
    logo_asset_id UUID REFERENCES media.asset(id) ON DELETE SET NULL,
    logo_restyle_required BOOLEAN,
    logo_restyle_generated BOOLEAN,
    logo_approved BOOLEAN,
    primary_color VARCHAR(16),
    secondary_color VARCHAR(16),
    accent_color_1 VARCHAR(16),
    accent_color_2 VARCHAR(16),
    font_policy TEXT CHECK (font_policy IN ('font_aziendali', 'da_selezionare')),
    visual_asset_source TEXT CHECK (visual_asset_source IN ('fornite_cliente', 'stock', 'misto')),
    tone_of_voice TEXT CHECK (tone_of_voice IN ('formale', 'amichevole', 'tecnico', 'emozionale', 'altro')),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (primary_color IS NULL OR primary_color ~ '^#([0-9A-Fa-f]{3}|[0-9A-Fa-f]{6})$'),
    CHECK (secondary_color IS NULL OR secondary_color ~ '^#([0-9A-Fa-f]{3}|[0-9A-Fa-f]{6})$'),
    CHECK (accent_color_1 IS NULL OR accent_color_1 ~ '^#([0-9A-Fa-f]{3}|[0-9A-Fa-f]{6})$'),
    CHECK (accent_color_2 IS NULL OR accent_color_2 ~ '^#([0-9A-Fa-f]{3}|[0-9A-Fa-f]{6})$')
);

CREATE TABLE IF NOT EXISTS brand.graphic_asset (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES core.web_project(id) ON DELETE CASCADE,
    asset_id UUID NOT NULL REFERENCES media.asset(id) ON DELETE CASCADE,
    asset_scope TEXT NOT NULL CHECK (asset_scope IN ('preview_banner', 'footer', 'pagina', 'prodotto', 'altro')),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (project_id, asset_id)
);

CREATE INDEX IF NOT EXISTS ix_graphic_asset_project ON brand.graphic_asset(project_id);

-- =========================
-- marketing
-- =========================
CREATE TABLE IF NOT EXISTS marketing.profile (
    project_id UUID PRIMARY KEY REFERENCES core.web_project(id) ON DELETE CASCADE,
    has_crm BOOLEAN,
    knows_crm BOOLEAN,
    runs_ads BOOLEAN,
    tracking_ga4 BOOLEAN,
    tracking_meta_pixel BOOLEAN,
    tracking_tiktok_pixel BOOLEAN,
    notes TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS marketing.ad_channel (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES core.web_project(id) ON DELETE CASCADE,
    channel_name TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (project_id, channel_name)
);

CREATE TABLE IF NOT EXISTS marketing.marketplace_channel (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES core.web_project(id) ON DELETE CASCADE,
    marketplace_name TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (project_id, marketplace_name)
);

-- =========================
-- sito / brainstorming / testi / pagine
-- =========================
CREATE TABLE IF NOT EXISTS sito.site_brief (
    project_id UUID PRIMARY KEY REFERENCES core.web_project(id) ON DELETE CASCADE,
    inspiration_sites TEXT,
    requested_menu TEXT,
    needs_about_page BOOLEAN,
    needs_where_page BOOLEAN,
    needs_services_page BOOLEAN,
    needs_contact_form BOOLEAN,
    needs_external_links BOOLEAN,
    contact_form_email TEXT,
    copy_mode TEXT CHECK (copy_mode IN ('scrivi_cliente', 'genera_ai', 'misto')),
    page_test_status TEXT
        CHECK (page_test_status IN ('non_iniziato', 'in_test', 'completato')),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS sito.page (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES core.web_project(id) ON DELETE CASCADE,
    page_key TEXT NOT NULL,
    page_title TEXT NOT NULL,
    is_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    UNIQUE (project_id, page_key)
);

CREATE TABLE IF NOT EXISTS sito.page_content (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    page_id UUID NOT NULL REFERENCES sito.page(id) ON DELETE CASCADE,
    content_type TEXT NOT NULL CHECK (content_type IN ('testo', 'cta', 'html', 'json')),
    content_text TEXT,
    generated_by_ai BOOLEAN NOT NULL DEFAULT FALSE,
    version_no INTEGER NOT NULL DEFAULT 1 CHECK (version_no > 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_page_content_page ON sito.page_content(page_id);

-- =========================
-- dominio
-- =========================
CREATE TABLE IF NOT EXISTS dominio.domain_setup (
    project_id UUID PRIMARY KEY REFERENCES core.web_project(id) ON DELETE CASCADE,
    has_existing_domain BOOLEAN,
    existing_domain TEXT,
    domain_to_register TEXT,
    domain_purchase_started_at TIMESTAMPTZ,
    domain_purchase_completed_at TIMESTAMPTZ,
    preferred_mailbox TEXT,
    mailbox_mode TEXT CHECK (mailbox_mode IN ('fornita_cliente', 'genera')),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (domain_purchase_completed_at IS NULL OR domain_purchase_started_at IS NULL OR domain_purchase_completed_at >= domain_purchase_started_at)
);

-- =========================
-- google my business / keywords
-- =========================
CREATE TABLE IF NOT EXISTS local_business.google_business_setup (
    project_id UUID PRIMARY KEY REFERENCES core.web_project(id) ON DELETE CASCADE,
    has_profile BOOLEAN,
    profile_url TEXT,
    profile_creation_requested BOOLEAN,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS local_business.keyword (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES core.web_project(id) ON DELETE CASCADE,
    keywords TEXT NOT NULL,
    source TEXT NOT NULL DEFAULT 'google_business'
        CHECK (source IN ('google_business', 'seo', 'altro')),
    UNIQUE (project_id, keywords)
);

CREATE INDEX IF NOT EXISTS ix_keyword_project ON local_business.keyword(project_id);

-- =========================
-- commerciale / contratti / pagamenti amministrativi
-- =========================
CREATE TABLE IF NOT EXISTS commerciale.contract (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES core.web_project(id) ON DELETE CASCADE,
    contact_name TEXT,
    contact_email TEXT,
    asset_pdf_id UUID REFERENCES media.asset(id) ON DELETE SET NULL,
    status TEXT NOT NULL DEFAULT 'bozza'
        CHECK (status IN ('bozza', 'inviato', 'firmato', 'annullato')),
    sent_at TIMESTAMPTZ,
    signed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (signed_at IS NULL OR sent_at IS NULL OR signed_at >= sent_at)
);

CREATE INDEX IF NOT EXISTS ix_contract_project ON commerciale.contract(project_id);

CREATE TABLE IF NOT EXISTS commerciale.order_administration (
    project_id UUID PRIMARY KEY REFERENCES core.web_project(id) ON DELETE CASCADE,
    purchased_service TEXT CHECK (purchased_service IN ('vetrina', 'ecommerce')),
    payment_received BOOLEAN,
    paid_amount NUMERIC(12, 2) CHECK (paid_amount IS NULL OR paid_amount >= 0),
    payment_marked_at TIMESTAMPTZ,
    invoice_generated BOOLEAN,
    invoice_number TEXT,
    auto_invoice_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS commerciale.payment_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES core.web_project(id) ON DELETE CASCADE,
    amount NUMERIC(12, 2) NOT NULL CHECK (amount >= 0),
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    note TEXT,
    recorded_by_user_id UUID REFERENCES core.staff_user(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS ix_payment_event_project ON commerciale.payment_event(project_id);

-- =========================
-- ai operations
-- =========================
CREATE TABLE IF NOT EXISTS ai_ops.project_ai_settings (
    project_id UUID PRIMARY KEY REFERENCES core.web_project(id) ON DELETE CASCADE,
    initial_analysis_started_at TIMESTAMPTZ,
    content_research_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    seo_indexing_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    seo_credits_total INTEGER NOT NULL DEFAULT 0 CHECK (seo_credits_total >= 0),
    seo_credits_used INTEGER NOT NULL DEFAULT 0 CHECK (seo_credits_used >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (seo_credits_used <= seo_credits_total)
);

CREATE TABLE IF NOT EXISTS ai_ops.ai_activity (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES core.web_project(id) ON DELETE CASCADE,
    activity_type TEXT NOT NULL
        CHECK (activity_type IN ('analisi_iniziale', 'genera_immagini', 'ricerca_contenuti', 'ottimizza_schede_prodotto', 'indicizzazione_seo', 'altro')),
    status TEXT NOT NULL DEFAULT 'queued'
        CHECK (status IN ('queued', 'running', 'completed', 'failed')),
    input_ref TEXT,
    result_summary TEXT,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    CHECK (finished_at IS NULL OR started_at IS NULL OR finished_at >= started_at)
);

CREATE INDEX IF NOT EXISTS ix_ai_activity_project ON ai_ops.ai_activity(project_id);
CREATE INDEX IF NOT EXISTS ix_ai_activity_status ON ai_ops.ai_activity(status);

-- =========================
-- integrazioni / api key tool esterni
-- =========================
CREATE TABLE IF NOT EXISTS integrazioni.tool_catalog (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tool_code TEXT NOT NULL UNIQUE,
    tool_name TEXT NOT NULL,
    category TEXT,
    description TEXT
);

CREATE TABLE IF NOT EXISTS integrazioni.project_tool_connection (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES core.web_project(id) ON DELETE CASCADE,
    tool_id UUID NOT NULL REFERENCES integrazioni.tool_catalog(id) ON DELETE RESTRICT,
    api_key_ciphertext TEXT,
    api_secret_ciphertext TEXT,
    config_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    last_tested_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (project_id, tool_id)
);

CREATE INDEX IF NOT EXISTS ix_tool_connection_project ON integrazioni.project_tool_connection(project_id);

-- =========================
-- ecommerce (solo se project_kind = ecommerce)
-- =========================
CREATE TABLE IF NOT EXISTS ecommerce.store_setup (
    project_id UUID PRIMARY KEY REFERENCES core.web_project(id) ON DELETE CASCADE,
    purchase_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    auto_renewal_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    rid_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    csv_import_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    csv_import_instructions_sent_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS ecommerce.accepted_payment_method (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES core.web_project(id) ON DELETE CASCADE,
    method_code TEXT NOT NULL,
    display_name TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (project_id, method_code)
);

CREATE TABLE IF NOT EXISTS ecommerce.accepted_carrier (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES core.web_project(id) ON DELETE CASCADE,
    carrier_code TEXT NOT NULL,
    display_name TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (project_id, carrier_code)
);

CREATE TABLE IF NOT EXISTS ecommerce.product_category (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES core.web_project(id) ON DELETE CASCADE,
    parent_category_id UUID REFERENCES ecommerce.product_category(id) ON DELETE SET NULL,
    name TEXT NOT NULL,
    slug TEXT NOT NULL,
    description TEXT,
    sort_order INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (project_id, slug)
);

CREATE INDEX IF NOT EXISTS ix_product_category_project ON ecommerce.product_category(project_id);

CREATE TABLE IF NOT EXISTS ecommerce.product_import_job (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES core.web_project(id) ON DELETE CASCADE,
    source_asset_id UUID REFERENCES media.asset(id) ON DELETE SET NULL,
    status TEXT NOT NULL DEFAULT 'received'
        CHECK (status IN ('received', 'processing', 'completed', 'failed')),
    rows_total INTEGER CHECK (rows_total IS NULL OR rows_total >= 0),
    rows_imported INTEGER CHECK (rows_imported IS NULL OR rows_imported >= 0),
    error_log TEXT,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at TIMESTAMPTZ,
    CHECK (processed_at IS NULL OR processed_at >= received_at)
);

CREATE INDEX IF NOT EXISTS ix_product_import_job_project ON ecommerce.product_import_job(project_id);
CREATE INDEX IF NOT EXISTS ix_product_import_job_status ON ecommerce.product_import_job(status);

-- =========================
-- trigger comune per updated_at
-- =========================
CREATE OR REPLACE FUNCTION core.set_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_client_company_updated_at ON core.client_company;
CREATE TRIGGER trg_client_company_updated_at
    BEFORE UPDATE ON core.client_company
    FOR EACH ROW EXECUTE FUNCTION core.set_updated_at();

DROP TRIGGER IF EXISTS trg_web_project_updated_at ON core.web_project;
CREATE TRIGGER trg_web_project_updated_at
    BEFORE UPDATE ON core.web_project
    FOR EACH ROW EXECUTE FUNCTION core.set_updated_at();

DROP TRIGGER IF EXISTS trg_company_profile_updated_at ON anagrafica.company_profile;
CREATE TRIGGER trg_company_profile_updated_at
    BEFORE UPDATE ON anagrafica.company_profile
    FOR EACH ROW EXECUTE FUNCTION core.set_updated_at();

DROP TRIGGER IF EXISTS trg_company_contact_updated_at ON anagrafica.company_contact;
CREATE TRIGGER trg_company_contact_updated_at
    BEFORE UPDATE ON anagrafica.company_contact
    FOR EACH ROW EXECUTE FUNCTION core.set_updated_at();

DROP TRIGGER IF EXISTS trg_crm_contact_updated_at ON crm.crm_contact;
CREATE TRIGGER trg_crm_contact_updated_at
    BEFORE UPDATE ON crm.crm_contact
    FOR EACH ROW EXECUTE FUNCTION core.set_updated_at();

DROP TRIGGER IF EXISTS trg_legal_profile_updated_at ON legale.legal_profile;
CREATE TRIGGER trg_legal_profile_updated_at
    BEFORE UPDATE ON legale.legal_profile
    FOR EACH ROW EXECUTE FUNCTION core.set_updated_at();

DROP TRIGGER IF EXISTS trg_brand_profile_updated_at ON brand.identity_profile;
CREATE TRIGGER trg_brand_profile_updated_at
    BEFORE UPDATE ON brand.identity_profile
    FOR EACH ROW EXECUTE FUNCTION core.set_updated_at();

DROP TRIGGER IF EXISTS trg_marketing_profile_updated_at ON marketing.profile;
CREATE TRIGGER trg_marketing_profile_updated_at
    BEFORE UPDATE ON marketing.profile
    FOR EACH ROW EXECUTE FUNCTION core.set_updated_at();

DROP TRIGGER IF EXISTS trg_site_brief_updated_at ON sito.site_brief;
CREATE TRIGGER trg_site_brief_updated_at
    BEFORE UPDATE ON sito.site_brief
    FOR EACH ROW EXECUTE FUNCTION core.set_updated_at();

DROP TRIGGER IF EXISTS trg_page_content_updated_at ON sito.page_content;
CREATE TRIGGER trg_page_content_updated_at
    BEFORE UPDATE ON sito.page_content
    FOR EACH ROW EXECUTE FUNCTION core.set_updated_at();

DROP TRIGGER IF EXISTS trg_domain_setup_updated_at ON dominio.domain_setup;
CREATE TRIGGER trg_domain_setup_updated_at
    BEFORE UPDATE ON dominio.domain_setup
    FOR EACH ROW EXECUTE FUNCTION core.set_updated_at();

DROP TRIGGER IF EXISTS trg_google_business_setup_updated_at ON local_business.google_business_setup;
CREATE TRIGGER trg_google_business_setup_updated_at
    BEFORE UPDATE ON local_business.google_business_setup
    FOR EACH ROW EXECUTE FUNCTION core.set_updated_at();

DROP TRIGGER IF EXISTS trg_order_admin_updated_at ON commerciale.order_administration;
CREATE TRIGGER trg_order_admin_updated_at
    BEFORE UPDATE ON commerciale.order_administration
    FOR EACH ROW EXECUTE FUNCTION core.set_updated_at();

DROP TRIGGER IF EXISTS trg_ai_settings_updated_at ON ai_ops.project_ai_settings;
CREATE TRIGGER trg_ai_settings_updated_at
    BEFORE UPDATE ON ai_ops.project_ai_settings
    FOR EACH ROW EXECUTE FUNCTION core.set_updated_at();

DROP TRIGGER IF EXISTS trg_store_setup_updated_at ON ecommerce.store_setup;
CREATE TRIGGER trg_store_setup_updated_at
    BEFORE UPDATE ON ecommerce.store_setup
    FOR EACH ROW EXECUTE FUNCTION core.set_updated_at();

COMMIT;
