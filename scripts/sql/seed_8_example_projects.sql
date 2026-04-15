BEGIN;

-- Seed di esempio per DBeaver
-- Crea 8 progetti: 4 vetrina + 4 e-commerce
-- Script idempotente: puo essere rilanciato senza duplicare i record principali.

INSERT INTO core.client_company (id, legal_name, industry, vat_number)
VALUES
    ('10000000-0000-0000-0000-000000000001', 'Studio Riva Srl', 'Servizi professionali', 'IT10000000001'),
    ('10000000-0000-0000-0000-000000000002', 'Marea Design Srl', 'Arredamento', 'IT10000000002'),
    ('10000000-0000-0000-0000-000000000003', 'Officina Verdi Srl', 'Automotive', 'IT10000000003'),
    ('10000000-0000-0000-0000-000000000004', 'Clinica Aurora Srl', 'Salute e benessere', 'IT10000000004'),
    ('10000000-0000-0000-0000-000000000005', 'Acme Fashion Srl', 'Abbigliamento', 'IT10000000005'),
    ('10000000-0000-0000-0000-000000000006', 'PetGarden Srl', 'Pet care', 'IT10000000006'),
    ('10000000-0000-0000-0000-000000000007', 'TerraBio Market Srl', 'Food retail', 'IT10000000007'),
    ('10000000-0000-0000-0000-000000000008', 'TechNest Parts Srl', 'Elettronica', 'IT10000000008')
ON CONFLICT (id) DO UPDATE SET
    legal_name = EXCLUDED.legal_name,
    industry = EXCLUDED.industry,
    vat_number = EXCLUDED.vat_number;

INSERT INTO anagrafica.company_profile (
    company_id, street, city, province, postal_code, country_code, category,
    founder_years, annual_revenue, referral_source, has_physical_store
)
VALUES
    ('10000000-0000-0000-0000-000000000001', 'Via Manzoni 12', 'Milano', 'MI', '20121', 'IT', 'Studio legale', 2014, 780000.00, 'Passaparola', false),
    ('10000000-0000-0000-0000-000000000002', 'Via Brera 8', 'Milano', 'MI', '20121', 'IT', 'Interior design', 2017, 1250000.00, 'Instagram', true),
    ('10000000-0000-0000-0000-000000000003', 'Via del Lavoro 44', 'Bologna', 'BO', '40127', 'IT', 'Officina meccanica', 2009, 940000.00, 'Google', true),
    ('10000000-0000-0000-0000-000000000004', 'Viale Europa 20', 'Padova', 'PD', '35129', 'IT', 'Poliambulatorio', 2016, 1680000.00, 'Referral medico', true),
    ('10000000-0000-0000-0000-000000000005', 'Via Torino 91', 'Milano', 'MI', '20123', 'IT', 'Abbigliamento', 2018, 3200000.00, 'Evento settore', true),
    ('10000000-0000-0000-0000-000000000006', 'Via delle Rose 7', 'Verona', 'VR', '37121', 'IT', 'Pet shop', 2020, 690000.00, 'Facebook Ads', true),
    ('10000000-0000-0000-0000-000000000007', 'Via San Donato 55', 'Firenze', 'FI', '50121', 'IT', 'Alimentari bio', 2015, 2100000.00, 'Passaparola', true),
    ('10000000-0000-0000-0000-000000000008', 'Via Galilei 18', 'Torino', 'TO', '10121', 'IT', 'Componenti tech', 2013, 4100000.00, 'LinkedIn', false)
ON CONFLICT (company_id) DO UPDATE SET
    street = EXCLUDED.street,
    city = EXCLUDED.city,
    province = EXCLUDED.province,
    postal_code = EXCLUDED.postal_code,
    country_code = EXCLUDED.country_code,
    category = EXCLUDED.category,
    founder_years = EXCLUDED.founder_years,
    annual_revenue = EXCLUDED.annual_revenue,
    referral_source = EXCLUDED.referral_source,
    has_physical_store = EXCLUDED.has_physical_store;

INSERT INTO anagrafica.company_contact (
    id, company_id, full_name, phone, phone_secondary, email, email_secondary,
    role_title, is_primary, notes
)
VALUES
    ('30000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'Luca Riva', '+39 02 4400111', '+39 348 1000001', 'luca.riva@studioriva.it', 'info@studioriva.it', 'Managing Partner', true, 'Preferisce call al mattino.'),
    ('30000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000002', 'Martina Sala', '+39 02 5500222', '+39 348 1000002', 'martina@marea-design.it', 'studio@marea-design.it', 'Creative Director', true, 'Molto attenta alla parte visual.'),
    ('30000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000003', 'Marco Verdi', '+39 051 6600333', '+39 348 1000003', 'marco@officinaverdi.it', 'assistenza@officinaverdi.it', 'Titolare', true, 'Vuole ricevere report semplici.'),
    ('30000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000004', 'Elisa Bassi', '+39 049 7700444', '+39 348 1000004', 'elisa.bassi@clinicaaurora.it', 'segreteria@clinicaaurora.it', 'Direzione sanitaria', true, 'Coinvolgere anche la reception.'),
    ('30000000-0000-0000-0000-000000000005', '10000000-0000-0000-0000-000000000005', 'Giulia Rota', '+39 02 8800555', '+39 348 1000005', 'giulia@acmefashion.it', 'operations@acmefashion.it', 'E-commerce Manager', true, 'Vuole partire con collezione primavera.'),
    ('30000000-0000-0000-0000-000000000006', '10000000-0000-0000-0000-000000000006', 'Davide Neri', '+39 045 9900666', '+39 348 1000006', 'davide@petgarden.it', 'shop@petgarden.it', 'Founder', true, 'Interessato a marketing automation.'),
    ('30000000-0000-0000-0000-000000000007', '10000000-0000-0000-0000-000000000007', 'Sara Guidi', '+39 055 2200777', '+39 348 1000007', 'sara@terrabio.it', 'amministrazione@terrabio.it', 'CEO', true, 'Ha gia un piccolo catalogo online da migrare.'),
    ('30000000-0000-0000-0000-000000000008', '10000000-0000-0000-0000-000000000008', 'Alberto Greco', '+39 011 3300888', '+39 348 1000008', 'alberto@technestparts.it', 'sales@technestparts.it', 'Commercial Director', true, 'Focalizzato su lead B2B e ordine rapido.')
ON CONFLICT (id) DO UPDATE SET
    full_name = EXCLUDED.full_name,
    phone = EXCLUDED.phone,
    phone_secondary = EXCLUDED.phone_secondary,
    email = EXCLUDED.email,
    email_secondary = EXCLUDED.email_secondary,
    role_title = EXCLUDED.role_title,
    is_primary = EXCLUDED.is_primary,
    notes = EXCLUDED.notes;

INSERT INTO core.web_project (id, company_id, project_name, project_kind, expected_outcome, status)
VALUES
    ('20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'Vetrina Studio Riva 2026', 'vetrina', 'Generare richieste consulenza dal sito', 'onboarding'),
    ('20000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000002', 'Vetrina Marea Design 2026', 'vetrina', 'Valorizzare portfolio e acquisire lead premium', 'onboarding'),
    ('20000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000003', 'Vetrina Officina Verdi 2026', 'vetrina', 'Aumentare richieste di preventivo officina', 'in_discovery'),
    ('20000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000004', 'Vetrina Clinica Aurora 2026', 'vetrina', 'Portare nuovi appuntamenti da traffico locale', 'onboarding'),
    ('20000000-0000-0000-0000-000000000005', '10000000-0000-0000-0000-000000000005', 'E-commerce Acme Fashion 2026', 'ecommerce', 'Vendere online la nuova collezione', 'in_production'),
    ('20000000-0000-0000-0000-000000000006', '10000000-0000-0000-0000-000000000006', 'E-commerce PetGarden 2026', 'ecommerce', 'Aprire canale D2C con prodotti pet', 'onboarding'),
    ('20000000-0000-0000-0000-000000000007', '10000000-0000-0000-0000-000000000007', 'E-commerce TerraBio 2026', 'ecommerce', 'Migrare vendite bio su shop proprietario', 'onboarding'),
    ('20000000-0000-0000-0000-000000000008', '10000000-0000-0000-0000-000000000008', 'E-commerce TechNest 2026', 'ecommerce', 'Creare catalogo ricambi e ordini rapidi B2B', 'in_discovery')
ON CONFLICT (id) DO UPDATE SET
    company_id = EXCLUDED.company_id,
    project_name = EXCLUDED.project_name,
    project_kind = EXCLUDED.project_kind,
    expected_outcome = EXCLUDED.expected_outcome,
    status = EXCLUDED.status;

INSERT INTO crm.crm_contact (
    id, company_id, first_name, last_name, phone, email, email_secondary,
    list_name, company_name_snapshot, sector, vat_number, notes,
    interest_temperature, first_call_date, second_call_date, cta, current_stage
)
VALUES
    ('40000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'Luca', 'Riva', '+39 02 4400111', 'luca.riva@studioriva.it', 'info@studioriva.it', 'lead_t1', 'Studio Riva Srl', 'Legal services', 'IT10000000001', 'Vuole un sito sobrio ma autorevole.', 'caldo', CURRENT_DATE - 12, CURRENT_DATE - 5, 'Inviare proposta economica', 'prospect'),
    ('40000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000002', 'Martina', 'Sala', '+39 02 5500222', 'martina@marea-design.it', 'studio@marea-design.it', 'prospect_caldo', 'Marea Design Srl', 'Interior design', 'IT10000000002', 'Forte sensibilita visiva e branding.', 'bollente', CURRENT_DATE - 9, CURRENT_DATE - 2, 'Condividere moodboard iniziale', 'prospect'),
    ('40000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000003', 'Marco', 'Verdi', '+39 051 6600333', 'marco@officinaverdi.it', 'assistenza@officinaverdi.it', 'inbound_sito', 'Officina Verdi Srl', 'Automotive service', 'IT10000000003', 'Vuole lead locali e richieste da mobile.', 'tiepido', CURRENT_DATE - 14, CURRENT_DATE - 8, 'Preparare wireframe home', 'lead'),
    ('40000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000004', 'Elisa', 'Bassi', '+39 049 7700444', 'elisa.bassi@clinicaaurora.it', 'segreteria@clinicaaurora.it', 'partner_referral', 'Clinica Aurora Srl', 'Healthcare', 'IT10000000004', 'Servono pagine prestazioni e prenotazioni.', 'caldo', CURRENT_DATE - 11, CURRENT_DATE - 4, 'Condividere architettura pagine', 'prospect'),
    ('40000000-0000-0000-0000-000000000005', '10000000-0000-0000-0000-000000000005', 'Giulia', 'Rota', '+39 02 8800555', 'giulia@acmefashion.it', 'operations@acmefashion.it', 'clienti_attivi', 'Acme Fashion Srl', 'Retail fashion', 'IT10000000005', 'Catalogo ampio e promozioni stagionali.', 'bollente', CURRENT_DATE - 20, CURRENT_DATE - 14, 'Avviare setup shop', 'cliente'),
    ('40000000-0000-0000-0000-000000000006', '10000000-0000-0000-0000-000000000006', 'Davide', 'Neri', '+39 045 9900666', 'davide@petgarden.it', 'shop@petgarden.it', 'lead_t1', 'PetGarden Srl', 'Pet retail', 'IT10000000006', 'Vuole subscription e prodotti ricorrenti.', 'caldo', CURRENT_DATE - 10, CURRENT_DATE - 3, 'Inviare checklist dati prodotti', 'prospect'),
    ('40000000-0000-0000-0000-000000000007', '10000000-0000-0000-0000-000000000007', 'Sara', 'Guidi', '+39 055 2200777', 'sara@terrabio.it', 'amministrazione@terrabio.it', 'prospect_caldo', 'TerraBio Market Srl', 'Organic food retail', 'IT10000000007', 'Ha bisogno di migrazione dallo shop attuale.', 'caldo', CURRENT_DATE - 13, CURRENT_DATE - 6, 'Confermare feed categorie', 'prospect'),
    ('40000000-0000-0000-0000-000000000008', '10000000-0000-0000-0000-000000000008', 'Alberto', 'Greco', '+39 011 3300888', 'alberto@technestparts.it', 'sales@technestparts.it', 'partner_referral', 'TechNest Parts Srl', 'Electronics B2B', 'IT10000000008', 'Serve navigazione tecnica e ordine rapido.', 'freddo', CURRENT_DATE - 7, CURRENT_DATE - 1, 'Fare discovery catalogo', 'lead')
ON CONFLICT (company_id) DO UPDATE SET
    first_name = EXCLUDED.first_name,
    last_name = EXCLUDED.last_name,
    phone = EXCLUDED.phone,
    email = EXCLUDED.email,
    email_secondary = EXCLUDED.email_secondary,
    list_name = EXCLUDED.list_name,
    company_name_snapshot = EXCLUDED.company_name_snapshot,
    sector = EXCLUDED.sector,
    vat_number = EXCLUDED.vat_number,
    notes = EXCLUDED.notes,
    interest_temperature = EXCLUDED.interest_temperature,
    first_call_date = EXCLUDED.first_call_date,
    second_call_date = EXCLUDED.second_call_date,
    cta = EXCLUDED.cta,
    current_stage = EXCLUDED.current_stage;

INSERT INTO legale.legal_profile (
    project_id, legal_support_mode, vat_number, rea_number, share_capital,
    pec_email, privacy_page_completed, terms_conditions_completed, footer_cta
)
VALUES
    ('20000000-0000-0000-0000-000000000001', 'consulente_cliente', 'IT10000000001', 'MI-201401', 50000.00, 'pec@studioriva.it', true, true, 'Richiedi una consulenza'),
    ('20000000-0000-0000-0000-000000000002', 'da_definire', 'IT10000000002', 'MI-201702', 25000.00, 'pec@marea-design.it', false, false, 'Parla con il nostro studio'),
    ('20000000-0000-0000-0000-000000000003', 'integrazione_iubenda', 'IT10000000003', 'BO-200903', 40000.00, 'pec@officinaverdi.it', true, false, 'Prenota il tuo check'),
    ('20000000-0000-0000-0000-000000000004', 'consulente_cliente', 'IT10000000004', 'PD-201604', 60000.00, 'pec@clinicaaurora.it', true, true, 'Prenota una visita'),
    ('20000000-0000-0000-0000-000000000005', 'integrazione_iubenda', 'IT10000000005', 'MI-201805', 70000.00, 'pec@acmefashion.it', true, true, 'Scopri la collezione'),
    ('20000000-0000-0000-0000-000000000006', 'da_definire', 'IT10000000006', 'VR-202006', 15000.00, 'pec@petgarden.it', false, false, 'Acquista online'),
    ('20000000-0000-0000-0000-000000000007', 'consulente_cliente', 'IT10000000007', 'FI-201507', 55000.00, 'pec@terrabio.it', true, true, 'Ordina ora'),
    ('20000000-0000-0000-0000-000000000008', 'consulente_cliente', 'IT10000000008', 'TO-201308', 120000.00, 'pec@technestparts.it', true, true, 'Richiedi un preventivo')
ON CONFLICT (project_id) DO UPDATE SET
    legal_support_mode = EXCLUDED.legal_support_mode,
    vat_number = EXCLUDED.vat_number,
    rea_number = EXCLUDED.rea_number,
    share_capital = EXCLUDED.share_capital,
    pec_email = EXCLUDED.pec_email,
    privacy_page_completed = EXCLUDED.privacy_page_completed,
    terms_conditions_completed = EXCLUDED.terms_conditions_completed,
    footer_cta = EXCLUDED.footer_cta;

INSERT INTO brand.identity_profile (
    project_id, logo_restyle_required, primary_color, secondary_color,
    accent_color_1, accent_color_2, font_policy, visual_asset_source, tone_of_voice
)
VALUES
    ('20000000-0000-0000-0000-000000000001', false, '#443877', '#7D6EB0', '#FFD4DA', '#E8E0FF', 'font_aziendali', 'fornite_cliente', 'formale'),
    ('20000000-0000-0000-0000-000000000002', true, '#443877', '#7D6EB0', '#FFD4DA', '#F4EAFE', 'da_selezionare', 'misto', 'emozionale'),
    ('20000000-0000-0000-0000-000000000003', false, '#443877', '#7D6EB0', '#FFD4DA', '#DDD6F7', 'font_aziendali', 'fornite_cliente', 'tecnico'),
    ('20000000-0000-0000-0000-000000000004', false, '#443877', '#7D6EB0', '#FFD4DA', '#F7EAF0', 'font_aziendali', 'stock', 'amichevole'),
    ('20000000-0000-0000-0000-000000000005', true, '#443877', '#7D6EB0', '#FFD4DA', '#F1E8FF', 'da_selezionare', 'misto', 'emozionale'),
    ('20000000-0000-0000-0000-000000000006', false, '#443877', '#7D6EB0', '#FFD4DA', '#FCE7EC', 'font_aziendali', 'fornite_cliente', 'amichevole'),
    ('20000000-0000-0000-0000-000000000007', false, '#443877', '#7D6EB0', '#FFD4DA', '#F6EED8', 'font_aziendali', 'fornite_cliente', 'formale'),
    ('20000000-0000-0000-0000-000000000008', true, '#443877', '#7D6EB0', '#FFD4DA', '#E5E7FF', 'da_selezionare', 'stock', 'tecnico')
ON CONFLICT (project_id) DO UPDATE SET
    logo_restyle_required = EXCLUDED.logo_restyle_required,
    primary_color = EXCLUDED.primary_color,
    secondary_color = EXCLUDED.secondary_color,
    accent_color_1 = EXCLUDED.accent_color_1,
    accent_color_2 = EXCLUDED.accent_color_2,
    font_policy = EXCLUDED.font_policy,
    visual_asset_source = EXCLUDED.visual_asset_source,
    tone_of_voice = EXCLUDED.tone_of_voice;

INSERT INTO marketing.profile (
    project_id, has_crm, knows_crm, runs_ads, tracking_ga4, tracking_meta_pixel, tracking_tiktok_pixel, notes
)
VALUES
    ('20000000-0000-0000-0000-000000000001', true, true, false, true, false, false, 'Obiettivo lead qualificati da contatto diretto.'),
    ('20000000-0000-0000-0000-000000000002', true, true, true, true, true, false, 'Presenza forte su Instagram e campagne inspiration.'),
    ('20000000-0000-0000-0000-000000000003', false, false, true, true, true, false, 'Focalizzato su lead locali e servizi officina.'),
    ('20000000-0000-0000-0000-000000000004', true, true, true, true, true, true, 'Campagne locali su Google e Meta.'),
    ('20000000-0000-0000-0000-000000000005', true, true, true, true, true, true, 'Marketing omnicanale per lanci stagionali.'),
    ('20000000-0000-0000-0000-000000000006', false, false, true, true, true, true, 'TikTok e Meta per audience pet lovers.'),
    ('20000000-0000-0000-0000-000000000007', true, true, true, true, true, false, 'Campagne Google Shopping e newsletter.'),
    ('20000000-0000-0000-0000-000000000008', true, true, true, true, false, false, 'SEO e lead generation B2B.')
ON CONFLICT (project_id) DO UPDATE SET
    has_crm = EXCLUDED.has_crm,
    knows_crm = EXCLUDED.knows_crm,
    runs_ads = EXCLUDED.runs_ads,
    tracking_ga4 = EXCLUDED.tracking_ga4,
    tracking_meta_pixel = EXCLUDED.tracking_meta_pixel,
    tracking_tiktok_pixel = EXCLUDED.tracking_tiktok_pixel,
    notes = EXCLUDED.notes;

INSERT INTO sito.vetrina_brief (
    project_id, site_goal, page_count, requested_pages, homepage_sections, primary_cta,
    has_portfolio, has_testimonials, has_faq, has_brochure, has_blog,
    needs_about_page, needs_where_page, needs_services_page, needs_contact_form,
    needs_external_links, contact_form_email, has_separate_shop, separate_shop_url
)
VALUES
    ('20000000-0000-0000-0000-000000000001', 'raccolta_contatti', 6, 'Home
Chi siamo
Aree di attivita
Team
FAQ
Contatti', 'Hero con value proposition
Servizi principali
Aree di competenza
CTA consulenza
Footer legale', 'contattaci', false, true, true, false, true, true, false, true, true, false, 'contatti@studioriva.it', false, NULL),
    ('20000000-0000-0000-0000-000000000002', 'presentazione_azienda', 7, 'Home
Studio
Progetti
Servizi
Press
Blog
Contatti', 'Hero immersiva
Portfolio in evidenza
Metodo di lavoro
Testimonianze
CTA finale', 'richiedi_preventivo', true, true, false, true, true, true, false, true, true, true, 'hello@marea-design.it', false, NULL),
    ('20000000-0000-0000-0000-000000000003', 'richiesta_preventivi', 5, 'Home
Servizi
Dove siamo
Recensioni
Contatti', 'Hero mobile first
Servizi rapidi
Marchi trattati
Recensioni
Mappa e CTA', 'richiedi_preventivo', false, true, true, false, false, false, true, true, true, true, 'preventivi@officinaverdi.it', false, NULL),
    ('20000000-0000-0000-0000-000000000004', 'prenotazioni', 8, 'Home
Prestazioni
Equipe
Prenotazioni
Convenzioni
FAQ
Dove siamo
Contatti', 'Hero istituzionale
Prestazioni top
Equipe medica
Prenotazione rapida
FAQ', 'prenota_chiamata', false, true, true, true, true, true, true, true, true, false, 'prenotazioni@clinicaaurora.it', true, 'https://booking.clinicaaurora.it')
ON CONFLICT (project_id) DO UPDATE SET
    site_goal = EXCLUDED.site_goal,
    page_count = EXCLUDED.page_count,
    requested_pages = EXCLUDED.requested_pages,
    homepage_sections = EXCLUDED.homepage_sections,
    primary_cta = EXCLUDED.primary_cta,
    has_portfolio = EXCLUDED.has_portfolio,
    has_testimonials = EXCLUDED.has_testimonials,
    has_faq = EXCLUDED.has_faq,
    has_brochure = EXCLUDED.has_brochure,
    has_blog = EXCLUDED.has_blog,
    needs_about_page = EXCLUDED.needs_about_page,
    needs_where_page = EXCLUDED.needs_where_page,
    needs_services_page = EXCLUDED.needs_services_page,
    needs_contact_form = EXCLUDED.needs_contact_form,
    needs_external_links = EXCLUDED.needs_external_links,
    contact_form_email = EXCLUDED.contact_form_email,
    has_separate_shop = EXCLUDED.has_separate_shop,
    separate_shop_url = EXCLUDED.separate_shop_url;

INSERT INTO sito.site_brief (
    project_id, inspiration_sites, requested_menu, copy_mode, page_test_status,
    has_existing_ecommerce, existing_ecommerce_url
)
VALUES
    ('20000000-0000-0000-0000-000000000005', 'https://www.cos.com
https://www.arket.com', 'Home
Nuovi arrivi
Donna
Uomo
Accessori
Saldi
Chi siamo
Contatti', 'misto', 'in_test', true, 'https://shop.acmefashion.it'),
    ('20000000-0000-0000-0000-000000000006', 'https://www.zooplus.it
https://www.homelesspets.it', 'Home
Cani
Gatti
Integratori
Offerte
Blog
Contatti', 'genera_ai', 'non_iniziato', false, NULL),
    ('20000000-0000-0000-0000-000000000007', 'https://www.eataly.net
https://www.naturasi.it', 'Home
Dispensa
Fresco
Promo
Chi siamo
FAQ
Contatti', 'scrivi_cliente', 'in_test', true, 'https://shop.terrabio.it'),
    ('20000000-0000-0000-0000-000000000008', 'https://www.rs-online.com
https://www.digikey.it', 'Home
Catalogo
Brand
Richiedi offerta
Supporto
Contatti', 'misto', 'non_iniziato', false, NULL)
ON CONFLICT (project_id) DO UPDATE SET
    inspiration_sites = EXCLUDED.inspiration_sites,
    requested_menu = EXCLUDED.requested_menu,
    copy_mode = EXCLUDED.copy_mode,
    page_test_status = EXCLUDED.page_test_status,
    has_existing_ecommerce = EXCLUDED.has_existing_ecommerce,
    existing_ecommerce_url = EXCLUDED.existing_ecommerce_url;

INSERT INTO dominio.domain_setup (
    project_id, has_existing_domain, existing_domain, domain_to_register,
    domain_purchase_started_at, domain_purchase_completed_at, preferred_mailbox, mailbox_mode
)
VALUES
    ('20000000-0000-0000-0000-000000000001', true, 'studioriva.it', NULL, NOW() - INTERVAL '30 days', NOW() - INTERVAL '29 days', 'info@studioriva.it', 'fornita_cliente'),
    ('20000000-0000-0000-0000-000000000002', true, 'marea-design.it', NULL, NOW() - INTERVAL '22 days', NOW() - INTERVAL '21 days', 'studio@marea-design.it', 'fornita_cliente'),
    ('20000000-0000-0000-0000-000000000003', true, 'officinaverdi.it', NULL, NOW() - INTERVAL '18 days', NOW() - INTERVAL '17 days', 'assistenza@officinaverdi.it', 'fornita_cliente'),
    ('20000000-0000-0000-0000-000000000004', true, 'clinicaaurora.it', NULL, NOW() - INTERVAL '25 days', NOW() - INTERVAL '24 days', 'segreteria@clinicaaurora.it', 'fornita_cliente'),
    ('20000000-0000-0000-0000-000000000005', true, 'acmefashion.it', NULL, NOW() - INTERVAL '35 days', NOW() - INTERVAL '34 days', 'shop@acmefashion.it', 'fornita_cliente'),
    ('20000000-0000-0000-0000-000000000006', false, NULL, 'petgarden-shop.it', NOW() - INTERVAL '12 days', NOW() - INTERVAL '11 days', 'shop@petgarden.it', 'genera'),
    ('20000000-0000-0000-0000-000000000007', true, 'terrabio.it', NULL, NOW() - INTERVAL '16 days', NOW() - INTERVAL '15 days', 'ordini@terrabio.it', 'fornita_cliente'),
    ('20000000-0000-0000-0000-000000000008', false, NULL, 'technestparts.it', NOW() - INTERVAL '8 days', NOW() - INTERVAL '7 days', 'sales@technestparts.it', 'genera')
ON CONFLICT (project_id) DO UPDATE SET
    has_existing_domain = EXCLUDED.has_existing_domain,
    existing_domain = EXCLUDED.existing_domain,
    domain_to_register = EXCLUDED.domain_to_register,
    domain_purchase_started_at = EXCLUDED.domain_purchase_started_at,
    domain_purchase_completed_at = EXCLUDED.domain_purchase_completed_at,
    preferred_mailbox = EXCLUDED.preferred_mailbox,
    mailbox_mode = EXCLUDED.mailbox_mode;

INSERT INTO local_business.google_business_setup (project_id, has_profile, profile_url, profile_creation_requested)
VALUES
    ('20000000-0000-0000-0000-000000000001', true, 'https://g.page/studioriva', false),
    ('20000000-0000-0000-0000-000000000002', true, 'https://g.page/mareadesign', false),
    ('20000000-0000-0000-0000-000000000003', true, 'https://g.page/officinaverdi', false),
    ('20000000-0000-0000-0000-000000000004', true, 'https://g.page/clinicaaurora', false),
    ('20000000-0000-0000-0000-000000000005', true, 'https://g.page/acmefashion', false),
    ('20000000-0000-0000-0000-000000000006', false, NULL, true),
    ('20000000-0000-0000-0000-000000000007', true, 'https://g.page/terrabio', false),
    ('20000000-0000-0000-0000-000000000008', false, NULL, false)
ON CONFLICT (project_id) DO UPDATE SET
    has_profile = EXCLUDED.has_profile,
    profile_url = EXCLUDED.profile_url,
    profile_creation_requested = EXCLUDED.profile_creation_requested;

INSERT INTO commerciale.order_administration (
    project_id, purchased_service, payment_received, paid_amount,
    payment_marked_at, invoice_generated, invoice_number, auto_invoice_enabled
)
VALUES
    ('20000000-0000-0000-0000-000000000001', 'vetrina', true, 1800.00, NOW() - INTERVAL '10 days', true, 'FAT-2026-101', true),
    ('20000000-0000-0000-0000-000000000002', 'vetrina', true, 2400.00, NOW() - INTERVAL '7 days', true, 'FAT-2026-102', true),
    ('20000000-0000-0000-0000-000000000003', 'vetrina', false, 0.00, NULL, false, NULL, true),
    ('20000000-0000-0000-0000-000000000004', 'vetrina', true, 3200.00, NOW() - INTERVAL '5 days', true, 'FAT-2026-103', true),
    ('20000000-0000-0000-0000-000000000005', 'ecommerce', true, 6900.00, NOW() - INTERVAL '20 days', true, 'FAT-2026-104', true),
    ('20000000-0000-0000-0000-000000000006', 'ecommerce', true, 4200.00, NOW() - INTERVAL '6 days', true, 'FAT-2026-105', true),
    ('20000000-0000-0000-0000-000000000007', 'ecommerce', true, 5100.00, NOW() - INTERVAL '9 days', true, 'FAT-2026-106', true),
    ('20000000-0000-0000-0000-000000000008', 'ecommerce', false, 0.00, NULL, false, NULL, true)
ON CONFLICT (project_id) DO UPDATE SET
    purchased_service = EXCLUDED.purchased_service,
    payment_received = EXCLUDED.payment_received,
    paid_amount = EXCLUDED.paid_amount,
    payment_marked_at = EXCLUDED.payment_marked_at,
    invoice_generated = EXCLUDED.invoice_generated,
    invoice_number = EXCLUDED.invoice_number,
    auto_invoice_enabled = EXCLUDED.auto_invoice_enabled;

INSERT INTO ai_ops.project_ai_settings (
    project_id, initial_analysis_started_at, content_research_enabled,
    seo_indexing_enabled, seo_credits_total, seo_credits_used
)
VALUES
    ('20000000-0000-0000-0000-000000000001', NOW() - INTERVAL '8 days', true, true, 100, 12),
    ('20000000-0000-0000-0000-000000000002', NOW() - INTERVAL '6 days', true, true, 120, 24),
    ('20000000-0000-0000-0000-000000000003', NOW() - INTERVAL '4 days', true, false, 80, 7),
    ('20000000-0000-0000-0000-000000000004', NOW() - INTERVAL '3 days', true, true, 140, 40),
    ('20000000-0000-0000-0000-000000000005', NOW() - INTERVAL '18 days', true, true, 300, 95),
    ('20000000-0000-0000-0000-000000000006', NOW() - INTERVAL '5 days', true, true, 160, 18),
    ('20000000-0000-0000-0000-000000000007', NOW() - INTERVAL '7 days', true, true, 220, 61),
    ('20000000-0000-0000-0000-000000000008', NOW() - INTERVAL '2 days', true, false, 180, 9)
ON CONFLICT (project_id) DO UPDATE SET
    initial_analysis_started_at = EXCLUDED.initial_analysis_started_at,
    content_research_enabled = EXCLUDED.content_research_enabled,
    seo_indexing_enabled = EXCLUDED.seo_indexing_enabled,
    seo_credits_total = EXCLUDED.seo_credits_total,
    seo_credits_used = EXCLUDED.seo_credits_used;

INSERT INTO ecommerce.store_setup (
    project_id, purchase_enabled, auto_renewal_enabled, rid_enabled,
    csv_import_enabled, csv_import_instructions_sent_at, product_count, has_product_variants
)
VALUES
    ('20000000-0000-0000-0000-000000000005', true, false, false, true, NOW() - INTERVAL '16 days', 320, true),
    ('20000000-0000-0000-0000-000000000006', true, true, true, false, NULL, 140, true),
    ('20000000-0000-0000-0000-000000000007', true, false, false, true, NOW() - INTERVAL '8 days', 260, false),
    ('20000000-0000-0000-0000-000000000008', true, false, false, true, NOW() - INTERVAL '3 days', 980, true)
ON CONFLICT (project_id) DO UPDATE SET
    purchase_enabled = EXCLUDED.purchase_enabled,
    auto_renewal_enabled = EXCLUDED.auto_renewal_enabled,
    rid_enabled = EXCLUDED.rid_enabled,
    csv_import_enabled = EXCLUDED.csv_import_enabled,
    csv_import_instructions_sent_at = EXCLUDED.csv_import_instructions_sent_at,
    product_count = EXCLUDED.product_count,
    has_product_variants = EXCLUDED.has_product_variants;

INSERT INTO ecommerce.accepted_payment_method (id, project_id, method_code, display_name, enabled)
VALUES
    (gen_random_uuid(), '20000000-0000-0000-0000-000000000005', 'card', 'Carta', true),
    (gen_random_uuid(), '20000000-0000-0000-0000-000000000005', 'paypal', 'PayPal', true),
    (gen_random_uuid(), '20000000-0000-0000-0000-000000000005', 'bonifico', 'Bonifico', true),
    (gen_random_uuid(), '20000000-0000-0000-0000-000000000006', 'card', 'Carta', true),
    (gen_random_uuid(), '20000000-0000-0000-0000-000000000006', 'paypal', 'PayPal', true),
    (gen_random_uuid(), '20000000-0000-0000-0000-000000000006', 'cash', 'Contanti', true),
    (gen_random_uuid(), '20000000-0000-0000-0000-000000000007', 'card', 'Carta', true),
    (gen_random_uuid(), '20000000-0000-0000-0000-000000000007', 'bonifico', 'Bonifico', true),
    (gen_random_uuid(), '20000000-0000-0000-0000-000000000008', 'card', 'Carta', true),
    (gen_random_uuid(), '20000000-0000-0000-0000-000000000008', 'bonifico', 'Bonifico', true)
ON CONFLICT (project_id, method_code) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    enabled = EXCLUDED.enabled;

INSERT INTO ecommerce.accepted_carrier (id, project_id, carrier_code, display_name, enabled)
VALUES
    (gen_random_uuid(), '20000000-0000-0000-0000-000000000005', 'gls', 'GLS', true),
    (gen_random_uuid(), '20000000-0000-0000-0000-000000000005', 'dhl', 'DHL', true),
    (gen_random_uuid(), '20000000-0000-0000-0000-000000000006', 'bartolini', 'Bartolini', true),
    (gen_random_uuid(), '20000000-0000-0000-0000-000000000006', 'sda', 'SDA', true),
    (gen_random_uuid(), '20000000-0000-0000-0000-000000000007', 'gls', 'GLS', true),
    (gen_random_uuid(), '20000000-0000-0000-0000-000000000008', 'dhl', 'DHL', true),
    (gen_random_uuid(), '20000000-0000-0000-0000-000000000008', 'bartolini', 'Bartolini', true)
ON CONFLICT (project_id, carrier_code) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    enabled = EXCLUDED.enabled;

INSERT INTO ecommerce.product_category (id, project_id, name, slug, sort_order, active)
VALUES
    (gen_random_uuid(), '20000000-0000-0000-0000-000000000005', 'Donna', 'donna', 1, true),
    (gen_random_uuid(), '20000000-0000-0000-0000-000000000005', 'Uomo', 'uomo', 2, true),
    (gen_random_uuid(), '20000000-0000-0000-0000-000000000005', 'Accessori', 'accessori', 3, true),
    (gen_random_uuid(), '20000000-0000-0000-0000-000000000006', 'Cani', 'cani', 1, true),
    (gen_random_uuid(), '20000000-0000-0000-0000-000000000006', 'Gatti', 'gatti', 2, true),
    (gen_random_uuid(), '20000000-0000-0000-0000-000000000006', 'Integratori', 'integratori', 3, true),
    (gen_random_uuid(), '20000000-0000-0000-0000-000000000007', 'Dispensa bio', 'dispensa-bio', 1, true),
    (gen_random_uuid(), '20000000-0000-0000-0000-000000000007', 'Fresco', 'fresco', 2, true),
    (gen_random_uuid(), '20000000-0000-0000-0000-000000000008', 'Schede madri', 'schede-madri', 1, true),
    (gen_random_uuid(), '20000000-0000-0000-0000-000000000008', 'Alimentatori', 'alimentatori', 2, true),
    (gen_random_uuid(), '20000000-0000-0000-0000-000000000008', 'Cavi e adattatori', 'cavi-e-adattatori', 3, true)
ON CONFLICT (project_id, slug) DO UPDATE SET
    name = EXCLUDED.name,
    sort_order = EXCLUDED.sort_order,
    active = EXCLUDED.active;

COMMIT;
