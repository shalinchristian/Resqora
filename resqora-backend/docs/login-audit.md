# Login audit logs

`POST /api/auth/login` and `POST /api/auth/verify-email` (which issues a JWT) each emit one
`resqora.audit.login` SLF4J event to the normal console / Render log stream. There are no
OAuth/Google login paths in this backend. Registration and password reset do not issue tokens.
A servlet filter wraps MVC/security, including body validation, malformed JSON, throttling,
and unexpected failures; the service enriches the event with known user IDs and reasons.
Success is recorded only after the request finishes successfully, including transaction commit.

Fields: outcome, explicit Asia/Kolkata ISO-8601 time (+05:30, independent of server TZ), userId,
normalized email, resolved IP, and failure reason. Unknown values are `-`. Success is INFO;
failure is WARN. Email is capped at 254 code points; IP at 64. Controls, Unicode line/format
separators, whitespace and key/value delimiter characters become underscores. No body,
password, verification code, token, or exception text is retained or emitted by the audit.
Logging exceptions are suppressed and cannot change the HTTP result. Existing HTTP responses
are unchanged; internal reasons are never returned to clients.

Reasons: BAD_CREDENTIALS, USER_NOT_FOUND, EMAIL_NOT_VERIFIED, RATE_LIMITED, INVALID_CODE,
EMAIL_ALREADY_VERIFIED, VALIDATION, AUTH_REJECTED (rejected before the controller), INTERNAL_ERROR.
Deleted accounts are filtered out by the existing active-user lookup and remain USER_NOT_FOUND;
no additional query is made to distinguish deleted accounts or enrich a pre-lookup rate limit.
Invalid/missing/expired/exhausted codes all use INVALID_CODE, as the code service does not distinguish them.
Malformed JSON has no trusted typed email and is logged as email=-.

The existing ClientIpResolver is reused: CF-Connecting-IP / True-Client-IP only from private
proxy addresses, never X-Forwarded-For. RESQORA_TRUST_PROXY_IP_HEADERS=false disables this trust.
Use that setting when a private network permits untrusted direct clients to reach the app.

These are app logs, not a database login-history table. No migration or history API is added.
A database history table (with retention, access control and privacy policy) is a separate decision.
Logs contain personal data (email/IP); restrict Render log access and choose appropriate retention.
Keep the audit logger at INFO or lower to retain successes. RESQORA_LOG_EMAILS is an existing
local-only email debugging feature and must remain false in production; its separate email-sender
logger may print codes, but the audit logger never does.
