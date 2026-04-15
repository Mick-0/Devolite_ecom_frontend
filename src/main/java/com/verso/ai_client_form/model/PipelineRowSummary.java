package com.verso.ai_client_form.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PipelineRowSummary(
    UUID rowId,
    int rowNumber,
    String projectName,
    String companyName,
    String contactFullName,
    String contactEmail,
    String contactPhone,
    String crmTemperature,
    boolean done,
    UUID doneProjectId,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
