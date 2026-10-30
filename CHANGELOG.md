# Changelog

Numbers here are only ever copy-pasted from an actual command's output, run at the
time of the entry. If a number isn't backed by a command shown below it, it doesn't
belong here.

## 2026-10-28 — Testcontainers attempted, reverted (environment blocker)

**Goal:** replace the H2 in-memory database in tests with a real Postgres via
Testcontainers, so tests catch Postgres-specific behavior H2's compatibility mode
can silently paper over.

**What happened:** added `spring-boot-testcontainers`, `testcontainers:junit-jupiter`,
and `testcontainers:postgresql` (test scope), plus `AbstractIntegrationTest` using a
JVM-lifetime singleton `PostgreSQLContainer` and `@DynamicPropertySource`. Confirmed
Docker itself is healthy (`docker run hello-world` succeeds; raw `curl --unix-socket`
to `/info` returns `200`), but every Testcontainers version tried — 1.19.8 (Spring
Boot 3.3.4's managed version), 1.20.4, 1.20.6, 2.0.5 — fails the same way:

```
$ mvn test
...
UnixSocketClientProviderStrategy: failed with exception BadRequestException (Status 400: {"ID":"","Containers":0,...})
DockerDesktopClientProviderStrategy: failed with exception BadRequestException (Status 400: {"ID":"","Containers":0,...})
```

Reproduced identically outside Claude Code's sandbox, in a plain terminal — ruling out
sandboxing as the cause. Also tried pinning `DOCKER_API_VERSION=1.44` to rule out a
version-negotiation mismatch against this machine's very new Docker Engine
(29.7.2 / API 1.55, built 2026-08-05) — no change.

**Decision:** reverted to H2 for now (`git diff` confirms test classes no longer
extend `AbstractIntegrationTest`; `pom.xml` and `src/test/resources/application.yml`
restored). Kept `AbstractIntegrationTest.java` and the `testcontainers.version`
property in `pom.xml` in place, unused, so re-enabling this is a small diff once the
root cause (suspected `docker-java`/Testcontainers incompatibility with this Docker
Desktop build) is resolved — either by a Testcontainers point release, a Docker
Desktop downgrade, or switching to an alternate runtime (e.g. Colima).

**Baseline (unaffected by this attempt):**
```
$ mvn -q test
Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
(mvn -q test 2>&1 — 12.32s user 0.59s system 204% cpu 6.322 total)
```

## 2026-10-28 — Fix: failed job alert emails no longer lose the listing

**Bug:** `JobAlertService.checkSearch` saved each `SeenListing` row *before* calling
`sendNotificationEmail`, and that method caught and swallowed its own `mailSender.send`
exception. Net effect: if SMTP was down, the listing was marked seen anyway and the
user never got notified — not on that run, not on any future run, since the dedupe
check (`existsBySavedSearchIdAndExternalId`) would now say "already seen".

**Proof added first (`JobAlertIntegrationTest.failedEmailDoesNotMarkListingAsSeen`),
run against the unmodified code to confirm it caught the bug:**
```
$ mvn -q test -Dtest=JobAlertIntegrationTest#failedEmailDoesNotMarkListingAsSeen
expected: 0L
 but was: 1L
```
(A second, independent symptom showed up in the same run: the next poll crashed with
an H2 unique-constraint violation trying to re-insert the same `SeenListing` row —
direct evidence of the dedupe check and the "seen" write being inconsistent, the same
root cause as bug #2 below.)

**Fix:**
- `sendNotificationEmail` no longer catches its own send exception — it propagates.
- `checkSearch` now calls `sendNotificationEmail` *before* persisting anything, and
  only calls the new `SeenListingRecorder.markSeen(...)` if the send succeeded.
- `SeenListingRecorder` is a separate `@Transactional` bean (not a method on
  `JobAlertService`), so each search's write commits in its own transaction. This
  also fixes bug #2 (one search's `SeenListing` writes no longer share a transaction
  with every other search's): `pollAllSavedSearches` lost its own `@Transactional` —
  the comment it carried explained that annotation existed only to let
  `checkSearch`'s self-invocation "ride along" inside one shared transaction; moving
  persistence to a separate bean removes the need for that trick entirely.
- Added `SavedSearchRepository.findAllWithUser()` (a `JOIN FETCH` query) since
  `pollAllSavedSearches` no longer runs inside a transaction that would otherwise keep
  `search.getUser()` loadable.

**Verified:**
```
$ mvn test
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0 -- in ApplicationFlowIntegrationTest
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0 -- in SavedSearchIntegrationTest
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0 -- in JobAlertIntegrationTest
Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

**Bonus fix found along the way:** `@Scheduled(fixedRateString=...)` fires its first
execution immediately at context startup (not after the first interval). This raced
with `JobAlertIntegrationTest`'s manual `pollAllSavedSearches()` call when the test
class ran in isolation, sending the same alert twice. Confirmed pre-existing (not
caused by the above fix) via `git stash` against the original code. Fixed by adding
`initialDelayString = "${app.job-alerts.poll-interval-ms}"` to the `@Scheduled`
annotation — also avoids hammering Adzuna's rate-limited free tier on every
app restart/deploy. Verified with 3 repeated isolated runs:
```
$ mvn -q test -Dtest=JobAlertIntegrationTest
(exit=0, x3)
```

## 2026-10-29 — Fix: N+1 query in JobAlertService.checkSearch

**Bug:** `checkSearch` called `seenListingRepository.existsBySavedSearchIdAndExternalId`
once per listing returned by Adzuna — one round-trip query per listing, every poll,
for every saved search.

**Measured before (temporary `show-sql: true` + `hibernate.generate_statistics: true`
in `src/test/resources/application.yml`, reverted after measuring):**
```
$ mvn -q test -Dtest=JobAlertIntegrationTest#newListingsEmailOnceAndSecondRunSendsNothing
(4 occurrences of "select sl1_0.id from seen_listings sl1_0 where sl1_0.saved_search_id=? and sl1_0.external_id=?"
 -- 2 poll calls x 2 listings each = 4 queries, one per listing)
```

**Fix:**
- Added `SeenListingRepository.findSeenExternalIds(UUID savedSearchId, Collection<String> externalIds)`,
  a single `@Query` using `external_id IN :externalIds`, returning the already-seen
  subset as a `Set<String>`.
- `checkSearch` now calls it once per poll (not once per listing) and filters
  `newListings` against the returned set in memory.
- Added an early `if (listings.isEmpty()) return;` guard before the batched call,
  since an empty `IN ()` clause is invalid JPQL.

**Measured after (same temporary logging):**
```
$ mvn -q test -Dtest=JobAlertIntegrationTest#newListingsEmailOnceAndSecondRunSendsNothing
(2 occurrences of "select sl1_0.external_id from seen_listings sl1_0 where sl1_0.saved_search_id=? and sl1_0.external_id in (?,?)"
 -- 2 poll calls x 1 query each = 2 queries total, independent of listing count)
```

**Verified full suite green after reverting the temporary logging:**
```
$ mvn -q test; echo "EXIT CODE: $?"
EXIT CODE: 0
```

## 2026-10-29 — Fix: SecurityConfig fail-open default

**Bug:** `securityFilterChain` ended with `.anyRequest().permitAll()`. The three
existing controllers were correctly listed, but any endpoint added later (or any
unmapped path) would be public by default with no one having to opt into that.

**Fix:** changed the catch-all to `.anyRequest().authenticated()` — safer
middle ground: any future endpoint defaults to requiring a JWT instead of being
open. Added an explicit `.requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()`
since Swagger UI (documented in README as the way to try the API interactively)
relied on the old catch-all to stay public. Removed the now-redundant explicit
`.authenticated()` entries for `/applications/**` and `/searches/**` since
`anyRequest().authenticated()` already covers them.

**Verified (full suite + manual check with app running against real Postgres via
`docker compose up -d db mailpit` and `mvn spring-boot:run`):**
```
$ mvn -q test; echo "EXIT CODE: $?"
EXIT CODE: 0

$ curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/swagger-ui/index.html
200
$ curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/v3/api-docs
200
$ curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/applications
401
$ curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/auth/login
500   (reachable -- 500 is an empty-body app error, not an auth block)
$ curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/actuator/env
403   (an unmapped path -- would have been 200/permitAll before this fix)
```

## 2026-10-29 — Fix: JwtService silently returned claims from expired tokens

**Bug:** `parseClaims` caught its own `ExpiredJwtException` and returned
`e.getClaims()` instead of rethrowing. `isTokenValid` happened to still reject
expired tokens because it separately compared the (expired) `exp` claim against
`now`, but `extractEmail` called the same `parseClaims` and had no such check —
it silently returned the subject email from an expired token's claims with no
error. Safe today only because `JwtAuthenticationFilter` happens to call
`isTokenValid` before `extractEmail`; any future caller of `extractEmail` alone
would get a seemingly-valid email back for an expired token.

**Proof added first (`JwtServiceTest`, a plain unit test — no Spring context
needed since `JwtService`'s constructor takes raw args), run against the
unmodified code:**
```
$ mvn -q test -Dtest=JwtServiceTest
[ERROR] JwtServiceTest.extractEmailThrowsForExpiredTokenInsteadOfSilentlyReturningIt -- FAILURE!
java.lang.AssertionError: Expecting code to raise a throwable.
Tests run: 3, Failures: 1, Errors: 0, Skipped: 0
```
(The other two tests passed even before the fix — `isTokenValid`'s separate
manual expiration check already worked. Only `extractEmail`'s silent success
on an expired token was wrong.)

**Fix:**
- `parseClaims` no longer catches `ExpiredJwtException` — jjwt already
  validates the `exp` claim during parsing and throws for an expired token, so
  catching and swallowing it was both unnecessary and the source of the bug.
- `isTokenValid` now just calls `parseClaims` and returns `true`/`false` based
  on whether it throws, instead of doing its own manual `exp` comparison.
- Net effect: every caller of `parseClaims` (both `isTokenValid` and
  `extractEmail`) now fails closed on an expired or otherwise invalid token,
  not just the one caller that happened to check expiration manually.

**Verified:**
```
$ mvn -q test -Dtest=JwtServiceTest
EXIT CODE: 0

$ mvn -q test
EXIT CODE: 0
```

## 2026-10-29 — Fix: replaced Lombok @Data on JPA entities

**Bug (latent, not test-caught):** `User`, `SavedSearch`, `Application`, and
`SeenListing` all used `@Data`, which generates `equals`/`hashCode`/`toString`
over every field. Three entities have a lazy `@ManyToOne` association
(`SavedSearch.user`, `Application.user`, `SeenListing.savedSearch`) — calling
the generated `toString()` (e.g. accidentally logging an entity) or
`equals`/`hashCode` (e.g. putting an entity in a `Set`) outside an open
Hibernate session on one of those would trigger a lazy load and could throw
`LazyInitializationException`. Separately, field-based `hashCode` is unsafe
for JPA entities in general: a transient entity's hashCode changes once its
generated `id` is assigned, which breaks it silently if it was ever put in a
`HashSet`/`HashMap` before being persisted.

**Fix:** replaced `@Data` with `@Getter @Setter` on all four entities, plus:
- Manual `equals`/`hashCode` based on `id` only (JPA best practice — see Vlad
  Mihalcea's "the perfect equals and hashCode" approach): two transient
  entities are never equal to each other, and `hashCode` is a constant
  (`getClass().hashCode()`) so it never changes across the entity's lifecycle.
- Manual `toString()` that only reads the entity's own scalar fields, never a
  lazy association, so logging an entity can never trigger a lazy load.

Confirmed no code relies on entity `equals`/`hashCode`/`toString` in a way this
would change (`grep` for `.equals(`, `.hashCode()`, `Set<User>` etc. and entity
`toString()`/logging across `src/main` and `src/test` — none found; `Setter`
is kept, not just `Getter`, since services reassign fields such as
`Application.status` directly).

**Verified:**
```
$ mvn -q test
EXIT CODE: 0
```

## 2026-10-29 — Fix: AdzunaJobListingClient treated outages as "zero results"

**Bug:** `search()` wrapped the entire HTTP call in `catch (Exception e) { return List.of(); }`.
A 500, a timeout, or a malformed response all looked identical to "Adzuna
legitimately has nothing new" — `JobAlertService` could never tell an outage
apart from a quiet week. The interface's own javadoc documented this as
intended: "Returns an empty list ... if nothing matches **or the search
fails**." There was also no connect/read timeout configured on the shared
`RestClient` bean, so a hung connection could block a poll indefinitely.

**Proof added first (`AdzunaJobListingClientTest`, direct unit test against
the client with WireMock, no Spring context), run against the unmodified
client:**
```
$ mvn -q test -Dtest=AdzunaJobListingClientTest
[ERROR] AdzunaJobListingClientTest.outageThrowsInsteadOfSilentlyReturningEmptyList -- FAILURE!
java.lang.AssertionError: Expecting code to raise a throwable.
Tests run: 2, Failures: 1, Errors: 0, Skipped: 0
```

**Fix:**
- `search()` now retries transient failures (`ResourceAccessException` —
  timeouts/connection issues, and `HttpServerErrorException` — 5xx) up to 3
  attempts with a 200ms backoff, then rethrows instead of swallowing. A 4xx
  is not retried (retrying a bad request won't fix it).
- A legitimate empty result (`response.results()` empty or null) still
  returns `List.of()` — only an actual failure propagates.
- `JobListingClient.search`'s javadoc updated to state the new contract:
  callers must not treat a thrown exception as an empty result. No change
  needed in `JobAlertService` — `pollAllSavedSearches`'s existing per-search
  `catch (Exception e) { log.error(...) }` already does the right thing once
  the client stops hiding the failure from it.
- Added `app.adzuna.connect-timeout-ms` / `read-timeout-ms` (defaults 3000 /
  5000) via `ClientHttpRequestFactorySettings` on the `RestClient` bean in
  `JobAlertConfig`, so a hung connection can't block a poll indefinitely.
- No new dependency: timeouts use
  `org.springframework.boot.web.client.ClientHttpRequestFactories`, already
  provided by `spring-boot` itself.

**Verified:**
```
$ mvn -q test -Dtest=AdzunaJobListingClientTest
(3 retry attempts logged, then exception propagates - exit 0)

$ mvn -q test
EXIT CODE: 0
```

## 2026-10-29 — Added pagination to GET /applications

**Change:** `ApplicationRepository.findAllByUserId`/`findAllByUserIdAndStatus`
now take a `Pageable` and return `Page<Application>` instead of loading every
matching row into memory. `ApplicationController.list` accepts a `Pageable`
(`@PageableDefault(size = 20)` — standard Spring Data `page`/`size`/`sort`
query params, no new dependency) and the response body is now a `Page<T>`
(`{"content": [...], "totalElements": N, ...}`) instead of a bare JSON array.
`ApplicationFlowIntegrationTest`'s list assertion updated from `$[0].id` to
`$.content[0].id`.

Left `SavedSearchRepository.findAllByUserId` unpaginated — a user accumulates
a handful of saved searches at most, unlike applications which can grow into
the hundreds over a job search spanning years; pagination there would be
unused complexity.

**Verified:**
```
$ mvn -q test
EXIT CODE: 0
```

## 2026-10-29 — Added Actuator /health, locked everything else down

**Change:** added `spring-boot-starter-actuator` (new dependency — asked
first). `management.endpoints.web.exposure.include: health` exposes only
`/actuator/health`; everything else under `/actuator/**` stays unexposed and,
on top of that, falls under the deny-by-default `.anyRequest().authenticated()`
in `SecurityConfig` from the earlier fix, so it's 403 either way. Added a
`.requestMatchers("/actuator/health").permitAll()` for the one endpoint
that's meant to be public (container/k8s liveness checks).

**Bug found while verifying manually, fixed in the same pass:** the mail
health indicator (`MailHealthContributorAutoConfiguration`) tried to connect
to the configured SMTP host and failed against the default
`smtp.gmail.com` with no real credentials in local dev, which flipped the
*entire* `/actuator/health` to `503 DOWN` — not just a mail-specific
sub-status. A liveness probe wired to this endpoint would have restart-looped
an otherwise-healthy app purely because SMTP was unreachable. Disabled with
`management.health.mail.enabled: false`: an SMTP outage isn't "the app is
down" — a failed job alert send already fails safe (the listing stays
unmarked and is retried next poll, per the earlier `JobAlertService` fix) and
shouldn't be conflated with the app's own liveness.

Also disabled the same indicator in the test profile
(`src/test/resources/application.yml`) — it fails to initialize at all when
`JavaMailSender` is `@MockBean`'d (`JobAlertIntegrationTest`), with
`Beans must not be empty`. (While fixing this, caught and corrected a nesting
mistake in my own edit to that file: `mail:` had ended up as a child of the
new `management:` block instead of `spring:`, which silently broke real mail
autoconfiguration for every test — caught immediately by the full suite
failing with `No qualifying bean of type JavaMailSender`.)

**Verified (manual, against the app running with real Postgres + Mailpit):**
```
$ curl -s http://localhost:8080/actuator/health
{"status":"UP"}
$ curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/actuator/health
200
$ curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/actuator
403
$ curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/actuator/env
403

$ mvn -q test
EXIT CODE: 0
```

## 2026-10-29 — Added GitHub Actions CI

**Change:** added `.github/workflows/ci.yml` — on push to `main` and on every
PR, checks out, sets up JDK 21 (Temurin, Maven dependency caching), and runs
`mvn -B test`. No services block needed: tests use H2 in-memory with Flyway
disabled, not the real Postgres.

**Honest caveat:** this has not actually run on GitHub's runners yet (no push
has been made) — only locally simulated by running the exact same command.
YAML syntax validated with `python3 -c "import yaml; yaml.safe_load(...)"`.
Flagging this explicitly rather than claiming it works untested on the real
runner.

**Verified (local simulation of the exact CI command):**
```
$ python3 -c "import yaml; yaml.safe_load(open('.github/workflows/ci.yml'))"
YAML valid

$ mvn -B test
Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 2026-10-29 — README architecture diagram + doc updates

**Change:** added a Mermaid architecture diagram to `README.md` (renders
natively on GitHub) showing the request path (frontend → JWT filter →
controllers → services → Postgres) alongside the independent scheduled path
(`JobAlertService` → Adzuna / SMTP, on its own timer, not tied to any HTTP
request). Updated the tech stack list (Flyway, Actuator, CI), the
applications list endpoint's docs (pagination params + response shape),
project structure (`db/migration/`, `.github/workflows/`), and added an
"Operational endpoints" section documenting `/actuator/health`'s behavior.

**Verified:** full suite still green after the doc-only change (sanity check
that nothing in this pass touched code):
```
$ mvn -q test
EXIT CODE: 0
```

## 2026-10-30 — Bugs found while manually testing the running app

**Bug 1: white screen on login.** The earlier pagination change
(`GET /applications` now returns `{content, totalElements, ...}` instead of
a bare array) was never reflected in the frontend. `Dashboard.jsx` set
`applications` to the whole page object and called `.length`/`.map()` on it,
throwing an uncaught render error with no error boundary — the entire React
tree went blank. **Fix:** `frontend/src/api.js`'s `listApplications` now
unwraps `page.content` itself, so every caller keeps getting a plain array;
no change needed in `Dashboard.jsx`. Verified by re-checking the actual
`GET /applications` response shape and rebuilding/redeploying the frontend
container.

**Bug 2 (pre-existing, unrelated to today's refactors): deleting a saved
search with any matched listings threw a 500.** `SavedSearchService.delete`
called `savedSearchRepository.delete(savedSearch)` directly. Once a search
had been polled and matched at least one listing, its `SeenListing` rows
reference it via a foreign key, so Postgres rejected the delete:
```
PSQLException: ERROR: update or delete on table "saved_searches" violates
foreign key constraint "fkhvq0m5y86jwhbbldwvbw715a1" on table "seen_listings"
```
**Proof added first** (`SavedSearchIntegrationTest.deletingASearchWithMatchedListingsDoesNotViolateForeignKey`
— creates a search, inserts a `SeenListing` row for it directly via the
repository to simulate the poller having already matched something, then
deletes via the API), confirmed RED by reverting just the fix line and
re-running:
```
$ mvn -q test -Dtest=SavedSearchIntegrationTest#deletingASearchWithMatchedListingsDoesNotViolateForeignKey
java.lang.AssertionError: Status expected:<204> but was:<500>
```
**Fix:** added `SeenListingRepository.deleteAllBySavedSearchId`, called from
`SavedSearchService.delete` before deleting the search itself.

**Verified:**
```
$ mvn -q test
EXIT CODE: 0
```

---

All items from the original known-issues list are now addressed, except
Testcontainers (explicitly deferred — see the first entry above — due to an
unresolved `docker-java`/Docker Desktop compatibility issue on this machine,
confirmed outside the sandbox).

## 2026-10-29 — Flyway migrations + proven indexes on foreign key columns

**Change:** added `flyway-core` + `flyway-database-postgresql` (new dependency
— asked first). Switched `spring.jpa.hibernate.ddl-auto` from `update` to
`validate`: Flyway now owns the schema; Hibernate only checks the entity
mappings match it at startup, instead of silently auto-altering tables.
`V1__baseline_schema.sql` reproduces the existing schema exactly (verified —
see below). Tests keep using H2 with `ddl-auto: create-drop` and have
`spring.flyway.enabled: false`, since Flyway's Postgres-dialect migrations
and H2's own auto-create would otherwise collide.

The dev Postgres predates Flyway (schema already existed from the old
`ddl-auto: update`), so `baseline-on-migrate: true` / `baseline-version: "1"`
treats that existing schema as already being at V1, instead of failing on
"non-empty schema without metadata table" — confirmed this didn't require
dropping any existing data.

**Bug found and fixed in the same pass:** Postgres does not automatically
index foreign key columns. `applications.user_id` and `saved_searches.user_id`
had no index, despite every per-user query (list/view applications, list
saved searches) filtering on them.

**Measured before (seeded 500,000 rows via `generate_series` on the real dev
Postgres, on top of existing data — no destructive action taken):**
```
$ docker compose exec -T db psql -U postgres -d internship_tracker -c \
  "EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM applications WHERE user_id = '<target>';"

Seq Scan on applications  (cost=0.00..6397.23 rows=92 width=2132) (actual time=31.862..31.863 rows=0 loops=1)
  Filter: (user_id = '<target>'::uuid)
  Rows Removed by Filter: 500202
  Buffers: shared hit=6166
Execution Time: 31.873 ms
```

**Fix:** `V2__index_foreign_keys.sql` adds `idx_applications_user_id` and
`idx_saved_searches_user_id`.

**Measured after (same query, same data, index applied manually first to
measure, then dropped so Flyway's own V2 run created it cleanly on next
startup — confirmed via `flyway_schema_history` and `\di idx_*`):**
```
Index Scan using idx_applications_user_id on applications  (cost=0.42..4.45 rows=1 width=617) (actual time=0.037..0.038 rows=0 loops=1)
  Index Cond: (user_id = '<target>'::uuid)
  Buffers: shared read=3
Execution Time: 0.071 ms
```
**31.873ms → 0.071ms (~450x), buffers 6166 → 3, plan: Seq Scan (500,202 rows
scanned) → Index Scan.**

(`seen_listings(saved_search_id, external_id)` already has a covering index
via its existing `UNIQUE` constraint — no new index needed there; this is
what the N+1 fix's `findSeenExternalIds` query already benefits from.)

**Verified app starts cleanly against real Postgres with both migrations
applied, and the full test suite (H2, Flyway disabled) is unaffected:**
```
$ mvn -q spring-boot:run
... Started InternshipTrackerApplication in 1.943 seconds ...

$ docker compose exec -T db psql -U postgres -d internship_tracker -c \
  "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
 version |      description      | success
---------+-----------------------+---------
 1       | << Flyway Baseline >> | t
 2       | index foreign keys    | t

$ mvn -q test
EXIT CODE: 0
```
