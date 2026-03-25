package com.verso.ai_client_form.controller;

import java.lang.reflect.Field;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.verso.ai_client_form.model.IntakeForm;
import com.verso.ai_client_form.model.ProjectSummary;
import com.verso.ai_client_form.service.IntakeService;
import jakarta.validation.Valid;

@Controller
public class IntakeController {

    private final IntakeService intakeService;

    public IntakeController(IntakeService intakeService) {
        this.intakeService = intakeService;
    }

    @GetMapping("/")
    public String root() {
        return "redirect:/intake";
    }

    @GetMapping("/intake")
    public String showForm(@RequestParam(value = "projectId", required = false) UUID projectId,
                           @RequestParam(value = "projectName", required = false) String projectName,
                           @RequestParam(value = "draftId", required = false) UUID draftId,
                           @RequestParam(value = "enforceLock", required = false) Boolean enforceLockOverride,
                           @ModelAttribute("prefill") IntakeForm prefill,
                           Model model) {
        UUID resolvedProjectId = projectId;
        if (resolvedProjectId == null && projectName != null && !projectName.isBlank()) {
            String trimmedName = projectName.trim();
            resolvedProjectId = intakeService.findProjectIdByName(trimmedName);
            if (resolvedProjectId == null) {
                model.addAttribute("projectLookupError", "Nessun progetto trovato con il nome \"" + trimmedName + "\".");
            }
        }
        IntakeForm form = (resolvedProjectId != null) ? intakeService.load(resolvedProjectId) : new IntakeForm();
        applyPrefill(form, prefill);
        UUID resolvedDraftId = intakeService.resolveDraftId(draftId, resolvedProjectId);
        form.setDraftId(resolvedDraftId);
        boolean enforceLock = (enforceLockOverride != null) ? enforceLockOverride : intakeService.isEnforceSectionOrder();
        model.addAttribute("confirmedSections", intakeService.loadConfirmedSections(resolvedDraftId));
        model.addAttribute("enforceSectionLock", enforceLock);
        model.addAttribute("form", form);
        return "intake";
    }

    private void applyPrefill(IntakeForm target, IntakeForm prefill) {
        if (target == null || prefill == null) {
            return;
        }
        Set<String> skip = Set.of("companyId", "projectId", "draftId", "logoFile", "visuraFile", "contractFile");
        Field[] fields = IntakeForm.class.getDeclaredFields();
        for (Field field : fields) {
            if (skip.contains(field.getName())) {
                continue;
            }
            field.setAccessible(true);
            try {
                Object value = field.get(prefill);
                if (value == null) {
                    continue;
                }
                if (value instanceof String && ((String) value).isBlank()) {
                    continue;
                }
                if (value instanceof List && ((List<?>) value).isEmpty()) {
                    continue;
                }
                field.set(target, value);
            } catch (IllegalAccessException ignored) {
            }
        }
    }

    @GetMapping(path = "/intake/projects", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public List<ProjectSummary> listProjects(@RequestParam(value = "limit", required = false) Integer limit) {
        int capped = (limit == null) ? 50 : Math.min(Math.max(limit, 1), 200);
        return intakeService.listRecentProjects(capped);
    }
    @PostMapping(path = "/intake", consumes = "multipart/form-data")
    public String submit(@Valid @ModelAttribute("form") IntakeForm form,
                         BindingResult bindingResult,
                         Principal principal,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "intake";
        }
        UUID projectId = intakeService.save(form, principal.getName());
        redirectAttributes.addFlashAttribute("saved", true);
        return "redirect:/intake?projectId=" + projectId;
    }

    @PostMapping(path = "/intake/section/confirm", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> confirmSection(@RequestBody ConfirmSectionRequest request,
                                                              Principal principal) {
        if (request == null || request.sectionKey == null || request.sectionKey.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "sectionKey required"));
        }
        try {
            UUID draftId = intakeService.getOrCreateDraft(request.draftId, request.projectId);
            intakeService.confirmSection(draftId, request.sectionKey, principal.getName());
            List<String> confirmed = intakeService.loadConfirmedSections(draftId);
            return ResponseEntity.ok(Map.of("confirmedSections", confirmed, "draftId", draftId));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping(path = "/intake/section/edit", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> editSection(@RequestBody ConfirmSectionRequest request,
                                                           Principal principal) {
        if (request.draftId == null || request.sectionKey == null || request.sectionKey.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "draftId and sectionKey required"));
        }
        try {
            intakeService.unconfirmSection(request.draftId, request.sectionKey, principal.getName());
            List<String> confirmed = intakeService.loadConfirmedSections(request.draftId);
            return ResponseEntity.ok(Map.of("confirmedSections", confirmed));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    public static class ConfirmSectionRequest {
        public UUID draftId;
        public UUID projectId;
        public String sectionKey;
    }
}








