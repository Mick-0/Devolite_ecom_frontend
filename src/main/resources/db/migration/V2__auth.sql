CREATE SCHEMA IF NOT EXISTS auth;

CREATE TABLE IF NOT EXISTS auth.users (
    username VARCHAR(100) PRIMARY KEY,
    password VARCHAR(200) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS auth.authorities (
    username VARCHAR(100) NOT NULL,
    authority VARCHAR(50) NOT NULL,
    CONSTRAINT fk_auth_user FOREIGN KEY (username)
        REFERENCES auth.users (username) ON DELETE CASCADE
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_auth_authorities
    ON auth.authorities (username, authority);

CREATE TABLE IF NOT EXISTS auth.user_profile (
    username VARCHAR(100) PRIMARY KEY,
    staff_user_id UUID REFERENCES core.staff_user(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
