package com.verso.ai_client_form.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PipelineImportSummary(
    UUID pipelineId,
    String pipelineName,
    String originalFilename,
    int rowCount,
    int activeRowCount,
    int doneCount,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}

