-- Domain section: collect precise info for existing/new domains and common blockers.

ALTER TABLE dominio.domain_setup
    ADD COLUMN IF NOT EXISTS existing_registrar TEXT,
    ADD COLUMN IF NOT EXISTS existing_dns_provider TEXT,
    ADD COLUMN IF NOT EXISTS existing_has_credentials BOOLEAN,
    ADD COLUMN IF NOT EXISTS existing_credential_username TEXT,
    ADD COLUMN IF NOT EXISTS existing_credential_email TEXT,
    ADD COLUMN IF NOT EXISTS existing_credential_secret TEXT,
    ADD COLUMN IF NOT EXISTS existing_two_factor_enabled BOOLEAN,
    ADD COLUMN IF NOT EXISTS existing_nameservers TEXT,
    ADD COLUMN IF NOT EXISTS existing_expiry_date DATE,
    ADD COLUMN IF NOT EXISTS existing_transfer_locked BOOLEAN,
    ADD COLUMN IF NOT EXISTS domain_issues TEXT,
    ADD COLUMN IF NOT EXISTS domain_problem_severity INTEGER CHECK (domain_problem_severity BETWEEN 0 AND 4),
    ADD COLUMN IF NOT EXISTS willing_to_register_new_domain BOOLEAN,
    ADD COLUMN IF NOT EXISTS new_registrar TEXT,
    ADD COLUMN IF NOT EXISTS new_credential_username TEXT,
    ADD COLUMN IF NOT EXISTS new_credential_email TEXT,
    ADD COLUMN IF NOT EXISTS new_credential_secret TEXT,
    ADD COLUMN IF NOT EXISTS reachability_checked_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS reachability_status TEXT,
    ADD COLUMN IF NOT EXISTS reachability_details TEXT;

