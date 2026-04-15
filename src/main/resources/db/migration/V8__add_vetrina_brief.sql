CREATE TABLE IF NOT EXISTS sito.vetrina_brief (
    project_id UUID PRIMARY KEY REFERENCES core.web_project(id) ON DELETE CASCADE,
    site_goal TEXT CHECK (site_goal IN (
        'presentazione_azienda',
        'raccolta_contatti',
        'richiesta_preventivi',
        'prenotazioni',
        'download_brochure',
        'supporto_commerciale'
    )),
    page_count INTEGER CHECK (page_count IS NULL OR page_count >= 0),
    requested_pages TEXT,
    homepage_sections TEXT,
    primary_cta TEXT CHECK (primary_cta IN (
        'contattaci',
        'richiedi_preventivo',
        'prenota_chiamata',
        'vieni_in_sede',
        'scarica_brochure'
    )),
    has_portfolio BOOLEAN,
    has_testimonials BOOLEAN,
    has_faq BOOLEAN,
    has_brochure BOOLEAN,
    has_blog BOOLEAN,
    needs_about_page BOOLEAN,
    needs_where_page BOOLEAN,
    needs_services_page BOOLEAN,
    needs_contact_form BOOLEAN,
    needs_external_links BOOLEAN,
    contact_form_email TEXT,
    has_separate_shop BOOLEAN,
    separate_shop_url TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO sito.vetrina_brief (
    project_id,
    requested_pages,
    needs_about_page,
    needs_where_page,
    needs_services_page,
    needs_contact_form,
    needs_external_links,
    contact_form_email,
    has_separate_shop,
    separate_shop_url
)
SELECT
    sb.project_id,
    sb.requested_menu,
    sb.needs_about_page,
    sb.needs_where_page,
    sb.needs_services_page,
    sb.needs_contact_form,
    sb.needs_external_links,
    sb.contact_form_email,
    sb.has_existing_ecommerce,
    sb.existing_ecommerce_url
FROM sito.site_brief sb
JOIN core.web_project wp ON wp.id = sb.project_id
WHERE wp.project_kind = 'vetrina'
ON CONFLICT (project_id) DO NOTHING;

DROP TRIGGER IF EXISTS trg_vetrina_brief_updated_at ON sito.vetrina_brief;
CREATE TRIGGER trg_vetrina_brief_updated_at
    BEFORE UPDATE ON sito.vetrina_brief
    FOR EACH ROW EXECUTE FUNCTION core.set_updated_at();
