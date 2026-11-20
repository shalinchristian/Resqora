# Job picks (JobsForce India)

`GET /api/resumes/{resumeId}/job-recommendations` gives up to 5 jobs from JobsForce India for one of your resumes. Needs login, and the resume has to be yours (else 404). It doesn't use AI so it doesn't touch the daily AI limit.

```json
{"jobs":[{"title":"Java Developer","hiringName":"JobsForce India for a confidential client","location":"Pune, Maharashtra, India","why":"Matches Java, SQL; aligns with your latest role.","url":"https://jobsforceindia.com/job/example","applyUrl":null,"postedAt":"2026-11-11","source":"JobsForce India"}]}
```

- 0 to 5 jobs, best first. if JobsForce is down or slow you just get `{"jobs":[]}`
- `hiringName` is exactly what JobsForce shows (`hiring.display`). most listings are agency ones for a "confidential client" so i don't make up a company name
- `location` and `postedAt` can be null, at least one of `url` / `applyUrl` is always there
- no API key needed, docs are at https://jobsforceindia.com/developers

## How the search works

The query is your latest job title + up to 3 skills from the resume. Years of experience are worked out from the dates in your experience section (overlaps aren't counted twice). Under 1 year counts as fresher, and if you only have internships it searches internships. Nothing personal like name, email or phone gets sent.

It grabs up to 40 results (2 pages of 20). The search results barely mention skills, so for the first 12 it also opens the full listing (6 at a time, 12s max) and matches against the description and skills there.

Score out of 100:
- 60% skill match (same `JobMatcher` the other job list uses)
- up to 30 for how close the title is
- up to 5 if it was posted recently (0 after 30 days)

A job needs 40+ and at least one matching skill or a close title, otherwise it's dropped. So sometimes you get fewer than 5 and that's fine. Jobs that already show up in the Jobicy list are removed so you don't see the same job twice.

## Other stuff

- 5s timeouts, no retries, max 100 requests a minute (their limit is 120). if they send a 429 it backs off for at least 60s
- results are cached in memory for 12 hours per resume (failures only for 60s). nothing is saved in the db, so no migration
- base url can be changed with `resqora.jobs.jobsforce-base-url`, handy for tests
- the Jobicy list (`JobSuggestionService`) is also capped at 5 now, old cached lists of 10 just get cut to 5
