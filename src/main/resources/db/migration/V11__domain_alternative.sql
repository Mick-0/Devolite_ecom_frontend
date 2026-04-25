ALTER TABLE dominio.domain_setup
    ADD COLUMN IF NOT EXISTS alternative_domain_to_register TEXT;

