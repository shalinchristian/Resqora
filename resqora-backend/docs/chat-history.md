# Server-side chat history

JWT authentication is required for every history route. Only the logged-in user's data is returned.

| Method | Dashboard | One resume | Response |
| --- | --- | --- | --- |
| GET | `/api/chat/history` | `/api/resumes/{resumeId}/chat/history` | 200, JSON below |
| DELETE | `/api/chat/history` | `/api/resumes/{resumeId}/chat/history` | 204, empty body |

```json
{"messages":[{"role":"user","content":"Question","createdAt":"2026-11-11T17:30:00+05:30"},{"role":"assistant","content":"Answer","createdAt":"2026-11-11T17:30:00+05:30"}]}
```

An empty history is `{"messages":[]}`. Roles are exactly `user` and `assistant`;
the text field is **content**, not `text`. Timestamps are ISO-8601 with an offset
(app timezone, default Asia/Kolkata). Responses contain at most 20 messages,
oldest first; equal timestamps are ordered by generated id. Both user and AI
turns count toward the 20, so this normally retains the last 10 complete exchanges.

A missing, deleted, or someone else's resume returns the same 404 as other resume
routes, for both GET and DELETE. DELETE is idempotent for an owned resume or the
dashboard and does not affect any other scope/user. GET and DELETE work when AI
is disabled or the daily limit is exhausted; they never call an AI or change
`ai_usage`. Existing CORS configuration already permits DELETE for `/api/**`.

After a successful, nonblank AI response, the new user/assistant pair is saved
and the oldest excess messages are removed in **one transaction**. A database
user-row lock serialises concurrent pair saves/clears across application servers.
No lock is held during the AI network call. AI failures (including both dashboard
providers failing or blank replies), validation failures and limit rejections
save **neither** turn. Summary calls are not chat history. Resumes cascade their
history on deletion (FK); account soft-delete explicitly removes all the user's
history, including the dashboard, but keeps usage records as before. The same
user lock prevents a reply arriving during account deletion from restoring history.

Clear applies to replies already saved when its lock is acquired. An AI request
still in flight can save a new pair afterwards; Clear does not cancel requests.
If the resume/account is deleted during an AI call, the final ownership/active
account check rejects the save rather than recreating history.

## Conversation context: intentionally unchanged

Both POST endpoints already accept browser-supplied `history`, using request
turns `{role: "user" | "model", text: "..."}` (up to six turns, 500 characters
each). The existing ChatPanel sends its last six messages. The prompt includes
this transcript as delimited, untrusted EARLIER_TURNS data. Persistence does not
automatically feed stored history into the model or change prompts. A frontend
loading the new response must map `assistant` to `model` and `content` to `text`
when constructing the existing POST request. Stored history can be displayed in
full while still sending only the existing six-turn/500-character context window.

Migration: `V14__chat_history.sql` (after numerically sorted V13). Uses PostgreSQL
TIMESTAMP WITH TIME ZONE, cascading user/resume foreign keys, scope/role checks,
and `(user_id, scope, resume_id, created_at, id)` index.

## Verification

`JAVA_HOME=/workspace/jdk/jdk-21.0.13+9 ./mvnw test` runs the full suite, including
15 chat-history integration cases with mocked AI. To run these same cases against
an existing **isolated test database** on PostgreSQL 17 instead of H2:

```sh
JAVA_HOME=/workspace/jdk/jdk-21.0.13+9 ./mvnw \
  -Dtest=ChatHistoryIntegrationTest \
  -Dspring.datasource.url=jdbc:postgresql://127.0.0.1:55463/chat_history_test \
  -Dspring.datasource.username=postgres -Dspring.datasource.password= \
  -Dspring.datasource.driver-class-name=org.postgresql.Driver \
  -Dspring.jpa.hibernate.ddl-auto=validate test
```

Flyway creates/upgrades the test schema and Hibernate validates it (not create-drop).
Never point this command at production. Local QA used embedded PostgreSQL 17.5,
and a packaged-app HTTP smoke test with `RESQORA_LOG_EMAILS=true` and both AI base
URLs pointed at a local mock server; it exercised real signup/verification/JWT,
uploads, chat persistence/trimming, both GET/DELETE routes, failures, daily limits,
resume deletion and soft account deletion without any real AI/email calls.
