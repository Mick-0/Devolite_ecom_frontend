package com.verso.ai_client_form.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ClientCompletionSummary(
    UUID projectId,
    String projectName,
    String companyName,
    String projectKind,
    String projectStatus,
    String vatNumber,
    String city,
    String contactName,
    String contactEmail,
    OffsetDateTime updatedAt,
    int progressPercent,
    int completedFields,
    int totalFields
) {}
