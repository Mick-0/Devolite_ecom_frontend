package com.verso.ai_client_form.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import com.verso.ai_client_form.model.ProjectSummary;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class IntakeRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public IntakeRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UUID upsertCompany(UUID companyId, String legalName, String industry, String vatNumber) {
        UUID id = companyId != null ? companyId : UUID.randomUUID();
        String sql = """
            insert into core.client_company (id, legal_name, industry, vat_number)
            values (:id, :legal_name, :industry, :vat_number)
            on conflict (id) do update set
                legal_name = excluded.legal_name,
                industry = excluded.industry,
                vat_number = excluded.vat_number
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("legal_name", legalName)
            .addValue("industry", industry)
            .addValue("vat_number", vatNumber);
        jdbc.update(sql, params);
        return id;
    }

    public Optional<UUID> findCompanyIdByVat(String vatNumber) {
        if (vatNumber == null || vatNumber.isBlank()) {
            return Optional.empty();
        }
        String sql = "select id from core.client_company where vat_number = :vat";
        return jdbc.query(sql, new MapSqlParameterSource("vat", vatNumber), rs ->
            rs.next() ? Optional.of(UUID.fromString(rs.getString(1))) : Optional.empty()
        );
    }
    public Optional<UUID> findProjectIdByName(String projectName) {
        if (projectName == null || projectName.isBlank()) {
            return Optional.empty();
        }
        String sql = "select id from core.web_project where lower(project_name) = lower(:name)";
        return jdbc.query(sql, new MapSqlParameterSource("name", projectName), rs ->
            rs.next() ? Optional.ofNullable(rs.getObject(1, UUID.class)) : Optional.empty()
        );
    }

    public void upsertCompanyProfile(UUID companyId, Map<String, Object> data) {
        String sql = """
            insert into anagrafica.company_profile
                (company_id, street, city, province, postal_code, country_code, category,
                 founder_years, annual_revenue, referral_source)
            values
                (:company_id, :street, :city, :province, :postal_code, :country_code, :category,
                 :founder_years, :annual_revenue, :referral_source)
            on conflict (company_id) do update set
                street = excluded.street,
                city = excluded.city,
                province = excluded.province,
                postal_code = excluded.postal_code,
                country_code = excluded.country_code,
                category = excluded.category,
                founder_years = excluded.founder_years,
                annual_revenue = excluded.annual_revenue,
                referral_source = excluded.referral_source
            """;
        MapSqlParameterSource params = new MapSqlParameterSource(data).addValue("company_id", companyId);
        jdbc.update(sql, params);
    }

    public void upsertPrimaryContact(UUID companyId, Map<String, Object> data) {
        Optional<UUID> existingId = jdbc.query(
            "select id from anagrafica.company_contact where company_id = :cid and is_primary = true",
            new MapSqlParameterSource("cid", companyId),
            rs -> rs.next() ? Optional.of(UUID.fromString(rs.getString(1))) : Optional.empty()
        );

        if (existingId.isPresent()) {
            String updateSql = """
                update anagrafica.company_contact set
                    full_name = :full_name,
                    phone = :phone,
                    phone_secondary = :phone_secondary,
                    email = :email,
                    email_secondary = :email_secondary,
                    role_title = :role_title,
                    notes = :notes
                where id = :id
                """;
            MapSqlParameterSource params = new MapSqlParameterSource(data)
                .addValue("id", existingId.get());
            jdbc.update(updateSql, params);
        } else {
            String insertSql = """
                insert into anagrafica.company_contact
                    (id, company_id, full_name, phone, phone_secondary, email, email_secondary,
                     role_title, is_primary, notes)
                values
                    (:id, :company_id, :full_name, :phone, :phone_secondary, :email, :email_secondary,
                     :role_title, true, :notes)
                """;
            MapSqlParameterSource params = new MapSqlParameterSource(data)
                .addValue("id", UUID.randomUUID())
                .addValue("company_id", companyId);
            jdbc.update(insertSql, params);
        }
    }

    public UUID upsertProject(UUID projectId, UUID companyId, Map<String, Object> data) {
        UUID id = projectId != null ? projectId : UUID.randomUUID();
        String sql = """
            insert into core.web_project
                (id, company_id, project_name, project_kind, expected_outcome, status)
            values
                (:id, :company_id, :project_name, :project_kind, :expected_outcome, :status)
            on conflict (id) do update set
                project_name = excluded.project_name,
                project_kind = excluded.project_kind,
                expected_outcome = excluded.expected_outcome,
                status = excluded.status
            """;
        MapSqlParameterSource params = new MapSqlParameterSource(data)
            .addValue("id", id)
            .addValue("company_id", companyId);
        jdbc.update(sql, params);
        return id;
    }

    public void initProjectSteps(UUID projectId, String projectKind) {
        String sql = """
            insert into onboarding.project_step (id, project_id, step_code, status)
            select gen_random_uuid(), :project_id, step_code, 'todo'
            from onboarding.step_catalog
            where applicable_to in ('both', :project_kind)
            on conflict (project_id, step_code) do nothing
            """;
        jdbc.update(sql, new MapSqlParameterSource()
            .addValue("project_id", projectId)
            .addValue("project_kind", projectKind)
        );
    }

    public void upsertCrmContact(UUID companyId, Map<String, Object> data, UUID changedByUserId) {
        String existingStage = jdbc.query(
            "select current_stage from crm.crm_contact where company_id = :cid",
            new MapSqlParameterSource("cid", companyId),
            rs -> rs.next() ? rs.getString(1) : null
        );

        String sql = """
            insert into crm.crm_contact
                (id, company_id, first_name, last_name, phone, email, email_secondary,
                 list_name, company_name_snapshot, sector, vat_number, notes,
                 interest_temperature, first_call_date, second_call_date, cta, current_stage)
            values
                (:id, :company_id, :first_name, :last_name, :phone, :email, :email_secondary,
                 :list_name, :company_name_snapshot, :sector, :vat_number, :notes,
                 :interest_temperature, :first_call_date, :second_call_date, :cta, :current_stage)
            on conflict (company_id) do update set
                first_name = excluded.first_name,
                last_name = excluded.last_name,
                phone = excluded.phone,
                email = excluded.email,
                email_secondary = excluded.email_secondary,
                list_name = excluded.list_name,
                company_name_snapshot = excluded.company_name_snapshot,
                sector = excluded.sector,
                vat_number = excluded.vat_number,
                notes = excluded.notes,
                interest_temperature = excluded.interest_temperature,
                first_call_date = excluded.first_call_date,
                second_call_date = excluded.second_call_date,
                cta = excluded.cta,
                current_stage = excluded.current_stage
            """;
        MapSqlParameterSource params = new MapSqlParameterSource(data)
            .addValue("id", UUID.randomUUID())
            .addValue("company_id", companyId);
        jdbc.update(sql, params);

        String newStage = (String) data.get("current_stage");
        if (newStage != null && !newStage.equals(existingStage)) {
            String eventSql = """
                insert into crm.pipeline_event
                    (id, contact_id, from_stage, to_stage, changed_by_user_id, note)
                values
                    (:id, (select id from crm.crm_contact where company_id = :cid), :from_stage, :to_stage, :uid, :note)
                """;
            jdbc.update(eventSql, new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("cid", companyId)
                .addValue("from_stage", existingStage)
                .addValue("to_stage", newStage)
                .addValue("uid", changedByUserId)
                .addValue("note", "Stage change via intake")
            );
        }
    }

    public void upsertLegalProfile(UUID projectId, Map<String, Object> data) {
        String sql = """
            insert into legale.legal_profile
                (project_id, legal_support_mode, vat_number, rea_number, share_capital,
                 pec_email, privacy_page_completed, terms_conditions_completed, footer_cta)
            values
                (:project_id, :legal_support_mode, :vat_number, :rea_number, :share_capital,
                 :pec_email, :privacy_page_completed, :terms_conditions_completed, :footer_cta)
            on conflict (project_id) do update set
                legal_support_mode = excluded.legal_support_mode,
                vat_number = excluded.vat_number,
                rea_number = excluded.rea_number,
                share_capital = excluded.share_capital,
                pec_email = excluded.pec_email,
                privacy_page_completed = excluded.privacy_page_completed,
                terms_conditions_completed = excluded.terms_conditions_completed,
                footer_cta = excluded.footer_cta
            """;
        MapSqlParameterSource params = new MapSqlParameterSource(data)
            .addValue("project_id", projectId);
        jdbc.update(sql, params);
    }

    public void upsertBrandProfile(UUID projectId, Map<String, Object> data) {
        String sql = """
            insert into brand.identity_profile
                (project_id, logo_restyle_required,
                 primary_color, secondary_color, accent_color_1, accent_color_2,
                 font_policy, visual_asset_source, tone_of_voice)
            values
                (:project_id, :logo_restyle_required,
                 :primary_color, :secondary_color, :accent_color_1, :accent_color_2,
                 :font_policy, :visual_asset_source, :tone_of_voice)
            on conflict (project_id) do update set
                logo_restyle_required = excluded.logo_restyle_required,
                primary_color = excluded.primary_color,
                secondary_color = excluded.secondary_color,
                accent_color_1 = excluded.accent_color_1,
                accent_color_2 = excluded.accent_color_2,
                font_policy = excluded.font_policy,
                visual_asset_source = excluded.visual_asset_source,
                tone_of_voice = excluded.tone_of_voice
            """;
        MapSqlParameterSource params = new MapSqlParameterSource(data)
            .addValue("project_id", projectId);
        jdbc.update(sql, params);
    }

    public void upsertMarketingProfile(UUID projectId, Map<String, Object> data) {
        String sql = """
            insert into marketing.profile
                (project_id, has_crm, knows_crm, runs_ads,
                 tracking_ga4, tracking_meta_pixel, tracking_tiktok_pixel, notes)
            values
                (:project_id, :has_crm, :knows_crm, :runs_ads,
                 :tracking_ga4, :tracking_meta_pixel, :tracking_tiktok_pixel, :notes)
            on conflict (project_id) do update set
                has_crm = excluded.has_crm,
                knows_crm = excluded.knows_crm,
                runs_ads = excluded.runs_ads,
                tracking_ga4 = excluded.tracking_ga4,
                tracking_meta_pixel = excluded.tracking_meta_pixel,
                tracking_tiktok_pixel = excluded.tracking_tiktok_pixel,
                notes = excluded.notes
            """;
        MapSqlParameterSource params = new MapSqlParameterSource(data)
            .addValue("project_id", projectId);
        jdbc.update(sql, params);
    }

    public void replaceAdChannels(UUID projectId, List<String> channels) {
        jdbc.update("delete from marketing.ad_channel where project_id = :pid",
            new MapSqlParameterSource("pid", projectId));
        if (channels == null) {
            return;
        }
        for (String ch : channels) {
            if (ch == null || ch.isBlank()) {
                continue;
            }
            jdbc.update(
                "insert into marketing.ad_channel (id, project_id, channel_name, enabled) values (:id, :pid, :name, true)",
                new MapSqlParameterSource()
                    .addValue("id", UUID.randomUUID())
                    .addValue("pid", projectId)
                    .addValue("name", ch)
            );
        }
    }

    public void replaceMarketplaceChannels(UUID projectId, List<String> channels) {
        jdbc.update("delete from marketing.marketplace_channel where project_id = :pid",
            new MapSqlParameterSource("pid", projectId));
        if (channels == null) {
            return;
        }
        for (String ch : channels) {
            if (ch == null || ch.isBlank()) {
                continue;
            }
            jdbc.update(
                "insert into marketing.marketplace_channel (id, project_id, marketplace_name, enabled) values (:id, :pid, :name, true)",
                new MapSqlParameterSource()
                    .addValue("id", UUID.randomUUID())
                    .addValue("pid", projectId)
                    .addValue("name", ch)
            );
        }
    }

    public void upsertSiteBrief(UUID projectId, Map<String, Object> data) {
        String sql = """
            insert into sito.site_brief
                (project_id, inspiration_sites, requested_menu, needs_about_page,
                 needs_where_page, needs_services_page, needs_contact_form,
                 needs_external_links, contact_form_email, copy_mode, page_test_status)
            values
                (:project_id, :inspiration_sites, :requested_menu, :needs_about_page,
                 :needs_where_page, :needs_services_page, :needs_contact_form,
                 :needs_external_links, :contact_form_email, :copy_mode, :page_test_status)
            on conflict (project_id) do update set
                inspiration_sites = excluded.inspiration_sites,
                requested_menu = excluded.requested_menu,
                needs_about_page = excluded.needs_about_page,
                needs_where_page = excluded.needs_where_page,
                needs_services_page = excluded.needs_services_page,
                needs_contact_form = excluded.needs_contact_form,
                needs_external_links = excluded.needs_external_links,
                contact_form_email = excluded.contact_form_email,
                copy_mode = excluded.copy_mode,
                page_test_status = excluded.page_test_status
            """;
        jdbc.update(sql, new MapSqlParameterSource(data).addValue("project_id", projectId));
    }

    public void upsertDomainSetup(UUID projectId, Map<String, Object> data) {
        String sql = """
            insert into dominio.domain_setup
                (project_id, has_existing_domain, existing_domain, domain_to_register,
                 domain_purchase_started_at, domain_purchase_completed_at,
                 preferred_mailbox, mailbox_mode)
            values
                (:project_id, :has_existing_domain, :existing_domain, :domain_to_register,
                 :domain_purchase_started_at, :domain_purchase_completed_at,
                 :preferred_mailbox, :mailbox_mode)
            on conflict (project_id) do update set
                has_existing_domain = excluded.has_existing_domain,
                existing_domain = excluded.existing_domain,
                domain_to_register = excluded.domain_to_register,
                domain_purchase_started_at = excluded.domain_purchase_started_at,
                domain_purchase_completed_at = excluded.domain_purchase_completed_at,
                preferred_mailbox = excluded.preferred_mailbox,
                mailbox_mode = excluded.mailbox_mode
            """;
        jdbc.update(sql, new MapSqlParameterSource(data).addValue("project_id", projectId));
    }

    public void upsertGoogleBusiness(UUID projectId, Map<String, Object> data) {
        String sql = """
            insert into local_business.google_business_setup
                (project_id, has_profile, profile_url, profile_creation_requested)
            values
                (:project_id, :has_profile, :profile_url, :profile_creation_requested)
            on conflict (project_id) do update set
                has_profile = excluded.has_profile,
                profile_url = excluded.profile_url,
                profile_creation_requested = excluded.profile_creation_requested
            """;
        jdbc.update(sql, new MapSqlParameterSource(data).addValue("project_id", projectId));
    }

    public void replaceKeywords(UUID projectId, List<String> keywords) {
        jdbc.update("delete from local_business.keyword where project_id = :pid",
            new MapSqlParameterSource("pid", projectId));
        if (keywords == null) {
            return;
        }
        for (String kw : keywords) {
            if (kw == null || kw.isBlank()) {
                continue;
            }
            jdbc.update(
                "insert into local_business.keyword (id, project_id, keywords, source) values (:id, :pid, :kw, 'seo')",
                new MapSqlParameterSource()
                    .addValue("id", UUID.randomUUID())
                    .addValue("pid", projectId)
                    .addValue("kw", kw)
            );
        }
    }

    public void upsertOrderAdministration(UUID projectId, Map<String, Object> data) {
        String sql = """
            insert into commerciale.order_administration
                (project_id, purchased_service, payment_received, paid_amount,
                 payment_marked_at, invoice_generated, invoice_number, auto_invoice_enabled)
            values
                (:project_id, :purchased_service, :payment_received, :paid_amount,
                 :payment_marked_at, :invoice_generated, :invoice_number, :auto_invoice_enabled)
            on conflict (project_id) do update set
                purchased_service = excluded.purchased_service,
                payment_received = excluded.payment_received,
                paid_amount = excluded.paid_amount,
                payment_marked_at = excluded.payment_marked_at,
                invoice_generated = excluded.invoice_generated,
                invoice_number = excluded.invoice_number,
                auto_invoice_enabled = excluded.auto_invoice_enabled
            """;
        jdbc.update(sql, new MapSqlParameterSource(data).addValue("project_id", projectId));
    }

    public void upsertContract(UUID projectId, Map<String, Object> data) {
        String existing = jdbc.query(
            "select id from commerciale.contract where project_id = :pid order by created_at desc limit 1",
            new MapSqlParameterSource("pid", projectId),
            rs -> rs.next() ? rs.getString(1) : null
        );

        if (existing == null) {
            String sql = """
                insert into commerciale.contract
                    (id, project_id, contact_name, contact_email, status, sent_at, signed_at)
                values
                    (:id, :project_id, :contact_name, :contact_email, :status, :sent_at, :signed_at)
                """;
            MapSqlParameterSource params = new MapSqlParameterSource(data)
                .addValue("id", UUID.randomUUID())
                .addValue("project_id", projectId);
            jdbc.update(sql, params);
        } else {
            String sql = """
                update commerciale.contract set
                    contact_name = :contact_name,
                    contact_email = :contact_email,
                    status = :status,
                    sent_at = :sent_at,
                    signed_at = :signed_at
                where id = :id
                """;
            MapSqlParameterSource params = new MapSqlParameterSource(data)
                .addValue("id", UUID.fromString(existing));
            jdbc.update(sql, params);
        }
    }

    public void upsertAiSettings(UUID projectId, Map<String, Object> data) {
        String sql = """
            insert into ai_ops.project_ai_settings
                (project_id, initial_analysis_started_at, content_research_enabled,
                 seo_indexing_enabled, seo_credits_total, seo_credits_used)
            values
                (:project_id, :initial_analysis_started_at, :content_research_enabled,
                 :seo_indexing_enabled, :seo_credits_total, :seo_credits_used)
            on conflict (project_id) do update set
                initial_analysis_started_at = excluded.initial_analysis_started_at,
                content_research_enabled = excluded.content_research_enabled,
                seo_indexing_enabled = excluded.seo_indexing_enabled,
                seo_credits_total = excluded.seo_credits_total,
                seo_credits_used = excluded.seo_credits_used
            """;
        jdbc.update(sql, new MapSqlParameterSource(data).addValue("project_id", projectId));
    }

    public void upsertStoreSetup(UUID projectId, Map<String, Object> data) {
        String sql = """
            insert into ecommerce.store_setup
                (project_id, purchase_enabled, auto_renewal_enabled, rid_enabled,
                 csv_import_enabled, csv_import_instructions_sent_at)
            values
                (:project_id, :purchase_enabled, :auto_renewal_enabled, :rid_enabled,
                 :csv_import_enabled, :csv_import_instructions_sent_at)
            on conflict (project_id) do update set
                purchase_enabled = excluded.purchase_enabled,
                auto_renewal_enabled = excluded.auto_renewal_enabled,
                rid_enabled = excluded.rid_enabled,
                csv_import_enabled = excluded.csv_import_enabled,
                csv_import_instructions_sent_at = excluded.csv_import_instructions_sent_at
            """;
        jdbc.update(sql, new MapSqlParameterSource(data).addValue("project_id", projectId));
    }

    public void replacePaymentMethods(UUID projectId, List<String> methods) {
        jdbc.update("delete from ecommerce.accepted_payment_method where project_id = :pid",
            new MapSqlParameterSource("pid", projectId));
        if (methods == null) {
            return;
        }
        for (String m : methods) {
            if (m == null || m.isBlank()) {
                continue;
            }
            jdbc.update(
                "insert into ecommerce.accepted_payment_method (id, project_id, method_code, display_name, enabled) values (:id, :pid, :code, :name, true)",
                new MapSqlParameterSource()
                    .addValue("id", UUID.randomUUID())
                    .addValue("pid", projectId)
                    .addValue("code", m)
                    .addValue("name", m)
            );
        }
    }

    public void replaceCarriers(UUID projectId, List<String> carriers) {
        jdbc.update("delete from ecommerce.accepted_carrier where project_id = :pid",
            new MapSqlParameterSource("pid", projectId));
        if (carriers == null) {
            return;
        }
        for (String c : carriers) {
            if (c == null || c.isBlank()) {
                continue;
            }
            jdbc.update(
                "insert into ecommerce.accepted_carrier (id, project_id, carrier_code, display_name, enabled) values (:id, :pid, :code, :name, true)",
                new MapSqlParameterSource()
                    .addValue("id", UUID.randomUUID())
                    .addValue("pid", projectId)
                    .addValue("code", c)
                    .addValue("name", c)
            );
        }
    }

    public void replaceCategories(UUID projectId, List<String> categories) {
        jdbc.update("delete from ecommerce.product_category where project_id = :pid",
            new MapSqlParameterSource("pid", projectId));
        if (categories == null) {
            return;
        }
        for (String c : categories) {
            if (c == null || c.isBlank()) {
                continue;
            }
            String slug = slugify(c);
            jdbc.update(
                "insert into ecommerce.product_category (id, project_id, name, slug, sort_order, active) values (:id, :pid, :name, :slug, 0, true)",
                new MapSqlParameterSource()
                    .addValue("id", UUID.randomUUID())
                    .addValue("pid", projectId)
                    .addValue("name", c)
                    .addValue("slug", slug)
            );
        }
    }

    public UUID insertAsset(UUID projectId, String category, String originalName, String storagePath,
                            String mimeType, Long sizeBytes, boolean generatedByAi, String comment, UUID uploadedBy) {
        UUID id = UUID.randomUUID();
        String sql = """
            insert into media.asset
                (id, project_id, asset_category, original_name, storage_path, mime_type, size_bytes,
                 generated_by_ai, comment, uploaded_by_user_id)
            values
                (:id, :project_id, :asset_category, :original_name, :storage_path, :mime_type, :size_bytes,
                 :generated_by_ai, :comment, :uploaded_by_user_id)
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("project_id", projectId)
            .addValue("asset_category", category)
            .addValue("original_name", originalName)
            .addValue("storage_path", storagePath)
            .addValue("mime_type", mimeType)
            .addValue("size_bytes", sizeBytes)
            .addValue("generated_by_ai", generatedByAi)
            .addValue("comment", comment)
            .addValue("uploaded_by_user_id", uploadedBy);
        jdbc.update(sql, params);
        return id;
    }

    public List<ProjectSummary> listRecentProjects(int limit) {
        int capped = Math.max(1, Math.min(limit, 200));
        String sql = """
            select p.id as project_id,
                   p.project_name,
                   p.project_kind,
                   p.updated_at,
                   c.legal_name as company_name
            from core.web_project p
            join core.client_company c on c.id = p.company_id
            order by p.updated_at desc, p.created_at desc
            limit :limit
            """;
        return jdbc.query(sql, new MapSqlParameterSource("limit", capped), (rs, i) ->
            new ProjectSummary(
                rs.getObject("project_id", UUID.class),
                rs.getString("project_name"),
                rs.getString("company_name"),
                rs.getString("project_kind"),
                rs.getObject("updated_at", OffsetDateTime.class)
            )
        );
    }
    public Optional<Map<String, Object>> findProject(UUID projectId) {
        return queryForMapOptional("select * from core.web_project where id = :id", Map.of("id", projectId));
    }

    public Optional<Map<String, Object>> findCompany(UUID companyId) {
        return queryForMapOptional("select * from core.client_company where id = :id", Map.of("id", companyId));
    }

    public Optional<Map<String, Object>> findCompanyProfile(UUID companyId) {
        return queryForMapOptional("select * from anagrafica.company_profile where company_id = :id", Map.of("id", companyId));
    }

    public Optional<Map<String, Object>> findPrimaryContact(UUID companyId) {
        return queryForMapOptional("select * from anagrafica.company_contact where company_id = :id and is_primary = true", Map.of("id", companyId));
    }

    public Optional<Map<String, Object>> findCrmContact(UUID companyId) {
        return queryForMapOptional("select * from crm.crm_contact where company_id = :id", Map.of("id", companyId));
    }

    public Optional<Map<String, Object>> findLegalProfile(UUID projectId) {
        return queryForMapOptional("select * from legale.legal_profile where project_id = :id", Map.of("id", projectId));
    }

    public Optional<Map<String, Object>> findBrandProfile(UUID projectId) {
        return queryForMapOptional("select * from brand.identity_profile where project_id = :id", Map.of("id", projectId));
    }

    public Optional<Map<String, Object>> findMarketingProfile(UUID projectId) {
        return queryForMapOptional("select * from marketing.profile where project_id = :id", Map.of("id", projectId));
    }

    public List<String> findAdChannels(UUID projectId) {
        return jdbc.query("select channel_name from marketing.ad_channel where project_id = :id",
            new MapSqlParameterSource("id", projectId), (rs, i) -> rs.getString(1));
    }

    public List<String> findMarketplaceChannels(UUID projectId) {
        return jdbc.query("select marketplace_name from marketing.marketplace_channel where project_id = :id",
            new MapSqlParameterSource("id", projectId), (rs, i) -> rs.getString(1));
    }

    public Optional<Map<String, Object>> findSiteBrief(UUID projectId) {
        return queryForMapOptional("select * from sito.site_brief where project_id = :id", Map.of("id", projectId));
    }

    public Optional<Map<String, Object>> findDomainSetup(UUID projectId) {
        return queryForMapOptional("select * from dominio.domain_setup where project_id = :id", Map.of("id", projectId));
    }

    public Optional<Map<String, Object>> findGoogleBusiness(UUID projectId) {
        return queryForMapOptional("select * from local_business.google_business_setup where project_id = :id", Map.of("id", projectId));
    }

    public List<String> findKeywords(UUID projectId) {
        return jdbc.query("select keywords from local_business.keyword where project_id = :id",
            new MapSqlParameterSource("id", projectId), (rs, i) -> rs.getString(1));
    }

    public Optional<Map<String, Object>> findOrderAdministration(UUID projectId) {
        return queryForMapOptional("select * from commerciale.order_administration where project_id = :id", Map.of("id", projectId));
    }

    public Optional<Map<String, Object>> findLatestContract(UUID projectId) {
        return queryForMapOptional(
            "select * from commerciale.contract where project_id = :id order by created_at desc limit 1",
            Map.of("id", projectId)
        );
    }

    public Optional<Map<String, Object>> findAiSettings(UUID projectId) {
        return queryForMapOptional("select * from ai_ops.project_ai_settings where project_id = :id", Map.of("id", projectId));
    }

    public Optional<Map<String, Object>> findStoreSetup(UUID projectId) {
        return queryForMapOptional("select * from ecommerce.store_setup where project_id = :id", Map.of("id", projectId));
    }

    public List<String> findPaymentMethods(UUID projectId) {
        return jdbc.query("select method_code from ecommerce.accepted_payment_method where project_id = :id",
            new MapSqlParameterSource("id", projectId), (rs, i) -> rs.getString(1));
    }

    public List<String> findCarriers(UUID projectId) {
        return jdbc.query("select carrier_code from ecommerce.accepted_carrier where project_id = :id",
            new MapSqlParameterSource("id", projectId), (rs, i) -> rs.getString(1));
    }

    public List<String> findCategories(UUID projectId) {
        return jdbc.query("select name from ecommerce.product_category where project_id = :id order by sort_order, name",
            new MapSqlParameterSource("id", projectId), (rs, i) -> rs.getString(1));
    }

    private Optional<Map<String, Object>> queryForMapOptional(String sql, Map<String, Object> params) {
        List<Map<String, Object>> rows = jdbc.query(sql, params, (rs, i) -> mapRow(rs));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private Map<String, Object> mapRow(ResultSet rs) throws SQLException {
        return new ColumnMapRowMapper().mapRow(rs, 0);
    }

    private String slugify(String input) {
        String slug = input.toLowerCase().replaceAll("[^a-z0-9]+", "-");
        slug = slug.replaceAll("(^-+|-+$)", "");
        return slug.isBlank() ? "category" : slug;
    }

    public UUID createDraft(UUID projectId) {
        UUID id = UUID.randomUUID();
        String sql = "insert into intake.draft (id, project_id) values (:id, :pid)";
        jdbc.update(sql, new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("pid", projectId)
        );
        return id;
    }

    public Optional<UUID> findDraftIdByProject(UUID projectId) {
        if (projectId == null) {
            return Optional.empty();
        }
        String sql = "select id from intake.draft where project_id = :pid order by created_at desc limit 1";
        return jdbc.query(sql, new MapSqlParameterSource("pid", projectId), rs ->
            rs.next() ? Optional.ofNullable(rs.getObject(1, UUID.class)) : Optional.empty()
        );
    }

    public void linkDraftToProject(UUID draftId, UUID projectId) {
        if (draftId == null || projectId == null) {
            return;
        }
        String sql = "update intake.draft set project_id = :pid where id = :id";
        jdbc.update(sql, new MapSqlParameterSource()
            .addValue("pid", projectId)
            .addValue("id", draftId)
        );
    }

    public List<String> findConfirmedSections(UUID draftId) {
        if (draftId == null) {
            return List.of();
        }
        String sql = "select section_key from intake.section_status where draft_id = :id and status = 'confirmed' order by confirmed_at nulls last";
        return jdbc.query(sql, new MapSqlParameterSource("id", draftId), (rs, i) -> rs.getString(1));
    }

    public void confirmSection(UUID draftId, String sectionKey, UUID userId) {
        String sql = """
            insert into intake.section_status
                (draft_id, section_key, status, confirmed_by_user_id, confirmed_at)
            values
                (:draft_id, :section_key, 'confirmed', :user_id, now())
            on conflict (draft_id, section_key) do update set
                status = 'confirmed',
                confirmed_by_user_id = excluded.confirmed_by_user_id,
                confirmed_at = excluded.confirmed_at
            """;
        jdbc.update(sql, new MapSqlParameterSource()
            .addValue("draft_id", draftId)
            .addValue("section_key", sectionKey)
            .addValue("user_id", userId)
        );
    }

    public void markSectionPending(UUID draftId, String sectionKey, UUID userId) {
        String sql = """
            insert into intake.section_status
                (draft_id, section_key, status, confirmed_by_user_id, confirmed_at)
            values
                (:draft_id, :section_key, 'pending', :user_id, null)
            on conflict (draft_id, section_key) do update set
                status = 'pending',
                confirmed_by_user_id = excluded.confirmed_by_user_id,
                confirmed_at = null
            """;
        jdbc.update(sql, new MapSqlParameterSource()
            .addValue("draft_id", draftId)
            .addValue("section_key", sectionKey)
            .addValue("user_id", userId)
        );
    }

    public void markSectionsPending(UUID draftId, List<String> sectionKeys, UUID userId) {
        if (sectionKeys == null || sectionKeys.isEmpty()) {
            return;
        }
        for (String key : sectionKeys) {
            if (key == null || key.isBlank()) {
                continue;
            }
            markSectionPending(draftId, key, userId);
        }
    }
}





