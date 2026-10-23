ALTER TABLE accounts
    ADD COLUMN IF NOT EXISTS registration_ip_hash BYTEA NULL;

-- Best-effort backfill from existing audit history; rows with no matching
-- event (audit disabled, or purged by retention) stay NULL and no longer
-- count toward security.registration.max-accounts-per-ip until re-registered.
UPDATE accounts a
SET registration_ip_hash = e.ip_hash
FROM (
    SELECT DISTINCT ON (account_id) account_id, ip_hash
    FROM security_events
    WHERE event_type = 'REGISTER_SUCCESS' AND ip_hash IS NOT NULL
    ORDER BY account_id, created_at ASC
) e
WHERE a.id = e.account_id AND a.registration_ip_hash IS NULL;

CREATE INDEX IF NOT EXISTS idx_accounts_registration_ip_hash ON accounts(registration_ip_hash);
