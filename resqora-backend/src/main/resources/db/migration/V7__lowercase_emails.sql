-- Slice 12: emails are case-insensitive. New writes are trimmed + lower-cased in the app;
-- this lower-cases existing rows. Rows whose lower-case form already belongs to another
-- account are left untouched (they would break the UNIQUE(email) constraint) - see CHANGES.md
-- for the query to find and merge them by hand. With every write normalised, the existing
-- UNIQUE(email) constraint enforces case-insensitive uniqueness (H2 has no lower() index).
UPDATE users
SET email = LOWER(TRIM(email))
WHERE email <> LOWER(TRIM(email))
  AND NOT EXISTS (
      SELECT 1 FROM users other
      WHERE other.id <> users.id
        AND LOWER(TRIM(other.email)) = LOWER(TRIM(users.email)));
