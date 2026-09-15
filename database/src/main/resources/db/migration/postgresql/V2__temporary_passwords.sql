ALTER TABLE accounts
    ADD COLUMN IF NOT EXISTS password_change_required BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE accounts
    ADD COLUMN IF NOT EXISTS temporary_password_expires_at TIMESTAMPTZ NULL;
