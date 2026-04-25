package com.verso.ai_client_form.service;

import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verso.ai_client_form.model.IntakeForm;
import com.verso.ai_client_form.repository.PipelineRepository;

@Service
public class PipelineService {

    private static final char DELIMITER = ';';

    private static final String H_PROJECT_NAME = "project_name";
    private static final String H_COMPANY_LEGAL_NAME = "company_legal_name";
    private static final String H_VAT_NUMBER = "vat_number";
    private static final String H_CONTACT_FULL_NAME = "contact_full_name";
    private static final String H_CONTACT_EMAIL = "contact_email";
    private static final String H_CONTACT_PHONE = "contact_phone";
    private static final String H_CRM_TEMPERATURE = "crm_temperature";
    private static final String H_CRM_NOTES = "crm_notes";
    private static final String H_CRM_SECTOR = "crm_sector";
    private static final String H_CRM_STAGE = "crm_current_stage";
    private static final String H_CRM_CTA = "crm_cta";

    private static final Set<String> REQUIRED_HEADERS = Set.of(H_PROJECT_NAME, H_COMPANY_LEGAL_NAME);
    private static final Set<String> ALLOWED_HEADERS = Set.of(
        H_PROJECT_NAME,
        H_COMPANY_LEGAL_NAME,
        H_VAT_NUMBER,
        H_CONTACT_FULL_NAME,
        H_CONTACT_EMAIL,
        H_CONTACT_PHONE,
        H_CRM_TEMPERATURE,
        H_CRM_NOTES,
        H_CRM_SECTOR,
        H_CRM_STAGE,
        H_CRM_CTA
    );

    private static final Map<String, String> HEADER_TO_INTAKE_FIELD = Map.ofEntries(
        Map.entry(H_PROJECT_NAME, "projectName"),
        Map.entry(H_COMPANY_LEGAL_NAME, "legalName"),
        Map.entry(H_VAT_NUMBER, "vatNumber"),
        Map.entry(H_CONTACT_FULL_NAME, "contactFullName"),
        Map.entry(H_CONTACT_EMAIL, "contactEmail"),
        Map.entry(H_CONTACT_PHONE, "contactPhone"),
        Map.entry(H_CRM_TEMPERATURE, "crmInterestTemperature"),
        Map.entry(H_CRM_NOTES, "crmNotes"),
        Map.entry(H_CRM_SECTOR, "crmSector"),
        Map.entry(H_CRM_STAGE, "crmCurrentStage"),
        Map.entry(H_CRM_CTA, "crmCta")
    );

    private static final Set<String> ALLOWED_TEMPERATURES = Set.of(
        "freddo", "tiepido", "caldo", "bollente", "disqualificato"
    );

    private final PipelineRepository repo;
    private final ObjectMapper objectMapper;

    public PipelineService(PipelineRepository repo, ObjectMapper objectMapper) {
        this.repo = repo;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void renamePipeline(UUID pipelineId, String newName) {
        if (pipelineId == null) {
            throw new IllegalArgumentException("Pipeline non valida.");
        }
        String safe = newName == null ? "" : newName.trim();
        if (safe.isBlank()) {
            throw new IllegalArgumentException("Inserisci un nome pipeline.");
        }
        int updated = repo.renameImport(pipelineId, safe);
        if (updated <= 0) {
            throw new IllegalArgumentException("Pipeline non trovata.");
        }
    }

    @Transactional
    public UUID importCsv(String pipelineName, MultipartFile file, String username) {
        String safeName = pipelineName == null ? "" : pipelineName.trim();
        if (safeName.isBlank()) {
            throw new IllegalArgumentException("Inserisci un nome pipeline.");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Carica un file CSV.");
        }

        String originalFilename = Optional.ofNullable(file.getOriginalFilename()).orElse("pipeline.csv");

        ParsedCsv parsed = parseCsv(file);
        List<RowParsed> rows = parsed.rows;
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Il CSV non contiene righe dati.");
        }

        // Duplicati dentro il CSV (case-insensitive)
        Set<String> seen = new HashSet<>();
        Set<String> duplicates = new HashSet<>();
        for (RowParsed row : rows) {
            String key = row.projectName.toLowerCase(Locale.ROOT);
            if (!seen.add(key)) {
                duplicates.add(row.projectName);
            }
        }
        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException("Import bloccato: nomi progetto duplicati nel CSV (" + String.join(", ", duplicates) + ").");
        }

        // Blocca se esiste gia un progetto con lo stesso nome
        List<String> lowered = rows.stream()
            .map(r -> r.projectName.toLowerCase(Locale.ROOT))
            .toList();
        if (repo.anyExistingProjectsByName(lowered)) {
            throw new IllegalArgumentException("Import bloccato: uno o piu nomi progetto esistono gia. Usa \"Modifica scheda esistente\" per aggiornarli.");
        }

        UUID importId = repo.createImport(safeName, originalFilename, username, rows.size());

        List<PipelineRepository.RowInsert> inserts = new ArrayList<>(rows.size());
        for (RowParsed row : rows) {
            inserts.add(new PipelineRepository.RowInsert(
                UUID.randomUUID(),
                row.rowNumber,
                row.projectName,
                row.companyName,
                row.contactFullName,
                row.contactEmail,
                row.contactPhone,
                row.crmTemperature,
                row.dataJson
            ));
        }
        repo.insertRows(importId, inserts);

        return importId;
    }

    @Transactional(readOnly = true)
    public IntakeForm buildPrefillFromRow(UUID rowId) {
        if (rowId == null) {
            return new IntakeForm();
        }
        String json = repo.findRowDataJson(rowId).orElse(null);
        IntakeForm prefill = new IntakeForm();
        if (json == null || json.isBlank()) {
            return prefill;
        }
        try {
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            applyMap(prefill, map);
        } catch (Exception ignored) {
        }
        return prefill;
    }

    @Transactional
    public void markRowDone(UUID rowId, UUID projectId) {
        if (rowId == null || projectId == null) {
            return;
        }
        repo.markRowDone(rowId, projectId);
    }

    @Transactional
    public void markDoneByProjectName(String projectName, UUID projectId) {
        repo.markRowsDoneByProjectName(projectName, projectId);
    }

    private ParsedCsv parseCsv(MultipartFile file) {
        try (CSVParser parser = CSVParser.parse(
            new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8),
            CSVFormat.DEFAULT.builder()
                .setDelimiter(DELIMITER)
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .build()
        )) {
            List<String> headerNames = parser.getHeaderNames();
            Map<String, String> normalizedToOriginal = new HashMap<>();
            Set<String> normalizedHeaders = new HashSet<>();
            for (String header : headerNames) {
                String normalized = normalizeHeader(header);
                if (normalized.isBlank()) {
                    continue;
                }
                normalizedToOriginal.put(normalized, header);
                normalizedHeaders.add(normalized);
            }

            List<String> missing = REQUIRED_HEADERS.stream().filter(h -> !normalizedHeaders.contains(h)).sorted().toList();
            if (!missing.isEmpty()) {
                throw new IllegalArgumentException("Header CSV mancante: " + String.join(", ", missing) + ".");
            }

            List<String> unknown = normalizedHeaders.stream().filter(h -> !ALLOWED_HEADERS.contains(h)).sorted().toList();
            if (!unknown.isEmpty()) {
                throw new IllegalArgumentException("Header CSV non riconosciuti: " + String.join(", ", unknown) + ".");
            }

            List<RowParsed> rows = new ArrayList<>();
            int rowNumber = 0;
            for (CSVRecord record : parser) {
                rowNumber++;
                Map<String, Object> data = new HashMap<>();

                String projectName = get(record, normalizedToOriginal, H_PROJECT_NAME);
                String companyName = get(record, normalizedToOriginal, H_COMPANY_LEGAL_NAME);
                if (projectName == null || projectName.isBlank()) {
                    throw new IllegalArgumentException("Riga " + rowNumber + ": project_name vuoto.");
                }
                if (companyName == null || companyName.isBlank()) {
                    throw new IllegalArgumentException("Riga " + rowNumber + ": company_legal_name vuoto.");
                }

                // Project + company
                putIfNotBlank(data, HEADER_TO_INTAKE_FIELD.get(H_PROJECT_NAME), projectName);
                putIfNotBlank(data, HEADER_TO_INTAKE_FIELD.get(H_COMPANY_LEGAL_NAME), companyName);

                String vat = get(record, normalizedToOriginal, H_VAT_NUMBER);
                putIfNotBlank(data, HEADER_TO_INTAKE_FIELD.get(H_VAT_NUMBER), vat);

                String contactFullName = get(record, normalizedToOriginal, H_CONTACT_FULL_NAME);
                putIfNotBlank(data, HEADER_TO_INTAKE_FIELD.get(H_CONTACT_FULL_NAME), contactFullName);

                String contactEmail = get(record, normalizedToOriginal, H_CONTACT_EMAIL);
                putIfNotBlank(data, HEADER_TO_INTAKE_FIELD.get(H_CONTACT_EMAIL), contactEmail);

                String contactPhone = get(record, normalizedToOriginal, H_CONTACT_PHONE);
                putIfNotBlank(data, HEADER_TO_INTAKE_FIELD.get(H_CONTACT_PHONE), contactPhone);

                String sector = get(record, normalizedToOriginal, H_CRM_SECTOR);
                putIfNotBlank(data, HEADER_TO_INTAKE_FIELD.get(H_CRM_SECTOR), sector);

                String notes = get(record, normalizedToOriginal, H_CRM_NOTES);
                putIfNotBlank(data, HEADER_TO_INTAKE_FIELD.get(H_CRM_NOTES), notes);

                String cta = get(record, normalizedToOriginal, H_CRM_CTA);
                putIfNotBlank(data, HEADER_TO_INTAKE_FIELD.get(H_CRM_CTA), cta);

                String stage = get(record, normalizedToOriginal, H_CRM_STAGE);
                putIfNotBlank(data, HEADER_TO_INTAKE_FIELD.get(H_CRM_STAGE), normalizeStage(stage));

                String temperature = normalizeTemperature(get(record, normalizedToOriginal, H_CRM_TEMPERATURE));
                if (temperature != null) {
                    data.put(HEADER_TO_INTAKE_FIELD.get(H_CRM_TEMPERATURE), temperature);
                }

                String json = objectMapper.writeValueAsString(data);
                rows.add(new RowParsed(
                    rowNumber,
                    projectName.trim(),
                    companyName.trim(),
                    emptyToNull(contactFullName),
                    emptyToNull(contactEmail),
                    emptyToNull(contactPhone),
                    temperature,
                    json
                ));
            }

            return new ParsedCsv(rows);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Errore nella lettura del CSV. Verifica separatore ';' e header.");
        }
    }

    private static String normalizeHeader(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        // Handle UTF-8 BOM that sometimes appears in the first CSV header column.
        if (!trimmed.isEmpty() && trimmed.charAt(0) == '\uFEFF') {
            trimmed = trimmed.substring(1);
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private static String get(CSVRecord record, Map<String, String> normalizedToOriginal, String normalizedHeader) {
        String original = normalizedToOriginal.get(normalizedHeader);
        if (original == null) {
            return null;
        }
        // When a row has fewer columns than the header, Commons CSV throws on record.get(headerName).
        // Treat missing columns as empty.
        if (!record.isMapped(original) || !record.isSet(original)) {
            return null;
        }
        String value = record.get(original);
        return value == null ? null : value.trim();
    }

    private static void putIfNotBlank(Map<String, Object> target, String field, String value) {
        if (field == null) {
            return;
        }
        if (value == null) {
            return;
        }
        String trimmed = value.trim();
        if (trimmed.isBlank()) {
            return;
        }
        target.put(field, trimmed);
    }

    private static String normalizeTemperature(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_TEMPERATURES.contains(normalized)) {
            throw new IllegalArgumentException("Valore crm_temperature non valido: " + value + ". Usa: freddo, tiepido, caldo, bollente, disqualificato.");
        }
        return normalized;
    }

    private static String normalizeStage(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "lead", "prospect", "cliente" -> normalized;
            default -> null;
        };
    }

    private static String emptyToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private static void applyMap(IntakeForm target, Map<String, Object> values) {
        if (target == null || values == null || values.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String fieldName = entry.getKey();
            Object raw = entry.getValue();
            if (fieldName == null || raw == null) {
                continue;
            }
            try {
                Field field = IntakeForm.class.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object converted = convert(raw, field.getType());
                if (converted != null) {
                    field.set(target, converted);
                }
            } catch (NoSuchFieldException ignored) {
            } catch (IllegalAccessException ignored) {
            }
        }
    }

    private static Object convert(Object raw, Class<?> type) {
        if (raw == null) {
            return null;
        }
        if (type.isAssignableFrom(raw.getClass())) {
            return raw;
        }
        String text = raw.toString().trim();
        if (text.isBlank()) {
            return null;
        }
        try {
            if (type == String.class) {
                return text;
            }
            if (type == Integer.class) {
                return Integer.valueOf(text);
            }
            if (type == BigDecimal.class) {
                return new BigDecimal(text);
            }
            if (type == Boolean.class) {
                String lowered = text.toLowerCase(Locale.ROOT);
                if (lowered.equals("true") || lowered.equals("1") || lowered.equals("si") || lowered.equals("s")) {
                    return Boolean.TRUE;
                }
                if (lowered.equals("false") || lowered.equals("0") || lowered.equals("no") || lowered.equals("n")) {
                    return Boolean.FALSE;
                }
                return null;
            }
            if (type == LocalDate.class) {
                return LocalDate.parse(text);
            }
            if (type == LocalDateTime.class) {
                return LocalDateTime.parse(text);
            }
            if (type == UUID.class) {
                return UUID.fromString(text);
            }
            if (List.class.isAssignableFrom(type) && raw instanceof List) {
                return raw;
            }
            if (List.class.isAssignableFrom(type) && raw instanceof String) {
                return List.of(text);
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private record ParsedCsv(List<RowParsed> rows) {}

    private record RowParsed(
        int rowNumber,
        String projectName,
        String companyName,
        String contactFullName,
        String contactEmail,
        String contactPhone,
        String crmTemperature,
        String dataJson
    ) {}
}
