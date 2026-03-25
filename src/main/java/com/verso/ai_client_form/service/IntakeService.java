package com.verso.ai_client_form.service;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.verso.ai_client_form.repository.IntakeRepository;
import com.verso.ai_client_form.repository.StaffUserRepository;
import com.verso.ai_client_form.model.IntakeForm;
import com.verso.ai_client_form.model.ProjectSummary;

@Service
public class IntakeService {

    private final IntakeRepository repo;
    private final StaffUserRepository staffRepo;
    private final StorageService storageService;

    @Value("${app.intake.enforce-section-order:true}")
    private boolean enforceSectionOrder;

    private final List<String> sectionOrder = List.of(
        "company", "contact", "project", "crm", "legal", "brand", "marketing",
        "site", "domain", "local", "commercial", "ai", "ecommerce", "files"
    );

    public IntakeService(IntakeRepository repo, StaffUserRepository staffRepo, StorageService storageService) {
        this.repo = repo;
        this.staffRepo = staffRepo;
        this.storageService = storageService;
    }

    public boolean isEnforceSectionOrder() {
        return enforceSectionOrder;
    }

    @Transactional(readOnly = true)
    public UUID findProjectIdByName(String projectName) {
        return repo.findProjectIdByName(projectName).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<ProjectSummary> listRecentProjects(int limit) {
        return repo.listRecentProjects(limit);
    }

    @Transactional
    public UUID save(IntakeForm form, String username) {
        UUID staffUserId = ensureStaffUser(username);

        UUID companyId = form.getCompanyId();
        if (companyId == null) {
            companyId = repo.findCompanyIdByVat(form.getVatNumber()).orElse(null);
        }
        companyId = repo.upsertCompany(companyId, form.getLegalName(), form.getIndustry(), form.getVatNumber());

        repo.upsertCompanyProfile(companyId, map(
            "street", form.getStreet(),
            "city", form.getCity(),
            "province", form.getProvince(),
            "postal_code", form.getPostalCode(),
            "country_code", emptyToNull(form.getCountryCode()),
            "category", form.getCategory(),
            "founder_years", form.getFounderYears(),
            "annual_revenue", form.getAnnualRevenue(),
            "referral_source", form.getReferralSource()
        ));

        repo.upsertPrimaryContact(companyId, map(
            "full_name", form.getContactFullName(),
            "phone", form.getContactPhone(),
            "phone_secondary", form.getContactPhoneSecondary(),
            "email", form.getContactEmail(),
            "email_secondary", form.getContactEmailSecondary(),
            "role_title", form.getContactRoleTitle(),
            "notes", form.getContactNotes()
        ));

        UUID projectId = form.getProjectId();
        if (projectId == null && notBlank(form.getProjectName())) {
            projectId = repo.findProjectIdByName(form.getProjectName()).orElse(null);
        }
        projectId = repo.upsertProject(projectId, companyId, map(
            "project_name", form.getProjectName(),
            "project_kind", defaultIfBlank(form.getProjectKind(), "vetrina"),
            "expected_outcome", form.getExpectedOutcome(),
            "source_channel", form.getSourceChannel(),
            "status", defaultIfBlank(form.getStatus(), "onboarding")
        ));

        repo.initProjectSteps(projectId, defaultIfBlank(form.getProjectKind(), "vetrina"));

        repo.upsertCrmContact(companyId, map(
            "first_name", form.getCrmFirstName(),
            "last_name", form.getCrmLastName(),
            "phone", form.getCrmPhone(),
            "email", form.getCrmEmail(),
            "email_secondary", form.getCrmEmailSecondary(),
            "list_name", form.getCrmListName(),
            "company_name_snapshot", form.getCrmCompanyNameSnapshot(),
            "sector", form.getCrmSector(),
            "vat_number", form.getCrmVatNumber(),
            "notes", form.getCrmNotes(),
            "interest_temperature", form.getCrmInterestTemperature(),
            "first_call_date", form.getCrmFirstCallDate(),
            "second_call_date", form.getCrmSecondCallDate(),
            "cta", form.getCrmCta(),
            "current_stage", defaultIfBlank(form.getCrmCurrentStage(), "lead")
        ), staffUserId);

        repo.upsertLegalProfile(projectId, map(
            "legal_support_mode", form.getLegalSupportMode(),
            "vat_number", form.getLegalVatNumber(),
            "rea_number", form.getLegalReaNumber(),
            "share_capital", form.getLegalShareCapital(),
            "pec_email", form.getLegalPecEmail(),
            "privacy_page_completed", bool(form.getLegalPrivacyCompleted()),
            "terms_conditions_completed", bool(form.getLegalTermsCompleted()),
            "footer_cta", form.getLegalFooterCta()
        ));

        repo.upsertBrandProfile(projectId, map(
            "logo_restyle_required", bool(form.getLogoRestyleRequired()),
            "logo_restyle_generated", bool(form.getLogoRestyleGenerated()),
            "logo_approved", bool(form.getLogoApproved()),
            "primary_color", form.getPrimaryColor(),
            "secondary_color", form.getSecondaryColor(),
            "accent_color_1", form.getAccentColor1(),
            "accent_color_2", form.getAccentColor2(),
            "font_policy", form.getFontPolicy(),
            "visual_asset_source", form.getVisualAssetSource(),
            "tone_of_voice", form.getToneOfVoice()
        ));

        repo.upsertMarketingProfile(projectId, map(
            "has_crm", bool(form.getMarketingHasCrm()),
            "knows_crm", bool(form.getMarketingKnowsCrm()),
            "runs_ads", bool(form.getMarketingRunsAds()),
            "tracking_ga4", bool(form.getTrackingGa4()),
            "tracking_meta_pixel", bool(form.getTrackingMetaPixel()),
            "tracking_tiktok_pixel", bool(form.getTrackingTiktokPixel()),
            "notes", form.getMarketingNotes()
        ));

        repo.replaceAdChannels(projectId, nullToEmpty(form.getAdChannels()));
        repo.replaceMarketplaceChannels(projectId, nullToEmpty(form.getMarketplaceChannels()));

        repo.upsertSiteBrief(projectId, map(
            "inspiration_sites", form.getInspirationSites(),
            "requested_menu", form.getRequestedMenu(),
            "needs_about_page", bool(form.getNeedsAboutPage()),
            "needs_where_page", bool(form.getNeedsWherePage()),
            "needs_services_page", bool(form.getNeedsServicesPage()),
            "needs_contact_form", bool(form.getNeedsContactForm()),
            "needs_external_links", bool(form.getNeedsExternalLinks()),
            "contact_form_email", form.getContactFormEmail(),
            "copy_mode", form.getCopyMode(),
            "page_test_status", form.getPageTestStatus()
        ));

        repo.upsertDomainSetup(projectId, map(
            "has_existing_domain", bool(form.getHasExistingDomain()),
            "existing_domain", form.getExistingDomain(),
            "domain_to_register", form.getDomainToRegister(),
            "domain_purchase_started_at", form.getDomainPurchaseStartedAt(),
            "domain_purchase_completed_at", form.getDomainPurchaseCompletedAt(),
            "preferred_mailbox", form.getPreferredMailbox(),
            "mailbox_mode", form.getMailboxMode()
        ));

        repo.upsertGoogleBusiness(projectId, map(
            "has_profile", bool(form.getGbHasProfile()),
            "profile_url", form.getGbProfileUrl(),
            "profile_creation_requested", bool(form.getGbProfileCreationRequested())
        ));

        repo.replaceKeywords(projectId, parseList(form.getGbKeywords()));

        repo.upsertOrderAdministration(projectId, map(
            "purchased_service", form.getPurchasedService(),
            "payment_received", bool(form.getPaymentReceived()),
            "paid_amount", form.getPaidAmount(),
            "payment_marked_at", form.getPaymentMarkedAt(),
            "invoice_generated", bool(form.getInvoiceGenerated()),
            "invoice_number", form.getInvoiceNumber(),
            "auto_invoice_enabled", bool(form.getAutoInvoiceEnabled())
        ));

        if (notBlank(form.getContractContactName()) || notBlank(form.getContractContactEmail())) {
            repo.upsertContract(projectId, map(
                "contact_name", form.getContractContactName(),
                "contact_email", form.getContractContactEmail(),
                "status", defaultIfBlank(form.getContractStatus(), "bozza"),
                "sent_at", form.getContractSentAt(),
                "signed_at", form.getContractSignedAt()
            ));
        }

        repo.upsertAiSettings(projectId, map(
            "initial_analysis_started_at", form.getAiInitialAnalysisStartedAt(),
            "content_research_enabled", bool(form.getAiContentResearchEnabled()),
            "seo_indexing_enabled", bool(form.getAiSeoIndexingEnabled()),
            "seo_credits_total", form.getAiSeoCreditsTotal(),
            "seo_credits_used", form.getAiSeoCreditsUsed()
        ));

        if ("ecommerce".equalsIgnoreCase(form.getProjectKind())) {
            repo.upsertStoreSetup(projectId, map(
                "purchase_enabled", bool(form.getPurchaseEnabled()),
                "auto_renewal_enabled", bool(form.getAutoRenewalEnabled()),
                "rid_enabled", bool(form.getRidEnabled()),
                "csv_import_enabled", bool(form.getCsvImportEnabled()),
                "csv_import_instructions_sent_at", form.getCsvImportInstructionsSentAt()
            ));
            repo.replacePaymentMethods(projectId, nullToEmpty(form.getPaymentMethods()));
            repo.replaceCarriers(projectId, nullToEmpty(form.getCarriers()));
            repo.replaceCategories(projectId, parseList(form.getProductCategories()));
        }

        handleFile(form.getLogoFile(), projectId, "logo", form.getLogoComment(), staffUserId);
        handleFile(form.getVisuraFile(), projectId, "documento_legale", form.getVisuraComment(), staffUserId);
        handleFile(form.getContractFile(), projectId, "contratto_pdf", form.getContractComment(), staffUserId);

        if (form.getDraftId() != null) {
            repo.linkDraftToProject(form.getDraftId(), projectId);
        }

        return projectId;
    }

    public UUID resolveDraftId(UUID draftId, UUID projectId) {
        if (draftId != null) {
            return draftId;
        }
        if (projectId != null) {
            return repo.findDraftIdByProject(projectId).orElse(null);
        }
        return null;
    }

    @Transactional
    public UUID getOrCreateDraft(UUID draftId, UUID projectId) {
        if (draftId != null) {
            return draftId;
        }
        if (projectId != null) {
            return repo.findDraftIdByProject(projectId)
                .orElseGet(() -> repo.createDraft(projectId));
        }
        return repo.createDraft(null);
    }

    @Transactional(readOnly = true)
    public List<String> loadConfirmedSections(UUID draftId) {
        if (draftId == null) {
            return List.of();
        }
        return repo.findConfirmedSections(draftId);
    }

    @Transactional
    public void confirmSection(UUID draftId, String sectionKey, String username) {
        if (!sectionOrder.contains(sectionKey)) {
            throw new IllegalArgumentException("Unknown section");
        }
        List<String> confirmed = repo.findConfirmedSections(draftId);
        int index = sectionOrder.indexOf(sectionKey);
        if (enforceSectionOrder && index > 0) {
            String prev = sectionOrder.get(index - 1);
            if (!confirmed.contains(prev)) {
                throw new IllegalArgumentException("Previous section not confirmed");
            }
        }
        UUID staffUserId = ensureStaffUser(username);
        repo.confirmSection(draftId, sectionKey, staffUserId);
    }

    @Transactional
    public void unconfirmSection(UUID draftId, String sectionKey, String username) {
        if (draftId == null) {
            throw new IllegalArgumentException("draftId required");
        }
        if (!sectionOrder.contains(sectionKey)) {
            throw new IllegalArgumentException("Unknown section");
        }
        UUID staffUserId = ensureStaffUser(username);
        int index = sectionOrder.indexOf(sectionKey);
        List<String> toReset = sectionOrder.subList(index, sectionOrder.size());
        repo.markSectionsPending(draftId, toReset, staffUserId);
    }

    @Transactional(readOnly = true)
    public IntakeForm load(UUID projectId) {
        IntakeForm form = new IntakeForm();
        repo.findProject(projectId).ifPresent(row -> {
            form.setProjectId(projectId);
            form.setCompanyId((UUID) row.get("company_id"));
            form.setProjectName((String) row.get("project_name"));
            form.setProjectKind((String) row.get("project_kind"));
            form.setExpectedOutcome((String) row.get("expected_outcome"));
            form.setSourceChannel((String) row.get("source_channel"));
            form.setStatus((String) row.get("status"));
        });
        if (form.getCompanyId() != null) {
            repo.findCompany(form.getCompanyId()).ifPresent(row -> {
                form.setLegalName((String) row.get("legal_name"));
                form.setIndustry((String) row.get("industry"));
                form.setVatNumber((String) row.get("vat_number"));
            });
            repo.findCompanyProfile(form.getCompanyId()).ifPresent(row -> {
                form.setStreet((String) row.get("street"));
                form.setCity((String) row.get("city"));
                form.setProvince((String) row.get("province"));
                form.setPostalCode((String) row.get("postal_code"));
                form.setCountryCode((String) row.get("country_code"));
                form.setCategory((String) row.get("category"));
                form.setFounderYears(toInteger(row.get("founder_years")));
                form.setAnnualRevenue((java.math.BigDecimal) row.get("annual_revenue"));
                form.setReferralSource((String) row.get("referral_source"));
            });
            repo.findPrimaryContact(form.getCompanyId()).ifPresent(row -> {
                form.setContactFullName((String) row.get("full_name"));
                form.setContactPhone((String) row.get("phone"));
                form.setContactPhoneSecondary((String) row.get("phone_secondary"));
                form.setContactEmail((String) row.get("email"));
                form.setContactEmailSecondary((String) row.get("email_secondary"));
                form.setContactRoleTitle((String) row.get("role_title"));
                form.setContactNotes((String) row.get("notes"));
            });
            repo.findCrmContact(form.getCompanyId()).ifPresent(row -> {
                form.setCrmFirstName((String) row.get("first_name"));
                form.setCrmLastName((String) row.get("last_name"));
                form.setCrmPhone((String) row.get("phone"));
                form.setCrmEmail((String) row.get("email"));
                form.setCrmEmailSecondary((String) row.get("email_secondary"));
                form.setCrmListName((String) row.get("list_name"));
                form.setCrmCompanyNameSnapshot((String) row.get("company_name_snapshot"));
                form.setCrmSector((String) row.get("sector"));
                form.setCrmVatNumber((String) row.get("vat_number"));
                form.setCrmNotes((String) row.get("notes"));
                form.setCrmInterestTemperature((String) row.get("interest_temperature"));
                form.setCrmFirstCallDate(toLocalDate(row.get("first_call_date")));
                form.setCrmSecondCallDate(toLocalDate(row.get("second_call_date")));
                form.setCrmCta((String) row.get("cta"));
                form.setCrmCurrentStage((String) row.get("current_stage"));
            });
        }

        repo.findLegalProfile(projectId).ifPresent(row -> {
            form.setLegalSupportMode((String) row.get("legal_support_mode"));
            form.setLegalVatNumber((String) row.get("vat_number"));
            form.setLegalReaNumber((String) row.get("rea_number"));
            form.setLegalShareCapital((java.math.BigDecimal) row.get("share_capital"));
            form.setLegalPecEmail((String) row.get("pec_email"));
            form.setLegalPrivacyCompleted(toBoolean(row.get("privacy_page_completed")));
            form.setLegalTermsCompleted(toBoolean(row.get("terms_conditions_completed")));
            form.setLegalFooterCta((String) row.get("footer_cta"));
        });

        repo.findBrandProfile(projectId).ifPresent(row -> {
            form.setLogoRestyleRequired(toBoolean(row.get("logo_restyle_required")));
            form.setLogoRestyleGenerated(toBoolean(row.get("logo_restyle_generated")));
            form.setLogoApproved(toBoolean(row.get("logo_approved")));
            form.setPrimaryColor((String) row.get("primary_color"));
            form.setSecondaryColor((String) row.get("secondary_color"));
            form.setAccentColor1((String) row.get("accent_color_1"));
            form.setAccentColor2((String) row.get("accent_color_2"));
            form.setFontPolicy((String) row.get("font_policy"));
            form.setVisualAssetSource((String) row.get("visual_asset_source"));
            form.setToneOfVoice((String) row.get("tone_of_voice"));
        });

        repo.findMarketingProfile(projectId).ifPresent(row -> {
            form.setMarketingHasCrm(toBoolean(row.get("has_crm")));
            form.setMarketingKnowsCrm(toBoolean(row.get("knows_crm")));
            form.setMarketingRunsAds(toBoolean(row.get("runs_ads")));
            form.setTrackingGa4(toBoolean(row.get("tracking_ga4")));
            form.setTrackingMetaPixel(toBoolean(row.get("tracking_meta_pixel")));
            form.setTrackingTiktokPixel(toBoolean(row.get("tracking_tiktok_pixel")));
            form.setMarketingNotes((String) row.get("notes"));
        });
        form.setAdChannels(repo.findAdChannels(projectId));
        form.setMarketplaceChannels(repo.findMarketplaceChannels(projectId));

        repo.findSiteBrief(projectId).ifPresent(row -> {
            form.setInspirationSites((String) row.get("inspiration_sites"));
            form.setRequestedMenu((String) row.get("requested_menu"));
            form.setNeedsAboutPage(toBoolean(row.get("needs_about_page")));
            form.setNeedsWherePage(toBoolean(row.get("needs_where_page")));
            form.setNeedsServicesPage(toBoolean(row.get("needs_services_page")));
            form.setNeedsContactForm(toBoolean(row.get("needs_contact_form")));
            form.setNeedsExternalLinks(toBoolean(row.get("needs_external_links")));
            form.setContactFormEmail((String) row.get("contact_form_email"));
            form.setCopyMode((String) row.get("copy_mode"));
            form.setPageTestStatus((String) row.get("page_test_status"));
        });

        repo.findDomainSetup(projectId).ifPresent(row -> {
            form.setHasExistingDomain(toBoolean(row.get("has_existing_domain")));
            form.setExistingDomain((String) row.get("existing_domain"));
            form.setDomainToRegister((String) row.get("domain_to_register"));
            form.setDomainPurchaseStartedAt(toLocalDateTime(row.get("domain_purchase_started_at")));
            form.setDomainPurchaseCompletedAt(toLocalDateTime(row.get("domain_purchase_completed_at")));
            form.setPreferredMailbox((String) row.get("preferred_mailbox"));
            form.setMailboxMode((String) row.get("mailbox_mode"));
        });

        repo.findGoogleBusiness(projectId).ifPresent(row -> {
            form.setGbHasProfile(toBoolean(row.get("has_profile")));
            form.setGbProfileUrl((String) row.get("profile_url"));
            form.setGbProfileCreationRequested(toBoolean(row.get("profile_creation_requested")));
        });
        form.setGbKeywords(String.join(", ", repo.findKeywords(projectId)));

        repo.findOrderAdministration(projectId).ifPresent(row -> {
            form.setPurchasedService((String) row.get("purchased_service"));
            form.setPaymentReceived(toBoolean(row.get("payment_received")));
            form.setPaidAmount((java.math.BigDecimal) row.get("paid_amount"));
            form.setPaymentMarkedAt(toLocalDateTime(row.get("payment_marked_at")));
            form.setInvoiceGenerated(toBoolean(row.get("invoice_generated")));
            form.setInvoiceNumber((String) row.get("invoice_number"));
            form.setAutoInvoiceEnabled(toBoolean(row.get("auto_invoice_enabled")));
        });

        repo.findLatestContract(projectId).ifPresent(row -> {
            form.setContractContactName((String) row.get("contact_name"));
            form.setContractContactEmail((String) row.get("contact_email"));
            form.setContractStatus((String) row.get("status"));
            form.setContractSentAt(toLocalDateTime(row.get("sent_at")));
            form.setContractSignedAt(toLocalDateTime(row.get("signed_at")));
        });

        repo.findAiSettings(projectId).ifPresent(row -> {
            form.setAiInitialAnalysisStartedAt(toLocalDateTime(row.get("initial_analysis_started_at")));
            form.setAiContentResearchEnabled(toBoolean(row.get("content_research_enabled")));
            form.setAiSeoIndexingEnabled(toBoolean(row.get("seo_indexing_enabled")));
            form.setAiSeoCreditsTotal(toInteger(row.get("seo_credits_total")));
            form.setAiSeoCreditsUsed(toInteger(row.get("seo_credits_used")));
        });

        repo.findStoreSetup(projectId).ifPresent(row -> {
            form.setPurchaseEnabled(toBoolean(row.get("purchase_enabled")));
            form.setAutoRenewalEnabled(toBoolean(row.get("auto_renewal_enabled")));
            form.setRidEnabled(toBoolean(row.get("rid_enabled")));
            form.setCsvImportEnabled(toBoolean(row.get("csv_import_enabled")));
            form.setCsvImportInstructionsSentAt(toLocalDateTime(row.get("csv_import_instructions_sent_at")));
        });
        form.setPaymentMethods(repo.findPaymentMethods(projectId));
        form.setCarriers(repo.findCarriers(projectId));
        form.setProductCategories(String.join(", ", repo.findCategories(projectId)));

        return form;
    }

    private void handleFile(MultipartFile file, UUID projectId, String category, String comment, UUID staffUserId) {
        if (file == null || file.isEmpty()) {
            return;
        }
        StorageService.StoredFile stored = storageService.store(projectId, file);
        repo.insertAsset(
            projectId,
            category,
            stored.originalName(),
            stored.relativePath(),
            stored.mimeType(),
            stored.sizeBytes(),
            false,
            comment,
            staffUserId
        );
    }

    private List<String> parseList(String input) {
        if (input == null || input.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(input.split("[,\n]"))
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .collect(Collectors.toList());
    }

    private List<String> nullToEmpty(List<String> list) {
        return list == null ? Collections.emptyList() : list;
    }

    private boolean bool(Boolean value) {
        return Boolean.TRUE.equals(value);
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private String defaultIfBlank(String s, String fallback) {
        return (s == null || s.isBlank()) ? fallback : s;
    }

    private String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private java.time.LocalDate toLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.time.LocalDate d) {
            return d;
        }
        if (value instanceof java.sql.Date d) {
            return d.toLocalDate();
        }
        if (value instanceof java.sql.Timestamp t) {
            return t.toLocalDateTime().toLocalDate();
        }
        return null;
    }

    private java.time.LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.time.LocalDateTime dt) {
            return dt;
        }
        if (value instanceof java.sql.Timestamp t) {
            return t.toLocalDateTime();
        }
        return null;
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Integer i) {
            return i;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        return null;
    }

    private Boolean toBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number n) {
            return n.intValue() != 0;
        }
        return null;
    }

    private Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length - 1; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private UUID ensureStaffUser(String username) {
        return staffRepo.findStaffIdByUsername(username)
            .orElseGet(() -> {
                UUID id = staffRepo.upsertStaffUser(username, username, "sales", null);
                staffRepo.linkUserProfile(username, id);
                return id;
            });
    }
}











