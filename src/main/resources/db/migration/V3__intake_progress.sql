CREATE SCHEMA IF NOT EXISTS intake;

CREATE TABLE IF NOT EXISTS intake.draft (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID REFERENCES core.web_project(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS intake.section_status (
    draft_id UUID NOT NULL REFERENCES intake.draft(id) ON DELETE CASCADE,
    section_key TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('pending', 'confirmed')),
    confirmed_by_user_id UUID REFERENCES core.staff_user(id) ON DELETE SET NULL,
    confirmed_at TIMESTAMPTZ,
    PRIMARY KEY (draft_id, section_key)
);

CREATE INDEX IF NOT EXISTS ix_section_status_draft ON intake.section_status(draft_id);

DROP TRIGGER IF EXISTS trg_intake_draft_updated_at ON intake.draft;
CREATE TRIGGER trg_intake_draft_updated_at
    BEFORE UPDATE ON intake.draft
    FOR EACH ROW EXECUTE FUNCTION core.set_updated_at();
