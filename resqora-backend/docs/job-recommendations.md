# Resume job recommendations (JobsForce India)

`GET /api/resumes/{resumeId}/job-recommendations` requires the existing JWT. The resume must belong to the caller (otherwise 404, including cached requests); no token is 401. It does not use AI or consume AI daily limits.

```json
{"jobs":[{"title":"Java Developer","hiringName":"JobsForce India for a confidential client","location":"Pune, Maharashtra, India","why":"Matches Java, SQL; aligns with your latest role.","url":"https://jobsforceindia.com/job/example","applyUrl":null,"postedAt":"2026-11-11","source":"JobsForce India"}]}
```

- Zero to **five** jobs, best first. No match or upstream error/timeout/429: HTTP 200 with `{"jobs":[]}`. Failures log only a warning class, not the query/resume/body. The existing Jobicy endpoint is independent of JobsForce failures.
- `hiringName` is **exactly** `hiring.display`, including punctuation/whitespace. Missing display is null; never substitute `name`, `employer` or a made-up company. Most agency listings are for confidential clients, not jobs at a named employer.
- `location` is a display string joined from source city/state/country (plus Remote when applicable), or null. `postedAt` is the source ISO local date (`YYYY-MM-DD`), or null; no fabricated time. `url`/`applyUrl` may individually be null, but at least one validated HTTP(S) URL is required. Link to `url ?? applyUrl` and credit `source`.

## Query and ranking

Reviewed https://jobsforceindia.com/developers and https://jobsforceindia.com/openapi.json. Uses the real `results`, `hiring.display`, location object, `posted_date`, and `apply_url` fields. No API key.

Query = latest dated experience title + first up to three distinct parsed skills, capped at the API's 200-character limit. The search covers all of India and does not filter by the resume's city. Experience comes from parsed experience date ranges, not an AI guess. Supports month-name/year, MM/YYYY, YYYY-MM and year-only ranges (year-only is January), and Present/Current/Ongoing/Now. Overlapping employment intervals are unioned; future endpoints are clamped to today. Less than one known year sets `fresher=true`; internships-only also set `job_type=internship`. Experienced candidates send `experience_years` (one decimal, max 50). Unknown/invalid professional dates omit both experience and fresher filters rather than inventing a value. No name, email, phone, company names or full resume text is sent.

Fetches up to **40 candidates** (two pages of 20; only follows `has_more`/`next_page`), not merely five. If the second page fails, the first page's candidates are still used.

Search summaries almost never name a skill (for example "Our client is looking for a Junior Software Developer to work on exciting projects"), so matching on title + summary scored every posting 0 and nobody without a strongly matching title got picks. The service therefore reads the full listing (`GET /jobs/{id}`) for the first **12** candidates in the provider's relevance order, six at a time, waiting at most 12 seconds in total. Their description, responsibilities, qualifications, skills and preferred skills are added to the text that is matched against the resume skills. Benefits are ignored. A listing that fails or is too slow keeps its summary only. This text is held only in the in-memory cache and is never returned or stored. There is no AI rerank. The rule-based score out of 100 is:

- 60% of existing `JobMatcher.matchScore` (canonical skill coverage);
- 30 × token-Jaccard title similarity, ignoring seniority words;
- up to +5 for recency, linear from today to zero at 30 days (unknown/future dates get zero).

**Minimum inclusive score: 40.0**, plus at least one matched skill or title similarity >= 0.5. Recency alone cannot qualify. Do not pad: fewer than five is expected when only a few jobs pass. Sort by score, then posted date, then title; dedupe before taking five. The `why` line cites only matched skills and/or title alignment.

Dedupe against the **actual five Jobicy suggestions** returned by the existing service for this resume (using its unchanged cache/ranking), by normalised title+nonblank hiring/company name or either URL. Within JobsForce candidates, the identity is normalised title+nonblank hiring name+location, so the same title from the same agency in different cities counts as separate jobs, while the same title, hiring name and location (or either URL) collapses to one. URL normalisation ignores scheme, www, default ports, trailing slashes, fragment and common tracking parameters but preserves job identity query parameters. Missing hiring names never manufacture a title+company identity. Dedupe is recalculated even on JobsForce cache hits so a newly loaded Jobicy list is honoured. A Jobicy failure is isolated; JobsForce can still return its own strong picks.

## Operational behaviour

- Five-second connect/read timeouts; no HTTP retries. At most two search requests plus 12 full-listing requests per uncached resume. Per-instance sliding-window budget of 100 requests/minute is deliberately below the provider's 120/IP/minute limit. A 429 honours numeric Retry-After (minimum 60 seconds), failing fast during cooldown. Current single-instance Render deployment shares this one limiter; multiple replicas sharing an IP would need a shared limiter.
- Bounded process-local per-resume cache (1000 entries); results valid for **12 hours**. Proactive minute-by-minute cleanup removes expired public job data even without traffic (normally ~12h 1m total retention, below 24h). Cache hits always follow the ownership check. Outages are negatively cached for only 60 seconds to prevent storms. Restart loses the cache. No JobsForce data is stored in PostgreSQL or files; **no Flyway migration**, so no V14/V15 conflict.
- Optional base URL: `resqora.jobs.jobsforce-base-url` (default `https://jobsforceindia.com/api/public/v1`), useful for local mock HTTP tests.

## Existing Jobicy cap

`service/jobs/JobSuggestionService.java`: `MAX_RESULTS = 5` (previously 10). New rankings take five; existing cached ten-job lists are sliced to five on read without changing/replacing their cache row, TTL, matching logic, or response shape (`{"jobs":[...],"cached":true|false}`). No other Jobicy behaviour changes.

Tests mock HTTP: query/date building, actual response shape/display attribution, URL filter, pagination, rate budget/cooldown/no retries, threshold and ranking/five cap, exact Jobicy-five dedupe, cache TTL/negative-cache/concurrency/cleanup, JWT/404, unchanged AI quota and JobsForce-to-Jobicy failure isolation.

## Manual API smoke check

One live GET to `https://jobsforceindia.com/api/public/v1/jobs?q=java&per_page=20&page=1` returned HTTP 200, `total=23`, 20 results and `has_more=true`. Replayed that exact response through the real `JobsForceClient` locally (pagination disabled for this replay only): all 20 jobs parsed. Example title: Mid-Level Software Developer; display: JobsForce India (recruiting for a confidential client); city: Nagpur; source date: 2026-11-11. Both source job/apply links, including attribution query parameters, were preserved. No live calls occur in automated tests.
