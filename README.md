# Internship Application Tracker

A small full-stack app for tracking internship applications: register, log in, and
manage a list of companies/roles you've applied to along with their status (wishlist,
applied, OA, interview, offer, rejected). Also supports **job alerts** — save a
keyword search and get emailed when a matching internship posting shows up.

Built as a weekend project to demonstrate a Spring Boot + JWT auth + Postgres API,
plus a React frontend, end to end.

## Tech stack

**Backend**
- Java 21
- Spring Boot 3 (Web, Data JPA, Security, Validation, Mail, Scheduling)
- PostgreSQL
- JWT auth via [jjwt](https://github.com/jwtk/jjwt)
- [Adzuna](https://developer.adzuna.com/) job listings API (job alerts)
- springdoc-openapi (Swagger UI)
- Lombok
- Maven

**Frontend**
- React 19 + Vite
- Plain fetch-based API client (no extra HTTP library)

**Infra**
- Docker / Docker Compose (app + Postgres + frontend, all in one command)

## Data model

**User**
| field        | type      | notes            |
|--------------|-----------|------------------|
| id           | UUID      | primary key      |
| email        | String    | unique, not null |
| passwordHash | String    | BCrypt hash      |
| createdAt    | Instant   | set on insert    |

**Application**
| field       | type              | notes                                |
|-------------|-------------------|---------------------------------------|
| id          | UUID              | primary key                           |
| company     | String            | not null                              |
| role        | String            | not null                              |
| status      | enum              | WISHLIST, APPLIED, OA, INTERVIEW, OFFER, REJECTED |
| appliedDate | LocalDate         | nullable                              |
| notes       | text              | nullable                              |
| link        | String            | nullable                              |
| user        | FK -> User (user_id) | owner of the row                   |

**SavedSearch**
| field      | type    | notes                                    |
|------------|---------|-------------------------------------------|
| id         | UUID    | primary key                                |
| keywords   | String  | not null (e.g. "software engineering intern") |
| location   | String  | nullable                                   |
| createdAt  | Instant | set on insert                              |
| user       | FK -> User (user_id) | owner of the search           |

**SeenListing** — internal bookkeeping so the same posting isn't emailed twice.
| field         | type    | notes                              |
|---------------|---------|-------------------------------------|
| id            | UUID    | primary key                          |
| savedSearch   | FK -> SavedSearch | which search found it       |
| externalId    | String  | the job listing's ID from Adzuna     |
| seenAt        | Instant | when it was first seen               |

Every `/applications/**` and `/searches/**` route is scoped to the authenticated user. Rows belonging
to another user are treated as if they don't exist (404, not 403) so existence
isn't leaked cross-account.

## Running locally with Docker Compose

Requires Docker Desktop (or another Docker engine) with Compose v2. No other setup
needed — the app, Postgres, and the frontend all run in containers.

```bash
docker compose up --build
```

This starts:
- `db` — Postgres 16, with a named volume so data survives restarts
- `app` — the Spring Boot API on `http://localhost:8080`, waiting for Postgres to
  report healthy before it starts
- `frontend` — the React app, built and served as static files via nginx, on
  `http://localhost:3000`

Open **http://localhost:3000** in a browser — register an account, then add/edit
internship applications and change their status from the UI.

To stop everything:

```bash
docker compose down          # keep the DB volume
docker compose down -v       # also wipe the DB volume
```

### Configuration

The app reads all secrets/connection info from environment variables (see
`docker-compose.yml` and `.env.example`) — nothing is hardcoded. Defaults are
provided so `docker compose up` works out of the box for local dev; override them
by copying `.env.example` to `.env` and editing it, or by exporting the vars
yourself before running compose.

| Variable            | Purpose                              | Default (local only)                 |
|---------------------|---------------------------------------|---------------------------------------|
| `DB_HOST`           | Postgres host                        | `db` (set by compose)                |
| `DB_PORT`           | Postgres port                        | `5432`                                |
| `DB_NAME`           | Database name                        | `internship_tracker`                  |
| `DB_USERNAME`       | Database user                        | `postgres`                            |
| `DB_PASSWORD`       | Database password                    | `postgres`                            |
| `JWT_SECRET`        | HMAC signing key for JWTs (>= 32 bytes) | dev-only placeholder, **override in any real deployment** |
| `JWT_EXPIRATION_MS` | Token lifetime in ms                 | `86400000` (24h)                      |
| `SERVER_PORT`       | HTTP port the app listens on         | `8080`                                |
| `ALLOWED_ORIGINS`   | Comma-separated CORS-allowed origins | `http://localhost:5173,http://localhost:3000` |
| `VITE_API_BASE_URL` | API base URL baked into the frontend build | `http://localhost:8080`         |
| `MAIL_HOST`/`MAIL_PORT`/`MAIL_USERNAME`/`MAIL_PASSWORD` | SMTP account job alert emails send from | `smtp.gmail.com:587`, blank credentials |
| `MAIL_FROM`         | "From" address on job alert emails   | `no-reply@internship-tracker.local`  |
| `ADZUNA_APP_ID` / `ADZUNA_APP_KEY` | Free API credentials from [developer.adzuna.com](https://developer.adzuna.com/) | blank (job alerts silently find nothing without these) |
| `ADZUNA_COUNTRY`    | Adzuna country code to search        | `us`                                  |
| `JOB_ALERT_POLL_INTERVAL_MS` | How often (ms) saved searches are checked | `21600000` (6h)              |

## Job alerts

Save a search (keywords + optional location) from the dashboard's "Job alerts"
section, or via the API. A background job polls Adzuna every `JOB_ALERT_POLL_INTERVAL_MS`
and emails you (at your account's registered address) when it finds a listing it
hasn't seen before for that search — duplicate postings are never re-emailed.

Without `ADZUNA_APP_ID`/`ADZUNA_APP_KEY` set, saved searches can still be created,
but the poller finds nothing (logged, not an error). Without SMTP credentials set,
a failed send is logged rather than crashing the poller.

To set it up for real:
1. Register for free Adzuna API credentials at https://developer.adzuna.com/
2. Get an SMTP account to send from — the easiest is a Gmail address with an
   [app password](https://myaccount.google.com/apppasswords) (not your regular password)
3. Set the variables above (via `.env` or exported before `docker compose up`)

## Running without Docker

**Backend** — requires a local Postgres instance and Java 21 + Maven.

```bash
export DB_HOST=localhost DB_NAME=internship_tracker DB_USERNAME=postgres DB_PASSWORD=postgres
export JWT_SECRET=$(openssl rand -base64 32)
mvn spring-boot:run
```

**Frontend** — requires Node 20+. Runs against whatever backend `VITE_API_BASE_URL`
points at (defaults to `http://localhost:8080`).

```bash
cd frontend
npm install
npm run dev
```

Then open `http://localhost:5173`.

## Running the tests

```bash
mvn test
```

Integration tests use an in-memory H2 database (see `src/test/resources/application.yml`)
so they don't need Postgres or Docker running. They cover the core flow:
register → login → create application → fetch it → filter/list → update status →
delete it → confirm it's gone (404) — plus a check that one user can't read another
user's application.

## Trying it out interactively (Swagger UI)

Instead of hand-typing curl commands, once the app is running you can open:

```
http://localhost:8080/swagger-ui/index.html
```

This is a browser page listing every endpoint with a form for the request body,
so you can click "Try it out" and fill in fields instead of writing raw JSON.
To call the protected `/applications` endpoints:
1. Call `POST /auth/register` or `/auth/login` from the page and copy the `token`
   from the response.
2. Click the **Authorize** button (top right, padlock icon) and paste the token
   in (no need to type "Bearer " — Swagger adds that for you).
3. Now every other endpoint's "Try it out" button will send that token
   automatically.

## API reference

All request/response bodies are JSON. Protected routes require
`Authorization: Bearer <token>`.

### Auth

**Register**
```bash
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email": "you@example.com", "password": "password123"}'
```
Returns `201` with `{ "token": "...", "tokenType": "Bearer" }`.

**Login**
```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "you@example.com", "password": "password123"}'
```
Returns `200` with `{ "token": "...", "tokenType": "Bearer" }`.

From here on, set:
```bash
TOKEN="<paste the token from above>"
```

### Applications

**List (optionally filter by status)**
```bash
curl http://localhost:8080/applications \
  -H "Authorization: Bearer $TOKEN"

curl "http://localhost:8080/applications?status=APPLIED" \
  -H "Authorization: Bearer $TOKEN"
```

**Create**
```bash
curl -X POST http://localhost:8080/applications \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "company": "Acme Corp",
    "role": "Backend Engineering Intern",
    "status": "APPLIED",
    "appliedDate": "2026-06-01",
    "notes": "Referred by a friend",
    "link": "https://acme.example.com/careers/123"
  }'
```

**Get one**
```bash
curl http://localhost:8080/applications/{id} \
  -H "Authorization: Bearer $TOKEN"
```

**Update (full replace)**
```bash
curl -X PUT http://localhost:8080/applications/{id} \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "company": "Acme Corp",
    "role": "Backend Engineering Intern",
    "status": "INTERVIEW",
    "appliedDate": "2026-06-01",
    "notes": "Phone screen scheduled",
    "link": "https://acme.example.com/careers/123"
  }'
```

**Update status only**
```bash
curl -X PATCH http://localhost:8080/applications/{id}/status \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"status": "OFFER"}'
```

**Delete**
```bash
curl -X DELETE http://localhost:8080/applications/{id} \
  -H "Authorization: Bearer $TOKEN"
```

### Job alerts (saved searches)

**Create a saved search**
```bash
curl -X POST http://localhost:8080/searches \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"keywords": "software engineering intern", "location": "New York"}'
```

**List your saved searches**
```bash
curl http://localhost:8080/searches -H "Authorization: Bearer $TOKEN"
```

**Delete a saved search**
```bash
curl -X DELETE http://localhost:8080/searches/{id} -H "Authorization: Bearer $TOKEN"
```

### Error responses

Validation failures, not-found, and auth errors all come back as JSON instead of
a stack trace, e.g.:

```json
{
  "timestamp": "2026-06-01T12:00:00Z",
  "status": 400,
  "error": "Validation Failed",
  "message": "One or more fields are invalid",
  "details": ["company: must not be blank", "status: must not be null"]
}
```

## Project structure

```
src/main/java/com/internshiptracker/
├── controller/    # REST endpoints (Auth, Application, SavedSearch)
├── service/       # business logic (AuthService, ApplicationService,
│                  #   SavedSearchService, JobAlertService)
├── repository/    # Spring Data JPA repositories
├── entity/        # JPA entities (User, Application, SavedSearch, SeenListing, ...)
├── dto/           # request/response payloads
├── client/        # external API integration (JobListingClient, AdzunaJobListingClient)
├── security/      # JWT generation/validation, filter, UserDetails wiring
├── config/        # SecurityConfig, OpenApiConfig, JobAlertConfig
└── exception/     # custom exceptions + @RestControllerAdvice handler

frontend/src/
├── api.js         # fetch wrapper for the backend API
├── App.jsx        # top-level auth state (token in localStorage)
├── pages/         # LoginPage, Dashboard
└── components/    # ApplicationTable, ApplicationForm, JobAlerts
```
