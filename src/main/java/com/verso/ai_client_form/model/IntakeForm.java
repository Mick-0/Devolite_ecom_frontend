package com.verso.ai_client_form.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class IntakeForm {

    private UUID companyId;
    private UUID projectId;
    private UUID draftId;

    @NotBlank(message = "Inserisci la ragione sociale.")
    private String legalName;
    private String industry;
    private String vatNumber;

    private String street;
    private String city;
    private String province;
    private String postalCode;
    private String countryCode;
    private String category;
    private Integer founderYears;
    private BigDecimal annualRevenue;
    private String referralSource;
    private Boolean hasPhysicalStore;

    @NotBlank(message = "Inserisci il nome completo del referente.")
    private String contactFullName;
    private String contactPhone;
    private String contactPhoneSecondary;
    private String contactEmail;
    private String contactEmailSecondary;
    private String contactRoleTitle;
    private String contactNotes;

    @NotBlank(message = "Inserisci il nome del progetto.")
    private String projectName;
    @NotBlank(message = "Seleziona il tipo di progetto.")
    @Pattern(regexp = "^(?:vetrina|ecommerce)$", message = "Seleziona un tipo di progetto valido.")
    private String projectKind;
    private String expectedOutcome;
    @Pattern(regexp = "^(?:in_discovery|onboarding|in_production|delivered|archived)?$", message = "Seleziona uno stato progetto valido.")
    private String status;

    private String crmFirstName;
    private String crmLastName;
    private String crmPhone;
    private String crmEmail;
    private String crmEmailSecondary;
    private String crmListName;
    private String crmCompanyNameSnapshot;
    private String crmSector;
    private String crmNotes;
    private String crmInterestTemperature;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate crmFirstCallDate;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate crmSecondCallDate;
    private String crmCta;
    @Pattern(regexp = "^(?:lead|prospect|cliente)?$", message = "Seleziona uno stage CRM valido.")
    private String crmCurrentStage;

    @Pattern(regexp = "^(?:consulente_cliente|integrazione_iubenda|da_definire)?$", message = "Seleziona una modalita legale valida.")
    private String legalSupportMode;
    private String legalReaNumber;
    private BigDecimal legalShareCapital;
    private String legalPecEmail;
    private Boolean legalPrivacyCompleted;
    private Boolean legalTermsCompleted;
    private String legalFooterCta;

    private Boolean logoRestyleRequired;
    private String primaryColor;
    private String secondaryColor;
    private String accentColor1;
    private String accentColor2;
    @Pattern(regexp = "^(?:font_aziendali|da_selezionare)?$", message = "Seleziona una policy font valida.")
    private String fontPolicy;
    @Pattern(regexp = "^(?:fornite_cliente|stock|misto)?$", message = "Seleziona una sorgente asset valida.")
    private String visualAssetSource;
    @Pattern(regexp = "^(?:formale|amichevole|tecnico|emozionale|altro)?$", message = "Seleziona un tono di voce valido.")
    private String toneOfVoice;

    private Boolean marketingHasCrm;
    private Boolean marketingKnowsCrm;
    private Boolean marketingRunsAds;
    private Boolean trackingGa4;
    private Boolean trackingMetaPixel;
    private Boolean trackingTiktokPixel;
    private String marketingNotes;
    private List<String> adChannels;
    private List<String> marketplaceChannels;

    @Pattern(regexp = "^(?:presentazione_azienda|raccolta_contatti|richiesta_preventivi|prenotazioni|download_brochure|supporto_commerciale)?$", message = "Seleziona un obiettivo vetrina valido.")
    private String showcaseGoal;
    private Integer showcasePageCount;
    private String showcaseRequestedPages;
    private String showcaseHomepageSections;
    @Pattern(regexp = "^(?:contattaci|richiedi_preventivo|prenota_chiamata|vieni_in_sede|scarica_brochure)?$", message = "Seleziona una CTA principale valida.")
    private String showcasePrimaryCta;
    private Boolean showcaseHasPortfolio;
    private Boolean showcaseHasTestimonials;
    private Boolean showcaseHasFaq;
    private Boolean showcaseHasBrochure;
    private Boolean showcaseHasBlog;
    private Boolean showcaseNeedsAboutPage;
    private Boolean showcaseNeedsWherePage;
    private Boolean showcaseNeedsServicesPage;
    private Boolean showcaseNeedsContactForm;
    private Boolean showcaseNeedsExternalLinks;
    private String showcaseContactFormEmail;
    private Boolean showcaseHasSeparateShop;
    private String showcaseSeparateShopUrl;

    private String inspirationSites;
    private String requestedMenu;
    @Pattern(regexp = "^(?:scrivi_cliente|genera_ai|misto)?$", message = "Seleziona una modalita copy valida.")
    private String copyMode;
    @Pattern(regexp = "^(?:non_iniziato|in_test|completato)?$", message = "Seleziona uno stato test valido.")
    private String pageTestStatus;
    private Boolean hasExistingEcommerce;
    private String existingEcommerceUrl;

    private Boolean hasExistingDomain;
    private String existingDomain;
    private String domainToRegister;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime domainPurchaseStartedAt;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime domainPurchaseCompletedAt;
    private String preferredMailbox;
    @Pattern(regexp = "^(?:fornita_cliente|genera)?$", message = "Seleziona una modalita mailbox valida.")
    private String mailboxMode;

    private Boolean gbHasProfile;
    private String gbProfileUrl;
    private Boolean gbProfileCreationRequested;
    private String gbKeywords;

    @Pattern(regexp = "^(?:vetrina|ecommerce)?$", message = "Seleziona un servizio valido.")
    private String purchasedService;
    private Boolean paymentReceived;
    private BigDecimal paidAmount;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime paymentMarkedAt;
    private Boolean invoiceGenerated;
    private String invoiceNumber;
    private Boolean autoInvoiceEnabled;

    private String contractContactName;
    private String contractContactEmail;
    @Pattern(regexp = "^(?:bozza|inviato|firmato|annullato)?$", message = "Seleziona uno stato contratto valido.")
    private String contractStatus;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime contractSentAt;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime contractSignedAt;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime aiInitialAnalysisStartedAt;
    private Boolean aiContentResearchEnabled;
    private Boolean aiSeoIndexingEnabled;
    private Integer aiSeoCreditsTotal;
    private Integer aiSeoCreditsUsed;

    private Boolean purchaseEnabled;
    private Boolean autoRenewalEnabled;
    private Boolean ridEnabled;
    private Boolean csvImportEnabled;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime csvImportInstructionsSentAt;
    private Integer productCount;
    private Boolean productHasVariants;
    private List<String> paymentMethods;
    private List<String> carriers;
    private String productCategories;

    private MultipartFile logoFile;
    private String logoComment;
    private MultipartFile visuraFile;
    private String visuraComment;
    private MultipartFile contractFile;
    private String contractComment;
}
















