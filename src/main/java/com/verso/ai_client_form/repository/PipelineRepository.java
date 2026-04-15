package com.verso.ai_client_form.repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import com.verso.ai_client_form.model.PipelineImportSummary;
import com.verso.ai_client_form.model.PipelineRowSummary;

@Repository
public class PipelineRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public PipelineRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UUID createImport(String pipelineName, String originalFilename, String username, int rowCount) {
        UUID id = UUID.randomUUID();
        String sql = """
            insert into pipeline.pipeline_import
                (id, pipeline_name, original_filename, delimiter, uploaded_by_username, row_count)
            values
                (:id, :pipeline_name, :original_filename, ';', :uploaded_by_username, :row_count)
            """;
        jdbc.update(sql, new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("pipeline_name", pipelineName)
            .addValue("original_filename", originalFilename)
            .addValue("uploaded_by_username", username)
            .addValue("row_count", rowCount)
        );
        return id;
    }

    public void insertRows(UUID importId, List<RowInsert> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        String sql = """
            insert into pipeline.pipeline_row
                (id, import_id, row_number, project_name, company_name,
                 contact_full_name, contact_email, contact_phone, crm_temperature, data_json)
            values
                (:id, :import_id, :row_number, :project_name, :company_name,
                 :contact_full_name, :contact_email, :contact_phone, :crm_temperature, cast(:data_json as jsonb))
            """;
        List<MapSqlParameterSource> batch = new ArrayList<>(rows.size());
        for (RowInsert row : rows) {
            batch.add(new MapSqlParameterSource()
                .addValue("id", row.id())
                .addValue("import_id", importId)
                .addValue("row_number", row.rowNumber())
                .addValue("project_name", row.projectName())
                .addValue("company_name", row.companyName())
                .addValue("contact_full_name", row.contactFullName())
                .addValue("contact_email", row.contactEmail())
                .addValue("contact_phone", row.contactPhone())
                .addValue("crm_temperature", row.crmTemperature())
                .addValue("data_json", row.dataJson())
            );
        }
        jdbc.batchUpdate(sql, batch.toArray(MapSqlParameterSource[]::new));
    }

    public List<PipelineImportSummary> listImports(int limit, String query, String sortBy, String sortDir) {
        int capped = Math.max(1, Math.min(limit, 200));
        String direction = "asc".equalsIgnoreCase(sortDir) ? "asc" : "desc";
        String orderExpression = importSortExpression(sortBy);
        String sql = """
            select i.id as pipeline_id,
                   i.pipeline_name,
                   i.original_filename,
                   i.row_count,
                   coalesce(sum(case when r.is_deleted = false then 1 else 0 end), 0) as active_row_count,
                   coalesce(sum(case when r.is_deleted = false and r.is_done = true then 1 else 0 end), 0) as done_count,
                   i.created_at,
                   i.updated_at
            from pipeline.pipeline_import i
            left join pipeline.pipeline_row r on r.import_id = i.id
            where (
                coalesce(:query_like, '') = ''
                or i.pipeline_name ilike :query_like
                or i.original_filename ilike :query_like
            )
            group by i.id
            order by %s %s nulls last, i.created_at desc
            limit :limit
            """.formatted(orderExpression, direction);

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("limit", capped)
            .addValue("query_like", hasText(query) ? "%" + query.trim() + "%" : null);

        return jdbc.query(sql, params, (rs, i) -> new PipelineImportSummary(
            rs.getObject("pipeline_id", UUID.class),
            rs.getString("pipeline_name"),
            rs.getString("original_filename"),
            rs.getInt("row_count"),
            rs.getInt("active_row_count"),
            rs.getInt("done_count"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class)
        ));
    }

    public Optional<PipelineImportSummary> findImport(UUID importId) {
        String sql = """
            select i.id as pipeline_id,
                   i.pipeline_name,
                   i.original_filename,
                   i.row_count,
                   coalesce(sum(case when r.is_deleted = false then 1 else 0 end), 0) as active_row_count,
                   coalesce(sum(case when r.is_deleted = false and r.is_done = true then 1 else 0 end), 0) as done_count,
                   i.created_at,
                   i.updated_at
            from pipeline.pipeline_import i
            left join pipeline.pipeline_row r on r.import_id = i.id
            where i.id = :id
            group by i.id
            """;
        return jdbc.query(sql, new MapSqlParameterSource("id", importId), rs -> {
            if (!rs.next()) {
                return Optional.empty();
            }
            return Optional.of(new PipelineImportSummary(
                rs.getObject("pipeline_id", UUID.class),
                rs.getString("pipeline_name"),
                rs.getString("original_filename"),
                rs.getInt("row_count"),
                rs.getInt("active_row_count"),
                rs.getInt("done_count"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)
            ));
        });
    }

    public List<PipelineRowSummary> listRows(UUID importId, int limit, String query, String sortBy, String sortDir) {
        int capped = Math.max(1, Math.min(limit, 500));
        String direction = "asc".equalsIgnoreCase(sortDir) ? "asc" : "desc";
        String orderExpression = rowSortExpression(sortBy);
        String sql = """
            select r.id as row_id,
                   r.row_number,
                   r.project_name,
                   r.company_name,
                   r.contact_full_name,
                   r.contact_email,
                   r.contact_phone,
                   r.crm_temperature,
                   r.is_done,
                   r.done_project_id,
                   r.created_at,
                   r.updated_at
            from pipeline.pipeline_row r
            where r.import_id = :id
              and r.is_deleted = false
              and (
                coalesce(:query_like, '') = ''
                or r.project_name ilike :query_like
                or coalesce(r.company_name, '') ilike :query_like
                or coalesce(r.contact_full_name, '') ilike :query_like
                or coalesce(r.contact_email, '') ilike :query_like
                or coalesce(r.contact_phone, '') ilike :query_like
              )
            order by %s %s nulls last, r.row_number asc
            limit :limit
            """.formatted(orderExpression, direction);
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("id", importId)
            .addValue("limit", capped)
            .addValue("query_like", hasText(query) ? "%" + query.trim() + "%" : null);

        return jdbc.query(sql, params, (rs, i) -> new PipelineRowSummary(
            rs.getObject("row_id", UUID.class),
            rs.getInt("row_number"),
            rs.getString("project_name"),
            rs.getString("company_name"),
            rs.getString("contact_full_name"),
            rs.getString("contact_email"),
            rs.getString("contact_phone"),
            rs.getString("crm_temperature"),
            rs.getBoolean("is_done"),
            rs.getObject("done_project_id", UUID.class),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class)
        ));
    }

    public Optional<String> findRowDataJson(UUID rowId) {
        String sql = """
            select data_json::text as data_json
            from pipeline.pipeline_row
            where id = :id and is_deleted = false
            """;
        return jdbc.query(sql, new MapSqlParameterSource("id", rowId), rs ->
            rs.next() ? Optional.ofNullable(rs.getString("data_json")) : Optional.empty()
        );
    }

    public int softDeleteRow(UUID rowId) {
        String sql = """
            update pipeline.pipeline_row
            set is_deleted = true, deleted_at = now()
            where id = :id and is_deleted = false
            """;
        return jdbc.update(sql, new MapSqlParameterSource("id", rowId));
    }

    public int markRowDone(UUID rowId, UUID projectId) {
        String sql = """
            update pipeline.pipeline_row
            set is_done = true,
                done_project_id = :project_id,
                done_at = now()
            where id = :id and is_deleted = false
            """;
        return jdbc.update(sql, new MapSqlParameterSource()
            .addValue("id", rowId)
            .addValue("project_id", projectId)
        );
    }

    public int markRowsDoneByProjectName(String projectName, UUID projectId) {
        if (!hasText(projectName)) {
            return 0;
        }
        String sql = """
            update pipeline.pipeline_row
            set is_done = true,
                done_project_id = :project_id,
                done_at = now()
            where is_deleted = false
              and is_done = false
              and lower(project_name) = lower(:project_name)
            """;
        return jdbc.update(sql, new MapSqlParameterSource()
            .addValue("project_id", projectId)
            .addValue("project_name", projectName.trim())
        );
    }

    public boolean anyExistingProjectsByName(List<String> projectNames) {
        if (projectNames == null || projectNames.isEmpty()) {
            return false;
        }
        String sql = """
            select 1
            from core.web_project
            where lower(project_name) in (:names)
            limit 1
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("names", projectNames);
        return Boolean.TRUE.equals(jdbc.query(sql, params,
            (org.springframework.jdbc.core.ResultSetExtractor<Boolean>) rs -> rs.next()
        ));
    }

    public Optional<Map<String, Object>> findPipelineRowMeta(UUID rowId) {
        String sql = """
            select id, project_name, company_name, contact_full_name, contact_email, contact_phone, crm_temperature
            from pipeline.pipeline_row
            where id = :id and is_deleted = false
            """;
        return queryForMapOptional(sql, Map.of("id", rowId));
    }

    private Optional<Map<String, Object>> queryForMapOptional(String sql, Map<String, ?> params) {
        List<Map<String, Object>> rows = jdbc.query(sql, new MapSqlParameterSource(params), new ColumnMapRowMapper());
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String importSortExpression(String sortBy) {
        if (sortBy == null) {
            return "i.created_at";
        }
        return switch (sortBy) {
            case "pipelineName" -> "i.pipeline_name";
            case "rowCount" -> "i.row_count";
            case "doneCount" -> "done_count";
            case "activeRowCount" -> "active_row_count";
            case "updatedAt" -> "i.updated_at";
            case "createdAt" -> "i.created_at";
            default -> "i.created_at";
        };
    }

    private static String rowSortExpression(String sortBy) {
        if (sortBy == null) {
            return "r.row_number";
        }
        return switch (sortBy) {
            case "projectName" -> "r.project_name";
            case "companyName" -> "r.company_name";
            case "contactEmail" -> "r.contact_email";
            case "crmTemperature" -> "r.crm_temperature";
            case "done" -> "r.is_done";
            case "updatedAt" -> "r.updated_at";
            case "createdAt" -> "r.created_at";
            case "rowNumber" -> "r.row_number";
            default -> "r.row_number";
        };
    }

    public record RowInsert(
        UUID id,
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
