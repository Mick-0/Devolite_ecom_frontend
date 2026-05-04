package com.verso.ai_client_form.template;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class IntakeTemplateRegressionTests {

    @Test
    void intakeTemplateNoLongerUsesInlineBootstrapScript() throws Exception {
        String template = Files.readString(Path.of("src/main/resources/templates/intake.html"));

        assertFalse(template.contains("window.__confirmedSections"));
        assertFalse(template.contains("window.__enforceSectionLock"));
        assertFalse(template.contains("th:inline=\"javascript\""));
    }

    @Test
    void intakeTemplateUsesMarketingLinkFieldsInsteadOfCheckboxLists() throws Exception {
        String template = Files.readString(Path.of("src/main/resources/templates/intake.html"));

        assertTrue(template.contains("th:field=\"*{crmSystemUrl}\""));
        assertTrue(template.contains("th:field=\"*{ga4PropertyUrl}\""));
        assertTrue(template.contains("th:field=\"*{googleAdsUrl}\""));
        assertTrue(template.contains("th:field=\"*{metaBusinessUrl}\""));
        assertTrue(template.contains("th:field=\"*{otherMarketingLinks}\""));
        assertTrue(template.contains("th:field=\"*{otherMarketplaceLinks}\""));
        assertFalse(template.contains("th:field=\"*{marketingHasCrm}\""));
        assertFalse(template.contains("th:field=\"*{trackingGa4}\""));
        assertFalse(template.contains("th:field=\"*{adChannels}\""));
        assertFalse(template.contains("th:field=\"*{marketplaceChannels}\""));
    }
}
