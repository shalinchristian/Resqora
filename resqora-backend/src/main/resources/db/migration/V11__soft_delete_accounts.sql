-- Deleting an account now keeps the account row as a record (name, email, sign-up time) and marks it
-- DELETED; the user's resumes, analyses, summaries and codes are still removed by the app.

-- When the account was created. Older accounts get the time of their first upload when there is one;
-- accounts with no uploads stay NULL because their real sign-up time was never recorded.
ALTER TABLE users ADD COLUMN created_at TIMESTAMP WITH TIME ZONE;
UPDATE users SET created_at = (SELECT MIN(r.created_at) FROM resumes r WHERE r.user_id = users.id);
ALTER TABLE users ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP;

-- ACTIVE or DELETED, and when it was deleted (NULL while active)
ALTER TABLE users ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE users ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;

-- One live account per email, while deleted accounts keep their email and the same email can sign up
-- again. active_email holds the email while the account is active and is set to NULL on delete; a
-- UNIQUE column allows any number of NULLs, so only active accounts compete for an email.
-- (A partial unique index would also work on PostgreSQL, but H2, used in tests, does not support one.)
ALTER TABLE users ADD COLUMN active_email VARCHAR(255);
UPDATE users SET active_email = email;
ALTER TABLE users ADD CONSTRAINT uq_users_active_email UNIQUE (active_email);
-- users_email_key is PostgreSQL's name for the old UNIQUE(email) from V1
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_email_key;
CREATE INDEX idx_users_email ON users (email);
