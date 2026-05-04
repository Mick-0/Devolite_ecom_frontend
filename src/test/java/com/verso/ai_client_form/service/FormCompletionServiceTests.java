package com.verso.ai_client_form.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.verso.ai_client_form.model.IntakeForm;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class FormCompletionServiceTests {

    private final FormCompletionService service = new FormCompletionService();

    @Test
    void calculatesFullCompletionForVetrina() {
        IntakeForm form = new IntakeForm();
        form.setLegalName("Acme Srl");
        form.setIndustry("Retail");
        form.setVatNumber("IT12345678901");
        form.setStreet("Via Roma 1");
        form.setCity("Milano");
        form.setProvince("MI");
        form.setPostalCode("20100");
        form.setCountryCode("IT");
        form.setCategory("Abbigliamento");
        form.setFounderYears(2012);
        form.setAnnualRevenue(new BigDecimal("1200000"));
        form.setReferralSource("Passaparola");
        form.setContactFullName("Mario Rossi");
        form.setContactPhone("+39 021234567");
        form.setContactEmail("mario@acme.it");
        form.setContactRoleTitle("CEO");
        form.setProjectName("Acme Vetrina");
        form.setProjectKind("vetrina");
        form.setStatus("onboarding");
        form.setExpectedOutcome("Lead");
        form.setLegalSupportMode("da_definire");
        form.setLegalReaNumber("MI123");
        form.setLegalShareCapital(new BigDecimal("50000"));
        form.setLegalPecEmail("pec@acme.it");
        form.setLegalFooterCta("Contattaci");
        form.setPrimaryColor("#112233");
        form.setSecondaryColor("#445566");
        form.setFontPolicy("font_aziendali");
        form.setVisualAssetSource("fornite_cliente");
        form.setToneOfVoice("formale");
        form.setShowcaseGoal("presentazione_azienda");
        form.setShowcasePrimaryCta("contattaci");
        form.setShowcaseRequestedPages("Home\nContatti");
        form.setShowcaseHomepageSections("Hero\nCTA");
        form.setHasExistingDomain(false);
        form.setWillingToRegisterNewDomain(false);
        form.setDomainToRegister("acme.it");

        FormCompletionService.CompletionStats stats = service.calculate(form, false);

        assertEquals(100, stats.progressPercent());
        assertTrue(stats.isComplete());
    }

    @Test
    void usesStoredSecretForDomainCompletion() {
        IntakeForm form = new IntakeForm();
        form.setLegalName("Acme Srl");
        form.setIndustry("Retail");
        form.setVatNumber("IT12345678901");
        form.setStreet("Via Roma 1");
        form.setCity("Milano");
        form.setProvince("MI");
        form.setPostalCode("20100");
        form.setCountryCode("IT");
        form.setCategory("Abbigliamento");
        form.setFounderYears(2012);
        form.setAnnualRevenue(new BigDecimal("1200000"));
        form.setReferralSource("Inbound");
        form.setContactFullName("Mario Rossi");
        form.setContactPhone("+39 021234567");
        form.setContactEmail("mario@acme.it");
        form.setContactRoleTitle("CEO");
        form.setProjectName("Acme Shop");
        form.setProjectKind("ecommerce");
        form.setStatus("onboarding");
        form.setExpectedOutcome("Vendite");
        form.setLegalSupportMode("da_definire");
        form.setLegalReaNumber("MI123");
        form.setLegalShareCapital(new BigDecimal("50000"));
        form.setLegalPecEmail("pec@acme.it");
        form.setLegalFooterCta("Contattaci");
        form.setPrimaryColor("#112233");
        form.setSecondaryColor("#445566");
        form.setFontPolicy("font_aziendali");
        form.setVisualAssetSource("fornite_cliente");
        form.setToneOfVoice("formale");
        form.setInspirationSites("https://example.com");
        form.setRequestedMenu("Home\nShop");
        form.setCopyMode("misto");
        form.setHasExistingDomain(true);
        form.setExistingDomain("acme.it");
        form.setExistingDomainRegistrar("Aruba");
        form.setExistingDomainHasCredentials(true);
        form.setExistingDomainCredentialEmail("admin@acme.it");
        form.setExistingDomainCredentialUsername("admin");
        form.setProductCount(20);
        form.setProductHasVariants(false);
        form.setEcomPanelPlatform("Shopify");

        FormCompletionService.CompletionStats withoutSecret = service.calculate(form, false);
        FormCompletionService.CompletionStats withSecret = service.calculate(form, true);

        assertFalse(withoutSecret.isComplete());
        assertEquals(100, withSecret.progressPercent());
        assertTrue(withSecret.isComplete());
    }
}
