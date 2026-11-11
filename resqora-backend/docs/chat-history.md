# Chat history

Chats are saved on the server now so they don't vanish when you switch device. You need to be logged in and you only ever see your own stuff.

| Method | Dashboard chat | Chat for one resume | Returns |
| --- | --- | --- | --- |
| GET | `/api/chat/history` | `/api/resumes/{resumeId}/chat/history` | 200 + the JSON below |
| DELETE | `/api/chat/history` | `/api/resumes/{resumeId}/chat/history` | 204 |

```json
{"messages":[{"role":"user","content":"Question","createdAt":"2026-11-11T17:30:00+05:30"},{"role":"assistant","content":"Answer","createdAt":"2026-11-11T17:30:00+05:30"}]}
```

How it works:
- roles are `user` and `assistant`, the text is in `content` (not `text`)
- max 20 messages are kept (so about the last 10 questions + answers), oldest first
- a reply only gets saved if the AI actually answered. errors, limits or blank replies save nothing
- a resume that's missing or not yours gives the same 404 as the other resume routes
- GET and DELETE still work when AI is off or you hit the daily limit, they don't count towards it
- deleting a resume deletes its chat, deleting your account deletes all your chats
- saving uses a lock on the user row so two replies at once don't mess up the trimming

The POST chat endpoints didn't change. The frontend still sends the last 6 messages (500 chars each) as `history` with `{role: "user" | "model", text}`, so when loading saved history map `assistant` -> `model` and `content` -> `text`.

The table is made in `V14__chat_history.sql`.

## Testing

`./mvnw test` runs everything (ChatHistoryIntegrationTest has the chat cases, AI is mocked). To run them on a real local Postgres instead of H2:

```sh
./mvnw -Dtest=ChatHistoryIntegrationTest \
  -Dspring.datasource.url=jdbc:postgresql://127.0.0.1:5432/chat_history_test \
  -Dspring.datasource.username=postgres -Dspring.datasource.password= \
  -Dspring.datasource.driver-class-name=org.postgresql.Driver \
  -Dspring.jpa.hibernate.ddl-auto=validate test
```

Only use a throwaway test db for this, never the real one.
