package com.verso.ai_client_form.controller;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import com.verso.ai_client_form.model.PipelineImportSummary;
import com.verso.ai_client_form.model.PipelineRowSummary;
import com.verso.ai_client_form.repository.PipelineRepository;
import com.verso.ai_client_form.service.PipelineService;

@Controller
public class PipelineController {

    private final PipelineRepository repo;
    private final PipelineService pipelineService;

    public PipelineController(PipelineRepository repo, PipelineService pipelineService) {
        this.repo = repo;
        this.pipelineService = pipelineService;
    }

    @GetMapping("/pipelines/{pipelineId}")
    public String showPipeline(@PathVariable("pipelineId") UUID pipelineId, Model model) {
        PipelineImportSummary summary = repo.findImport(pipelineId).orElse(null);
        if (summary == null) {
            model.addAttribute("message", "Pipeline non trovata.");
            return "error";
        }
        model.addAttribute("pipeline", summary);
        return "pipeline";
    }

    @PostMapping(path = "/pipelines/{pipelineId}/rename", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> rename(@PathVariable("pipelineId") UUID pipelineId,
                                                      @RequestBody Map<String, Object> body) {
        Object raw = body == null ? null : body.get("pipelineName");
        String name = raw == null ? null : raw.toString();
        try {
            pipelineService.renamePipeline(pipelineId, name);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of("ok", false, "error", "Errore durante la rinomina."));
        }
    }

    @GetMapping(path = "/pipelines/list", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public List<PipelineImportSummary> listPipelines(@RequestParam(value = "limit", required = false) Integer limit,
                                                     @RequestParam(value = "q", required = false) String query,
                                                     @RequestParam(value = "sortBy", required = false) String sortBy,
                                                     @RequestParam(value = "sortDir", required = false) String sortDir) {
        int capped = (limit == null) ? 100 : Math.min(Math.max(limit, 1), 200);
        return repo.listImports(capped, query, sortBy, sortDir);
    }

    @PostMapping(path = "/pipelines/upload", consumes = "multipart/form-data", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> uploadPipeline(@RequestParam("pipelineName") String pipelineName,
                                                              @RequestParam("csvFile") MultipartFile csvFile,
                                                              Principal principal) {
        try {
            UUID id = pipelineService.importCsv(pipelineName, csvFile, principal.getName());
            return ResponseEntity.ok(Map.of("ok", true, "pipelineId", id));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of("ok", false, "error", "Errore durante l'import.")); 
        }
    }

    @GetMapping(path = "/pipelines/{pipelineId}/rows", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public List<PipelineRowSummary> listRows(@PathVariable("pipelineId") UUID pipelineId,
                                             @RequestParam(value = "limit", required = false) Integer limit,
                                             @RequestParam(value = "q", required = false) String query,
                                             @RequestParam(value = "sortBy", required = false) String sortBy,
                                             @RequestParam(value = "sortDir", required = false) String sortDir) {
        int capped = (limit == null) ? 300 : Math.min(Math.max(limit, 1), 500);
        return repo.listRows(pipelineId, capped, query, sortBy, sortDir);
    }

    @GetMapping(path = "/pipelines/{pipelineId}/rows/table", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> listRowsTable(@PathVariable("pipelineId") UUID pipelineId,
                                             @RequestParam(value = "limit", required = false) Integer limit,
                                             @RequestParam(value = "q", required = false) String query,
                                             @RequestParam(value = "sortBy", required = false) String sortBy,
                                             @RequestParam(value = "sortDir", required = false) String sortDir) {
        int capped = (limit == null) ? 300 : Math.min(Math.max(limit, 1), 500);
        return pipelineService.listTableRows(pipelineId, capped, query, sortBy, sortDir);
    }

    @PostMapping(path = "/pipelines/rows/{rowId}/table", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateRowTable(@PathVariable("rowId") UUID rowId,
                                                              @RequestBody Map<String, Object> body) {
        Object rawValues = body == null ? null : body.get("values");
        Map<String, Object> values = new LinkedHashMap<>();
        if (rawValues instanceof Map<?, ?> map) {
            map.forEach((key, value) -> values.put(String.valueOf(key), value));
        }
        try {
            pipelineService.updateTableRow(rowId, values);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of("ok", false, "error", "Errore durante il salvataggio riga."));
        }
    }

    @PostMapping(path = "/pipelines/rows/{rowId}/delete", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteRow(@PathVariable("rowId") UUID rowId) {
        int updated = repo.softDeleteRow(rowId);
        return ResponseEntity.ok(Map.of("ok", true, "updated", updated));
    }
}
