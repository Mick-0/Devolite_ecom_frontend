package com.verso.ai_client_form.service;

import com.verso.ai_client_form.model.IntakeForm;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class FormCompletionService {

    public CompletionStats calculate(IntakeForm form, boolean storedExistingDomainSecretPresent) {
        if (form == null) {
            return new CompletionStats(0, 0, 0);
        }

        List<Boolean> tracked = new ArrayList<>();

        addAuto(tracked, form.getLegalName());
        addAuto(tracked, form.getIndustry());
        addAuto(tracked, form.getVatNumber());
        addAuto(tracked, form.getStreet());
        addAuto(tracked, form.getCity());
        addAuto(tracked, form.getProvince());
        addAuto(tracked, form.getPostalCode());
        addAuto(tracked, form.getCountryCode());
        addAuto(tracked, form.getCategory());
        addAuto(tracked, form.getFounderYears());
        addAuto(tracked, form.getAnnualRevenue());
        addAuto(tracked, form.getReferralSource());

        addTracked(tracked, form.getContactFullName());
        addTracked(tracked, form.getContactPhone());
        addTracked(tracked, form.getContactEmail());
        addTracked(tracked, form.getContactRoleTitle());

        addAuto(tracked, form.getProjectName());
        addAuto(tracked, form.getProjectKind());
        addAuto(tracked, form.getStatus());
        addAuto(tracked, form.getExpectedOutcome());

        addAuto(tracked, form.getLegalSupportMode());
        addAuto(tracked, form.getLegalReaNumber());
        addAuto(tracked, form.getLegalShareCapital());
        addAuto(tracked, form.getLegalPecEmail());
        addAuto(tracked, form.getLegalFooterCta());

        addTracked(tracked, form.getPrimaryColor());
        addTracked(tracked, form.getSecondaryColor());
        addTracked(tracked, form.getFontPolicy());
        addTracked(tracked, form.getVisualAssetSource());
        addTracked(tracked, form.getToneOfVoice());

        if ("vetrina".equalsIgnoreCase(trim(form.getProjectKind()))) {
            addTracked(tracked, form.getShowcaseGoal());
            addTracked(tracked, form.getShowcasePrimaryCta());
            addTracked(tracked, form.getShowcaseRequestedPages());
            addTracked(tracked, form.getShowcaseHomepageSections());
        }

        if ("ecommerce".equalsIgnoreCase(trim(form.getProjectKind()))) {
            addTracked(tracked, form.getInspirationSites());
            addTracked(tracked, form.getRequestedMenu());
            addTracked(tracked, form.getCopyMode());
        }

        if (Boolean.TRUE.equals(form.getWillingToRegisterNewDomain())) {
            addAuto(tracked, form.getAlternativeDomainToRegister());
        }
        if (Boolean.TRUE.equals(form.getHasExistingDomain())) {
            addAuto(tracked, form.getExistingDomain());
            addAuto(tracked, form.getExistingDomainRegistrar());
            if (Boolean.TRUE.equals(form.getExistingDomainHasCredentials())) {
                addAuto(tracked, form.getExistingDomainCredentialEmail());
                addAuto(tracked, form.getExistingDomainCredentialUsername());
                tracked.add(storedExistingDomainSecretPresent || hasValue(form.getExistingDomainCredentialSecret()));
            }
        } else {
            addAuto(tracked, form.getDomainToRegister());
        }

        if ("ecommerce".equalsIgnoreCase(trim(form.getProjectKind()))) {
            addTracked(tracked, form.getProductCount());
            if (Boolean.TRUE.equals(form.getProductHasVariants())) {
                addTracked(tracked, form.getVariantAxes());
            }
            addTracked(tracked, form.getEcomPanelPlatform());
        }

        int total = tracked.size();
        int completed = (int) tracked.stream().filter(Boolean::booleanValue).count();
        int percent = total == 0 ? 0 : (int) Math.round((completed * 100.0) / total);
        return new CompletionStats(completed, total, percent);
    }

    private void addAuto(List<Boolean> tracked, Object value) {
        tracked.add(hasValue(value));
    }

    private void addTracked(List<Boolean> tracked, Object value) {
        tracked.add(hasValue(value));
    }

    private boolean hasValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String s) {
            return !trim(s).isEmpty();
        }
        return true;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    public record CompletionStats(int completedFields, int totalFields, int progressPercent) {
        public boolean isComplete() {
            return totalFields > 0 && progressPercent >= 100;
        }
    }
}
