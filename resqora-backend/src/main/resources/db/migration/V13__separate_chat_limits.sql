-- Resume chat and dashboard chat now have separate daily limits, so ai_usage records which chat a
-- message came from. Old CHAT rows: Groq only ever answered the dashboard chat, so those are
-- DASHBOARD_CHAT; Gemini chat rows can't be told apart and count as RESUME_CHAT.
UPDATE ai_usage SET kind = 'DASHBOARD_CHAT' WHERE kind = 'CHAT' AND provider = 'GROQ';
UPDATE ai_usage SET kind = 'RESUME_CHAT' WHERE kind = 'CHAT';
ALTER TABLE ai_usage ALTER COLUMN kind SET DEFAULT 'RESUME_CHAT';
-- the per-user limit check counts today's rows of one kind
CREATE INDEX idx_ai_usage_user_kind_created ON ai_usage (user_id, kind, created_at);
