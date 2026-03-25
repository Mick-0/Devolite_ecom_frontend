CREATE UNIQUE INDEX IF NOT EXISTS ux_web_project_name_ci
    ON core.web_project (lower(project_name));
