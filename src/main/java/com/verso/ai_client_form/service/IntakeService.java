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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
    private final PipelineService pipelineService;

    @Value("${app.intake.enforce-section-order:true}")
    private boolean enforceSectionOrder;

    private final List<String> sectionOrder = List.of(
        "company", "contact", "project", "crm", "legal", "brand", "marketing",
        "vetrina", "site", "domain", "local", "commercial", "ai", "ecommerce", "files"
    );

    public IntakeService(IntakeRepository repo, StaffUserRepository staffRepo, StorageService storageService, PipelineService pipelineService) {
        this.repo = repo;
        this.staffRepo = staffRepo;
        this.storageService = storageService;
        this.pipelineService = pipelineService;
    }

    public boolean isEnforceSectionOrder() {
        return enforceSectionOrder;
    }

    public boolean isSectionApplicable(String sectionKey, String projectKind) {
        String normalizedKind = normalizeProjectKind(projectKind);
        return switch (sectionKey) {
            case "vetrina" -> "vetrina".equalsIgnoreCase(normalizedKind);
            case "site", "ecommerce" -> "ecommerce".equalsIgnoreCase(normalizedKind);
            default -> true;
        };
    }

    @Transactional(readOnly = true)
    public UUID findProjectIdByName(String projectName) {
        return repo.findProjectIdByName(projectName).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<ProjectSummary> listRecentProjects(int limit, String query, String sortBy, String sortDir) {
        return repo.listRecentProjects(limit, query, sortBy, sortDir);
    }

    @Transactional
    public UUID save(IntakeForm form, String username) {
        UUID staffUserId = ensureStaffUser(username);
        String projectKind = normalizeProjectKind(form.getProjectKind());

        String normalizedVat = emptyToNull(form.getVatNumber());
        form.setVatNumber(normalizedVat);

        UUID companyId = form.getCompanyId();
        if (companyId == null) {
            companyId = repo.findCompanyIdByVat(normalizedVat).orElse(null);
        }
        companyId = repo.upsertCompany(companyId, form.getLegalName(), form.getIndustry(), normalizedVat);

        repo.upsertCompanyProfile(companyId, map(
            "street", form.getStreet(),
            "city", form.getCity(),
            "province", form.getProvince(),
            "postal_code", form.getPostalCode(),
            "country_code", emptyToNull(form.getCountryCode()),
            "category", form.getCategory(),
            "founder_years", form.getFounderYears(),
            "annual_revenue", form.getAnnualRevenue(),
            "referral_source", form.getReferralSource(),
            "has_physical_store", bool(form.getHasPhysicalStore())
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
            "project_kind", projectKind,
            "expected_outcome", form.getExpectedOutcome(),
            "status", defaultIfBlank(form.getStatus(), "onboarding")
        ));

        repo.initProjectSteps(projectId, projectKind);

        repo.upsertCrmContact(companyId, map(
            "first_name", form.getCrmFirstName(),
            "last_name", form.getCrmLastName(),
            "phone", form.getCrmPhone(),
            "email", form.getCrmEmail(),
            "email_secondary", form.getCrmEmailSecondary(),
            "list_name", form.getCrmListName(),
            "company_name_snapshot", form.getCrmCompanyNameSnapshot(),
            "sector", form.getCrmSector(),
            "vat_number", normalizedVat,
            "notes", form.getCrmNotes(),
            "interest_temperature", form.getCrmInterestTemperature(),
            "first_call_date", form.getCrmFirstCallDate(),
            "second_call_date", form.getCrmSecondCallDate(),
            "cta", form.getCrmCta(),
            "current_stage", defaultIfBlank(form.getCrmCurrentStage(), "lead")
        ), staffUserId);

        repo.upsertLegalProfile(projectId, map(
            "legal_support_mode", emptyToNull(form.getLegalSupportMode()),
            "vat_number", normalizedVat,
            "rea_number", form.getLegalReaNumber(),
            "share_capital", form.getLegalShareCapital(),
            "pec_email", form.getLegalPecEmail(),
            "privacy_page_completed", bool(form.getLegalPrivacyCompleted()),
            "terms_conditions_completed", bool(form.getLegalTermsCompleted()),
            "footer_cta", form.getLegalFooterCta()
        ));

        repo.upsertBrandProfile(projectId, map(
            "logo_restyle_required", bool(form.getLogoRestyleRequired()),
            "primary_color", emptyToNull(form.getPrimaryColor()),
            "secondary_color", emptyToNull(form.getSecondaryColor()),
            "accent_color_1", emptyToNull(form.getAccentColor1()),
            "accent_color_2", emptyToNull(form.getAccentColor2()),
            "font_policy", emptyToNull(form.getFontPolicy()),
            "visual_asset_source", emptyToNull(form.getVisualAssetSource()),
            "tone_of_voice", emptyToNull(form.getToneOfVoice())
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

        if ("vetrina".equalsIgnoreCase(projectKind)) {
            Integer showcasePageCount = form.getShowcasePageCount();
            if (showcasePageCount != null && showcasePageCount < 0) {
                showcasePageCount = 0;
            }
            repo.upsertShowcaseBrief(projectId, map(
                "site_goal", emptyToNull(form.getShowcaseGoal()),
                "page_count", showcasePageCount,
                "requested_pages", form.getShowcaseRequestedPages(),
                "homepage_sections", form.getShowcaseHomepageSections(),
                "primary_cta", emptyToNull(form.getShowcasePrimaryCta()),
                "has_portfolio", bool(form.getShowcaseHasPortfolio()),
                "has_testimonials", bool(form.getShowcaseHasTestimonials()),
                "has_faq", bool(form.getShowcaseHasFaq()),
                "has_brochure", bool(form.getShowcaseHasBrochure()),
                "has_blog", bool(form.getShowcaseHasBlog()),
                "needs_about_page", bool(form.getShowcaseNeedsAboutPage()),
                "needs_where_page", bool(form.getShowcaseNeedsWherePage()),
                "needs_services_page", bool(form.getShowcaseNeedsServicesPage()),
                "needs_contact_form", bool(form.getShowcaseNeedsContactForm()),
                "needs_external_links", bool(form.getShowcaseNeedsExternalLinks()),
                "contact_form_email", emptyToNull(form.getShowcaseContactFormEmail()),
                "has_separate_shop", bool(form.getShowcaseHasSeparateShop()),
                "separate_shop_url", emptyToNull(form.getShowcaseSeparateShopUrl())
            ));
        }

        repo.upsertDomainSetup(projectId, map(
            "has_existing_domain", bool(form.getHasExistingDomain()),
            "existing_domain", form.getExistingDomain(),
            "existing_registrar", emptyToNull(form.getExistingDomainRegistrar()),
            "existing_dns_provider", emptyToNull(form.getExistingDomainDnsProvider()),
            "existing_has_credentials", bool(form.getExistingDomainHasCredentials()),
            "existing_credential_username", emptyToNull(form.getExistingDomainCredentialUsername()),
            "existing_credential_email", emptyToNull(form.getExistingDomainCredentialEmail()),
            "existing_credential_secret", emptyToNull(form.getExistingDomainCredentialSecret()),
            "existing_two_factor_enabled", bool(form.getExistingDomainTwoFactorEnabled()),
            "existing_nameservers", emptyToNull(form.getExistingDomainNameservers()),
            "existing_expiry_date", form.getExistingDomainExpiryDate(),
            "existing_transfer_locked", bool(form.getExistingDomainTransferLocked()),
            "domain_to_register", form.getDomainToRegister(),
            "alternative_domain_to_register", emptyToNull(form.getAlternativeDomainToRegister()),
            "new_registrar", emptyToNull(form.getNewDomainRegistrar()),
            "new_credential_username", emptyToNull(form.getNewDomainCredentialUsername()),
            "new_credential_email", emptyToNull(form.getNewDomainCredentialEmail()),
            "new_credential_secret", emptyToNull(form.getNewDomainCredentialSecret()),
            "willing_to_register_new_domain", bool(form.getWillingToRegisterNewDomain()),
            "domain_issues", emptyToNull(form.getDomainIssues()),
            "domain_problem_severity", form.getDomainProblemSeverity(),
            "reachability_checked_at", form.getDomainReachabilityCheckedAt(),
            "reachability_status", emptyToNull(form.getDomainReachabilityStatus()),
            "reachability_details", emptyToNull(form.getDomainReachabilityDetails()),
            "domain_purchase_started_at", form.getDomainPurchaseStartedAt(),
            "domain_purchase_completed_at", form.getDomainPurchaseCompletedAt(),
            "preferred_mailbox", form.getPreferredMailbox(),
            "mailbox_mode", emptyToNull(form.getMailboxMode())
        ));

        repo.upsertGoogleBusiness(projectId, map(
            "has_profile", bool(form.getGbHasProfile()),
            "profile_url", form.getGbProfileUrl(),
            "profile_creation_requested", bool(form.getGbProfileCreationRequested())
        ));

        repo.replaceKeywords(projectId, parseList(form.getGbKeywords()));

        repo.upsertOrderAdministration(projectId, map(
            "purchased_service", emptyToNull(form.getPurchasedService()),
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
            "status", defaultIfBlank(emptyToNull(form.getContractStatus()), "bozza"),
            "sent_at", form.getContractSentAt(),
            "signed_at", form.getContractSignedAt()
        ));
        }

        Integer seoTotal = defaultInt(form.getAiSeoCreditsTotal(), 0);
        Integer seoUsed = defaultInt(form.getAiSeoCreditsUsed(), 0);
        if (seoTotal < 0) {
            seoTotal = 0;
        }
        if (seoUsed < 0) {
            seoUsed = 0;
        }
        if (seoUsed > seoTotal) {
            seoUsed = seoTotal;
        }
        repo.upsertAiSettings(projectId, map(
            "initial_analysis_started_at", form.getAiInitialAnalysisStartedAt(),
            "content_research_enabled", bool(form.getAiContentResearchEnabled()),
            "seo_indexing_enabled", bool(form.getAiSeoIndexingEnabled()),
            "seo_credits_total", seoTotal,
            "seo_credits_used", seoUsed
        ));

	        if ("ecommerce".equalsIgnoreCase(projectKind)) {
	            repo.upsertSiteBrief(projectId, map(
	                "inspiration_sites", form.getInspirationSites(),
	                "requested_menu", form.getRequestedMenu(),
	                "copy_mode", emptyToNull(form.getCopyMode()),
	                "page_test_status", emptyToNull(form.getPageTestStatus()),
	                "has_existing_ecommerce", bool(form.getHasExistingEcommerce()),
	                "existing_ecommerce_url", emptyToNull(form.getExistingEcommerceUrl())
	            ));
	            Integer productCount = form.getProductCount();
	            if (productCount != null && productCount < 0) {
	                productCount = 0;
	            }
	            boolean hasVariants = bool(form.getProductHasVariants());
	            String variantMode = emptyToNull(form.getVariantManagementMode());
	            String variantAxes = emptyToNull(form.getVariantAxes());
	            Integer variantTotalSku = form.getVariantTotalSkuCount();
	            Integer variantSeparateProducts = form.getVariantSeparateProductCount();
	            boolean variantsAffectPrice = bool(form.getVariantsAffectPrice());
	            boolean variantsAffectStock = bool(form.getVariantsAffectStock());
	            if (!hasVariants) {
	                variantMode = null;
	                variantAxes = null;
	                variantTotalSku = null;
	                variantSeparateProducts = null;
	                variantsAffectPrice = false;
	                variantsAffectStock = false;
	            } else {
	                if (variantTotalSku != null && variantTotalSku < 0) {
	                    variantTotalSku = 0;
	                }
	                if (variantSeparateProducts != null && variantSeparateProducts < 0) {
	                    variantSeparateProducts = 0;
	                }
	                if (!"separate_products".equalsIgnoreCase(variantMode)) {
	                    variantSeparateProducts = null;
	                }
	            }

	            boolean panelHasCreds = bool(form.getEcomPanelHasCredentials());
	            String panelPlatform = emptyToNull(form.getEcomPanelPlatform());
	            String panelUrl = emptyToNull(form.getEcomPanelUrl());
	            String panelEmail = emptyToNull(form.getEcomPanelCredentialEmail());
	            String panelUser = emptyToNull(form.getEcomPanelCredentialUsername());
	            String panelSecret = emptyToNull(form.getEcomPanelCredentialSecret());
	            boolean panel2fa = bool(form.getEcomPanelTwoFactorEnabled());
	            String panelNotes = emptyToNull(form.getEcomPanelNotes());
	            if (!panelHasCreds) {
	                panelEmail = null;
	                panelUser = null;
	                panelSecret = null;
	                panel2fa = false;
	            }

	            repo.upsertStoreSetup(projectId, map(
	                "purchase_enabled", bool(form.getPurchaseEnabled()),
	                "auto_renewal_enabled", bool(form.getAutoRenewalEnabled()),
	                "rid_enabled", bool(form.getRidEnabled()),
	                "csv_import_enabled", bool(form.getCsvImportEnabled()),
	                "csv_import_instructions_sent_at", form.getCsvImportInstructionsSentAt(),
	                "product_count", productCount,
	                "has_product_variants", hasVariants,
	                "variant_management_mode", variantMode,
	                "variant_axes", variantAxes,
	                "variant_total_sku_count", variantTotalSku,
	                "variant_separate_product_count", variantSeparateProducts,
	                "variants_affect_price", variantsAffectPrice,
	                "variants_affect_stock", variantsAffectStock,
	                "ecom_panel_platform", panelPlatform,
	                "ecom_panel_url", panelUrl,
	                "ecom_panel_has_credentials", panelHasCreds,
	                "ecom_panel_credential_email", panelEmail,
	                "ecom_panel_credential_username", panelUser,
	                "ecom_panel_credential_secret", panelSecret,
	                "ecom_panel_two_factor_enabled", panel2fa,
	                "ecom_panel_notes", panelNotes
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

        // Mark pipeline row as completed (if the form was opened from a pipeline).
        if (form.getPipelineRowId() != null) {
            pipelineService.markRowDone(form.getPipelineRowId(), projectId);
        }
        // Fallback: if the user created the project starting from a pipeline row name.
        pipelineService.markDoneByProjectName(form.getProjectName(), projectId);

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
    public void confirmSection(UUID draftId, String sectionKey, String username, String projectKind) {
        if (!sectionOrder.contains(sectionKey)) {
            throw new IllegalArgumentException("Unknown section");
        }
        String normalizedKind = normalizeProjectKind(projectKind);
        if (!isSectionApplicable(sectionKey, normalizedKind)) {
            throw new IllegalArgumentException("Sezione non applicabile al tipo di progetto selezionato.");
        }
        List<String> confirmed = repo.findConfirmedSections(draftId);
        int index = sectionOrder.indexOf(sectionKey);
        if (enforceSectionOrder && index > 0) {
            String prev = previousApplicableSection(sectionKey, normalizedKind);
            if (prev != null && !confirmed.contains(prev)) {
                throw new IllegalArgumentException("Previous section not confirmed");
            }
        }
        UUID staffUserId = ensureStaffUser(username);
        repo.confirmSection(draftId, sectionKey, staffUserId);
    }

    @Transactional
    public void unconfirmSection(UUID draftId, String sectionKey, String username, String projectKind) {
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
                form.setHasPhysicalStore(toBoolean(row.get("has_physical_store")));
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
                String crmVat = (String) row.get("vat_number");
                if ((form.getVatNumber() == null || form.getVatNumber().isBlank()) && crmVat != null && !crmVat.isBlank()) {
                    form.setVatNumber(crmVat);
                }
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
            String legalVat = (String) row.get("vat_number");
            if ((form.getVatNumber() == null || form.getVatNumber().isBlank()) && legalVat != null && !legalVat.isBlank()) {
                form.setVatNumber(legalVat);
            }
            form.setLegalReaNumber((String) row.get("rea_number"));
            form.setLegalShareCapital((java.math.BigDecimal) row.get("share_capital"));
            form.setLegalPecEmail((String) row.get("pec_email"));
            form.setLegalPrivacyCompleted(toBoolean(row.get("privacy_page_completed")));
            form.setLegalTermsCompleted(toBoolean(row.get("terms_conditions_completed")));
            form.setLegalFooterCta((String) row.get("footer_cta"));
        });

        repo.findBrandProfile(projectId).ifPresent(row -> {
            form.setLogoRestyleRequired(toBoolean(row.get("logo_restyle_required")));
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

        repo.findShowcaseBrief(projectId).ifPresent(row -> {
            form.setShowcaseGoal((String) row.get("site_goal"));
            form.setShowcasePageCount(toInteger(row.get("page_count")));
            form.setShowcaseRequestedPages((String) row.get("requested_pages"));
            form.setShowcaseHomepageSections((String) row.get("homepage_sections"));
            form.setShowcasePrimaryCta((String) row.get("primary_cta"));
            form.setShowcaseHasPortfolio(toBoolean(row.get("has_portfolio")));
            form.setShowcaseHasTestimonials(toBoolean(row.get("has_testimonials")));
            form.setShowcaseHasFaq(toBoolean(row.get("has_faq")));
            form.setShowcaseHasBrochure(toBoolean(row.get("has_brochure")));
            form.setShowcaseHasBlog(toBoolean(row.get("has_blog")));
            form.setShowcaseNeedsAboutPage(toBoolean(row.get("needs_about_page")));
            form.setShowcaseNeedsWherePage(toBoolean(row.get("needs_where_page")));
            form.setShowcaseNeedsServicesPage(toBoolean(row.get("needs_services_page")));
            form.setShowcaseNeedsContactForm(toBoolean(row.get("needs_contact_form")));
            form.setShowcaseNeedsExternalLinks(toBoolean(row.get("needs_external_links")));
            form.setShowcaseContactFormEmail((String) row.get("contact_form_email"));
            form.setShowcaseHasSeparateShop(toBoolean(row.get("has_separate_shop")));
            form.setShowcaseSeparateShopUrl((String) row.get("separate_shop_url"));
        });

        repo.findSiteBrief(projectId).ifPresent(row -> {
            form.setInspirationSites((String) row.get("inspiration_sites"));
            form.setRequestedMenu((String) row.get("requested_menu"));
            form.setCopyMode((String) row.get("copy_mode"));
            form.setPageTestStatus((String) row.get("page_test_status"));
            form.setHasExistingEcommerce(toBoolean(row.get("has_existing_ecommerce")));
            form.setExistingEcommerceUrl((String) row.get("existing_ecommerce_url"));
            if ("vetrina".equalsIgnoreCase(form.getProjectKind())) {
                if (isBlank(form.getShowcaseRequestedPages())) {
                    form.setShowcaseRequestedPages((String) row.get("requested_menu"));
                }
                if (form.getShowcaseNeedsAboutPage() == null) {
                    form.setShowcaseNeedsAboutPage(toBoolean(row.get("needs_about_page")));
                }
                if (form.getShowcaseNeedsWherePage() == null) {
                    form.setShowcaseNeedsWherePage(toBoolean(row.get("needs_where_page")));
                }
                if (form.getShowcaseNeedsServicesPage() == null) {
                    form.setShowcaseNeedsServicesPage(toBoolean(row.get("needs_services_page")));
                }
                if (form.getShowcaseNeedsContactForm() == null) {
                    form.setShowcaseNeedsContactForm(toBoolean(row.get("needs_contact_form")));
                }
                if (form.getShowcaseNeedsExternalLinks() == null) {
                    form.setShowcaseNeedsExternalLinks(toBoolean(row.get("needs_external_links")));
                }
                if (isBlank(form.getShowcaseContactFormEmail())) {
                    form.setShowcaseContactFormEmail((String) row.get("contact_form_email"));
                }
                if (form.getShowcaseHasSeparateShop() == null) {
                    form.setShowcaseHasSeparateShop(toBoolean(row.get("has_existing_ecommerce")));
                }
                if (isBlank(form.getShowcaseSeparateShopUrl())) {
                    form.setShowcaseSeparateShopUrl((String) row.get("existing_ecommerce_url"));
                }
            }
        });

        repo.findDomainSetup(projectId).ifPresent(row -> {
            form.setHasExistingDomain(toBoolean(row.get("has_existing_domain")));
            form.setExistingDomain((String) row.get("existing_domain"));
            form.setExistingDomainRegistrar((String) row.get("existing_registrar"));
            form.setExistingDomainDnsProvider((String) row.get("existing_dns_provider"));
            form.setExistingDomainHasCredentials(toBoolean(row.get("existing_has_credentials")));
            form.setExistingDomainCredentialUsername((String) row.get("existing_credential_username"));
            form.setExistingDomainCredentialEmail((String) row.get("existing_credential_email"));
            // Do not prefill secrets back into the form.
            form.setExistingDomainTwoFactorEnabled(toBoolean(row.get("existing_two_factor_enabled")));
            form.setExistingDomainNameservers((String) row.get("existing_nameservers"));
            form.setExistingDomainExpiryDate(toLocalDate(row.get("existing_expiry_date")));
            form.setExistingDomainTransferLocked(toBoolean(row.get("existing_transfer_locked")));
            form.setDomainToRegister((String) row.get("domain_to_register"));
            form.setAlternativeDomainToRegister((String) row.get("alternative_domain_to_register"));
            form.setNewDomainRegistrar((String) row.get("new_registrar"));
            form.setNewDomainCredentialUsername((String) row.get("new_credential_username"));
            form.setNewDomainCredentialEmail((String) row.get("new_credential_email"));
            // Do not prefill secrets back into the form.
            form.setWillingToRegisterNewDomain(toBoolean(row.get("willing_to_register_new_domain")));
            form.setDomainIssues((String) row.get("domain_issues"));
            form.setDomainProblemSeverity(toInteger(row.get("domain_problem_severity")));
            form.setDomainReachabilityCheckedAt(toLocalDateTime(row.get("reachability_checked_at")));
            form.setDomainReachabilityStatus((String) row.get("reachability_status"));
            form.setDomainReachabilityDetails((String) row.get("reachability_details"));
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
	            form.setProductCount(toInteger(row.get("product_count")));
	            form.setProductHasVariants(toBoolean(row.get("has_product_variants")));
	            form.setVariantManagementMode((String) row.get("variant_management_mode"));
	            form.setVariantAxes((String) row.get("variant_axes"));
	            form.setVariantTotalSkuCount(toInteger(row.get("variant_total_sku_count")));
	            form.setVariantSeparateProductCount(toInteger(row.get("variant_separate_product_count")));
	            form.setVariantsAffectPrice(toBoolean(row.get("variants_affect_price")));
	            form.setVariantsAffectStock(toBoolean(row.get("variants_affect_stock")));
	            form.setEcomPanelPlatform((String) row.get("ecom_panel_platform"));
	            form.setEcomPanelUrl((String) row.get("ecom_panel_url"));
	            form.setEcomPanelHasCredentials(toBoolean(row.get("ecom_panel_has_credentials")));
	            form.setEcomPanelCredentialEmail((String) row.get("ecom_panel_credential_email"));
	            form.setEcomPanelCredentialUsername((String) row.get("ecom_panel_credential_username"));
	            // do not prefill secret
	            form.setEcomPanelTwoFactorEnabled(toBoolean(row.get("ecom_panel_two_factor_enabled")));
	            form.setEcomPanelNotes((String) row.get("ecom_panel_notes"));
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
        registerRollbackCleanup(stored.relativePath());
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

    private void registerRollbackCleanup(String relativePath) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                    storageService.delete(relativePath);
                }
            }
        });
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

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String defaultIfBlank(String s, String fallback) {
        return (s == null || s.isBlank()) ? fallback : s;
    }

    private String normalizeProjectKind(String projectKind) {
        return defaultIfBlank(projectKind, "vetrina");
    }

    private String previousApplicableSection(String sectionKey, String projectKind) {
        int index = sectionOrder.indexOf(sectionKey);
        for (int i = index - 1; i >= 0; i--) {
            String candidate = sectionOrder.get(i);
            if (isSectionApplicable(candidate, projectKind)) {
                return candidate;
            }
        }
        return null;
    }

    private String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private Integer defaultInt(Integer value, int fallback) {
        return value == null ? fallback : value;
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











