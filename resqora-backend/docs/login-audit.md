# Login logs

Every login (`POST /api/auth/login`) and email verify (`POST /api/auth/verify-email`, since that also gives a JWT) writes one log line under the `resqora.audit.login` logger. It just goes to the normal console / Render logs, there's no table for it.

What's in a line: success or fail, time in IST, user id, email, IP and a reason if it failed. Anything we don't know is `-`. Successes are INFO, failures are WARN.

Failure reasons: BAD_CREDENTIALS, USER_NOT_FOUND, EMAIL_NOT_VERIFIED, RATE_LIMITED, INVALID_CODE, EMAIL_ALREADY_VERIFIED, VALIDATION, AUTH_REJECTED, INTERNAL_ERROR.

Some things to remember:
- passwords, codes and tokens never go in the log
- weird characters in the email are swapped for `_` so nobody can fake extra log lines
- if logging itself breaks it's ignored, the login response stays the same
- IP comes from ClientIpResolver (CF-Connecting-IP / True-Client-IP from the proxy). set `RESQORA_TRUST_PROXY_IP_HEADERS=false` to turn that off
- the logs have emails and IPs in them so don't share Render log access around
- keep `RESQORA_LOG_EMAILS` false in prod, that one prints codes (the audit log doesn't)
