-- Slice 15: keep the original upload for the preview, save the AI summary,
-- and drop the per-button AI answer cache (chat replaces the buttons; chat is never cached).
-- Deleting a resume row (or an account, which deletes its resumes) removes the file bytes with it.
ALTER TABLE resumes ADD COLUMN file_data BYTEA;
ALTER TABLE resumes ADD COLUMN content_type VARCHAR(100);
ALTER TABLE resumes ADD COLUMN ai_summary TEXT;

-- ai_usage (daily limit counting) is a separate table and stays.
DROP TABLE ai_responses;
