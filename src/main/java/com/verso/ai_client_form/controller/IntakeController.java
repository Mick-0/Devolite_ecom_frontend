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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.verso.ai_client_form.model.ClientCompletionSummary;
import com.verso.ai_client_form.model.IntakeForm;
import com.verso.ai_client_form.model.ProjectSummary;
import com.verso.ai_client_form.service.IntakeService;
import com.verso.ai_client_form.service.PipelineService;
import jakarta.validation.Valid;

@Controller
public class IntakeController {

    private final IntakeService intakeService;
    private final PipelineService pipelineService;

    public IntakeController(IntakeService intakeService, PipelineService pipelineService) {
        this.intakeService = intakeService;
        this.pipelineService = pipelineService;
    }

    @GetMapping("/")
    public String root() {
        return "redirect:/dashboard";
    }

    @GetMapping("/intake")
    public String showForm(@RequestParam(value = "projectId", required = false) UUID projectId,
                           @RequestParam(value = "projectName", required = false) String projectName,
                           @RequestParam(value = "draftId", required = false) UUID draftId,
                           @RequestParam(value = "pipelineRowId", required = false) UUID pipelineRowId,
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
        if (pipelineRowId != null) {
            IntakeForm pipelinePrefill = pipelineService.buildPrefillFromRow(pipelineRowId);
            applyPrefill(form, pipelinePrefill);
            form.setPipelineRowId(pipelineRowId);
        }
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
    public List<ProjectSummary> listProjects(@RequestParam(value = "limit", required = false) Integer limit,
                                             @RequestParam(value = "q", required = false) String query,
                                             @RequestParam(value = "sortBy", required = false) String sortBy,
                                             @RequestParam(value = "sortDir", required = false) String sortDir) {
        int capped = (limit == null) ? 50 : Math.min(Math.max(limit, 1), 200);
        return intakeService.listRecentProjects(capped, query, sortBy, sortDir);
    }

    @GetMapping("/clients/active")
    public String activeClientsPage(@RequestParam(value = "limit", required = false) Integer limit,
                                    @RequestParam(value = "q", required = false) String query,
                                    @RequestParam(value = "sortBy", required = false) String sortBy,
                                    @RequestParam(value = "sortDir", required = false) String sortDir,
                                    Model model) {
        int capped = (limit == null) ? 100 : Math.min(Math.max(limit, 1), 200);
        String effectiveSortBy = (sortBy == null || sortBy.isBlank()) ? "updatedAt" : sortBy;
        String effectiveSortDir = "asc".equalsIgnoreCase(sortDir) ? "asc" : "desc";
        List<ClientCompletionSummary> clients = intakeService.listCompletedClients(capped, query, effectiveSortBy, effectiveSortDir);
        model.addAttribute("clients", clients);
        model.addAttribute("query", query == null ? "" : query);
        model.addAttribute("sortBy", effectiveSortBy);
        model.addAttribute("sortDir", effectiveSortDir);
        model.addAttribute("limit", capped);
        return "clients-active";
    }

    @PostMapping(path = "/intake", consumes = "multipart/form-data")
    public String submit(@Valid @ModelAttribute("form") IntakeForm form,
                         BindingResult bindingResult,
                         Principal principal,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        validateFiles(form, bindingResult);
        if (bindingResult.hasErrors()) {
            model.addAttribute("confirmedSections", intakeService.loadConfirmedSections(form.getDraftId()));
            model.addAttribute("enforceSectionLock", intakeService.isEnforceSectionOrder());
            return "intake";
        }
        try {
            UUID projectId = intakeService.save(form, principal.getName());
            redirectAttributes.addFlashAttribute("saved", true);
            return "redirect:/intake?projectId=" + projectId;
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            bindingResult.rejectValue("projectName", "projectName.duplicate", "Esiste gia un progetto con questo nome.");
            model.addAttribute("confirmedSections", intakeService.loadConfirmedSections(form.getDraftId()));
            model.addAttribute("enforceSectionLock", intakeService.isEnforceSectionOrder());
            return "intake";
        }
    }

    private void validateFiles(IntakeForm form, BindingResult bindingResult) {
        if (form == null) {
            return;
        }
        validateFile(
            form.getLogoFile(),
            "logoFile",
            Set.of("image/png", "image/jpeg", "image/svg+xml", "image/webp"),
            Set.of("png", "jpg", "jpeg", "svg", "webp"),
            "Formato logo non valido. Usa PNG, JPG, SVG o WebP.",
            bindingResult
        );
        validateFile(
            form.getVisuraFile(),
            "visuraFile",
            Set.of("application/pdf"),
            Set.of("pdf"),
            "La visura deve essere in PDF.",
            bindingResult
        );
        validateFile(
            form.getContractFile(),
            "contractFile",
            Set.of("application/pdf"),
            Set.of("pdf"),
            "Il contratto deve essere in PDF.",
            bindingResult
        );
    }

    private void validateFile(MultipartFile file,
                              String fieldName,
                              Set<String> allowedMimeTypes,
                              Set<String> allowedExtensions,
                              String message,
                              BindingResult bindingResult) {
        if (file == null || file.isEmpty()) {
            return;
        }
        String contentType = file.getContentType();
        boolean typeOk = contentType != null && allowedMimeTypes.contains(contentType.toLowerCase());
        boolean extOk = hasAllowedExtension(file.getOriginalFilename(), allowedExtensions);
        if (!typeOk && !extOk) {
            bindingResult.rejectValue(fieldName, "file.invalid", message);
        }
    }

    private boolean hasAllowedExtension(String filename, Set<String> allowedExtensions) {
        if (filename == null || filename.isBlank()) {
            return false;
        }
        int idx = filename.lastIndexOf('.');
        if (idx < 0 || idx == filename.length() - 1) {
            return false;
        }
        String ext = filename.substring(idx + 1).toLowerCase();
        return allowedExtensions.contains(ext);
    }

    @PostMapping(path = "/intake/section/confirm", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> confirmSection(@RequestBody ConfirmSectionRequest request,
                                                              Principal principal) {
        if (request == null || request.sectionKey == null || request.sectionKey.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "sectionKey required"));
        }
        if ("project".equals(request.sectionKey)) {
            String name = request.projectName == null ? "" : request.projectName.trim();
            if (name.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Inserisci il nome progetto prima di confermare."));
            }
            String kind = request.projectKind == null ? "" : request.projectKind.trim();
            if (kind.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Seleziona il tipo di progetto prima di confermare."));
            }
            UUID existing = intakeService.findProjectIdByName(name);
            if (existing != null && (request.projectId == null || !existing.equals(request.projectId))) {
                return ResponseEntity.badRequest().body(Map.of("error", "Esiste gia un progetto con questo nome. Usa \"Modifica scheda esistente\" per aggiornarlo."));
            }
        }
        try {
            UUID draftId = intakeService.getOrCreateDraft(request.draftId, request.projectId);
            intakeService.confirmSection(draftId, request.sectionKey, principal.getName(), request.projectKind);
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
            intakeService.unconfirmSection(request.draftId, request.sectionKey, principal.getName(), request.projectKind);
            List<String> confirmed = intakeService.loadConfirmedSections(request.draftId);
            return ResponseEntity.ok(Map.of("confirmedSections", confirmed));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping(path = "/intake/secret", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> revealSecret(@RequestBody SecretRequest request) {
        if (request == null || request.projectId == null || request.secretKey == null || request.secretKey.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "projectId e secretKey sono obbligatori."));
        }
        try {
            String value = intakeService.loadStoredSecret(request.projectId, request.secretKey);
            if (value == null || value.isBlank()) {
                return ResponseEntity.ok(Map.of("found", false, "message", "Nessuna credenziale salvata."));
            }
            return ResponseEntity.ok(Map.of("found", true, "value", value));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    public static class ConfirmSectionRequest {
        public UUID draftId;
        public UUID projectId;
        public String sectionKey;
        public String projectName;
        public String projectKind;
    }

    public static class SecretRequest {
        public UUID projectId;
        public String secretKey;
    }
}

