CREATE SCHEMA IF NOT EXISTS pipeline;

CREATE TABLE IF NOT EXISTS pipeline.pipeline_import (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pipeline_name TEXT NOT NULL,
    original_filename TEXT NOT NULL,
    delimiter CHAR(1) NOT NULL DEFAULT ';',
    uploaded_by_username TEXT NOT NULL,
    row_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

DROP TRIGGER IF EXISTS trg_pipeline_import_updated_at ON pipeline.pipeline_import;
CREATE TRIGGER trg_pipeline_import_updated_at
    BEFORE UPDATE ON pipeline.pipeline_import
    FOR EACH ROW EXECUTE FUNCTION core.set_updated_at();

CREATE TABLE IF NOT EXISTS pipeline.pipeline_row (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    import_id UUID NOT NULL REFERENCES pipeline.pipeline_import(id) ON DELETE CASCADE,
    row_number INTEGER NOT NULL,
    project_name TEXT NOT NULL,
    company_name TEXT,
    contact_full_name TEXT,
    contact_email TEXT,
    contact_phone TEXT,
    crm_temperature TEXT,
    data_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    is_done BOOLEAN NOT NULL DEFAULT FALSE,
    done_project_id UUID REFERENCES core.web_project(id),
    done_at TIMESTAMPTZ,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pipeline_row_number_check CHECK (row_number > 0),
    CONSTRAINT pipeline_row_project_name_check CHECK (length(btrim(project_name)) > 0)
);

DROP TRIGGER IF EXISTS trg_pipeline_row_updated_at ON pipeline.pipeline_row;
CREATE TRIGGER trg_pipeline_row_updated_at
    BEFORE UPDATE ON pipeline.pipeline_row
    FOR EACH ROW EXECUTE FUNCTION core.set_updated_at();

CREATE UNIQUE INDEX IF NOT EXISTS ux_pipeline_row_import_project
    ON pipeline.pipeline_row (import_id, lower(project_name))
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS ix_pipeline_row_import ON pipeline.pipeline_row(import_id);
CREATE INDEX IF NOT EXISTS ix_pipeline_row_project_name ON pipeline.pipeline_row(lower(project_name));
CREATE INDEX IF NOT EXISTS ix_pipeline_row_company_name ON pipeline.pipeline_row(lower(company_name));
CREATE INDEX IF NOT EXISTS ix_pipeline_row_contact_email ON pipeline.pipeline_row(lower(contact_email));
CREATE INDEX IF NOT EXISTS ix_pipeline_row_done ON pipeline.pipeline_row(is_done) WHERE is_deleted = FALSE;

