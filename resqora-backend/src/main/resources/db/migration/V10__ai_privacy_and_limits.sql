-- AI privacy setting: when FALSE the app never sends this user's data to an AI provider.
-- Existing and new accounts start with AI on, matching how the app behaved before.
ALTER TABLE users ADD COLUMN ai_enabled BOOLEAN NOT NULL DEFAULT TRUE;

-- What each AI call was (CHAT or SUMMARY) and which provider answered it (GEMINI or GROQ).
-- The per-user daily limit counts CHAT rows; the app-wide caps count rows per provider.
-- Older rows were all Gemini calls and are counted as chat.
ALTER TABLE ai_usage ADD COLUMN kind VARCHAR(20) NOT NULL DEFAULT 'CHAT';
ALTER TABLE ai_usage ADD COLUMN provider VARCHAR(20) NOT NULL DEFAULT 'GEMINI';
-- the limit checks count today's rows per user and for the whole app on every AI call
CREATE INDEX idx_ai_usage_user_created ON ai_usage (user_id, created_at);
CREATE INDEX idx_ai_usage_created ON ai_usage (created_at);

-- Which version of the rule-based analyzer produced a saved analysis. Rows from an older
-- version are recomputed the next time they are read, so every resume shows current findings.
ALTER TABLE resume_analyses ADD COLUMN analyzer_version INT NOT NULL DEFAULT 1;
