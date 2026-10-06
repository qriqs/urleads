# UrLeads agent guide

This file gives coding agents shared context for UrLeads. Read the relevant
documents in `docs/` before making changes. The project currently has planning
documents and an HTML design prototype; do not assume that a production
frontend, backend, database, or deployment already exists. Inspect the current
repository and preserve existing work before acting.

## Product and MVP

UrLeads is a private CRM for a single configured user to manage leads and their
follow-ups. The planned MVP includes:

- Login and logout.
- Create, list, search, filter, view, edit, and delete leads.
- Lead stages: `NUEVO`, `EN_SEGUIMIENTO`, and `CERRADO`.
- An optional next follow-up date, including today and overdue views.
- A dashboard with real lead totals and follow-up counts.
- **Historial y notas**, an internal chronological log on each lead. It has a
  conversation-like presentation, but it is not a chat with the customer.
  Entries are private notes written by the authenticated user, stored as plain
  text, and do not send messages or change the lead stage or follow-up date.

Projects, team collaboration, public registration, roles, AI, file uploads,
WhatsApp/email integrations, automated messages, and real-time customer chat
are outside the MVP. Do not add them unless the user explicitly changes scope.

## Project documents

Consult the source of truth for the task at hand:

| Document | Use it for |
|---|---|
| `README.md` | Project overview and documentation index. |
| `docs/producto.md` | User, product flow, MVP scope, and exclusions. |
| `docs/requisitos.md` | Functional requirements, rules, and course traceability. |
| `docs/arquitectura.md` | Stack, component boundaries, data model, and security. |
| `docs/api.md` | Planned REST routes, payloads, and error contract. |
| `docs/diseno-ui.md` | Screens, visual behavior, and internal lead journal. |
| `docs/dashboard-preview.html` | Static design prototype with fictitious data; not the app. |
| `docs/plan-equipo.md` | Team roles, schedule, and GitHub workflow. |
| `docs/backlog.md` | Initial tasks and acceptance criteria. |
| `docs/pruebas.md` | Planned test coverage and evidence. |
| `docs/despliegue.md` | Railway deployment plan and cost precautions. |
| `docs/entrega-academica.md` | Course deliverables and demonstration checklist. |

These documents describe the agreed plan. If implementation reveals a conflict,
inspect the actual code and tests, then update the relevant documentation rather
than claiming the plan is already implemented.

## Planned technology

The agreed stack is:

- Frontend: React, TypeScript, Vite, Tailwind CSS, shadcn/ui, React Router,
  TanStack Query, and `fetch`.
- Backend: Java 21, Spring Boot, Spring MVC, Spring Data JPA, Spring Security,
  Lombok, Bean Validation, and Maven Wrapper.
- Database and schema: PostgreSQL and Flyway.
- Local database: Docker Compose, if added to the implementation.
- CI: GitHub Actions, if added to the implementation.
- Hosting: Railway, subject to confirming the provider is acceptable to the
  instructor and approving costs.

These are planned choices, not proof that dependencies, scripts, or deployment
configuration exist. Check repository files before using commands or changing
versions. Do not add or upgrade dependencies without the user's approval.

## Architecture and API conventions

The planned production setup is one Spring Boot application serving the built
React assets and `/api`, plus one PostgreSQL service. Local development may run
Vite separately and proxy API requests to Spring.

For backend work, keep responsibilities separated: controllers handle HTTP,
DTOs define API payloads, services enforce business rules, repositories access
data, and JPA entities stay internal. Follow the existing code if it differs;
do not create speculative layers or endpoints.

The planned API uses `/api` and includes:

- `/api/auth/csrf`, `/api/auth/login`, `/api/auth/me`, and
  `/api/auth/logout`.
- CRUD routes under `/api/leads`, nested notes routes under
  `/api/leads/{id}/notes`, and `/api/dashboard`.

Confirm the contract in `docs/api.md` before changing either side. The course
requires working `GET`, `POST`, `PUT`, and `DELETE` operations.

## Security and privacy

- Use Spring Security sessions with an HttpOnly cookie; set Secure in
  production and choose appropriate SameSite behavior.
- Keep CSRF protection enabled. Do not store session credentials in
  `localStorage` or create custom authentication cryptography.
- Hash passwords with `BCryptPasswordEncoder`. Never return hashes or store
  plaintext passwords.
- Scope every lead and its journal entries to the authenticated owner on the
  server. Never rely on a client-supplied owner ID.
- Validate inputs on the server. Treat journal content as plain text and
  prevent HTML/script interpretation in the UI.
- Do not log or commit passwords, tokens, cookies, secrets, real personal data,
  or sensitive Railway variables. Use fictitious sample data.

## Team ownership

The initial responsibilities are recorded in `docs/plan-equipo.md` and
`docs/backlog.md`:

- **Cristopher:** technical coordination, structure, CI, deployment, and
  integration.
- **Sebas:** owns the full frontend, from visual design through API integration.
- **Karlo:** lead CRUD backend, DTOs, validation, repositories, and tests.
- **Alexander:** authentication/security, then journal API and dashboard API,
  with support from Cristopher on Spring Security.
- **Villa:** manual product QA, Postman API checks, reproducible defect reports,
  regression checks, report evidence, and presentation. The report is mostly
  complete; focus remaining document work on screenshots and actual results.

Villa does not own frontend implementation or unit tests. Developers own
automated tests for their modules. Postman/API checks and browser-based manual
testing provide separate evidence and do not replace repository/data tests.

Use these names as planning labels only. Do not assume which person is running
an agent. Work on the task the user assigns, coordinate through the team's
chosen GitHub process, and avoid silently moving another person's work.

The Linear project is [UrLeads in DSW2](https://linear.app/enmanuelprojects/project/urleads-b3c2085df458).
Karlo's Linear account was not returned by the workspace user lookup, so his
issues remain unassigned with his intended ownership stated in each description.
For defect-reserve tasks, use the canonical issues DSW-33 (frontend), DSW-29
(lead data), and DSW-31 (security/integration); their duplicate copies are in
Linear's Duplicate state.

For task boundaries, DSW-8 owns the minimal Spring Boot scaffold and health
endpoint; DSW-10 owns local PostgreSQL and schema migration; DSW-16 is only a
local startup/connectivity smoke test. DSW-13 owns lead CRUD; DSW-28 owns
backend stage/follow-up rules and filters. DSW-18 owns lead list/search/create
UI; DSW-14 owns edit/delete UI and consumes backend filters. The dashboard API
does not depend on the notes API. UL-16 is functional Postman API verification,
not a unit-test substitute.

The early Railway validation task DSW-26 depends only on the Spring scaffold
(DSW-8) and PostgreSQL/migration (DSW-10); it does not wait for complete CRUD,
authentication, or CI. Costs require approval before provisioning. DSW-16 is a
local startup/connectivity check, while DSW-22 is the Postman API verification.

Do not treat the early Railway task as a production release: it validates only
the minimal scaffold and database connection, and requires instructor approval
and explicit cost approval. UL-20b covers the integrated release.

## Working rules

Before editing, inspect repository status, relevant files, and any more-specific
`AGENTS.md` instructions. Treat tracked, untracked, and modified files as user
work. Keep changes focused, follow established project conventions, and update
documentation when behavior or the API contract changes.

Ask before expanding scope, changing the agreed architecture, adding
dependencies, changing provider or billing settings, or performing destructive
operations. Do not create commits, push, open pull requests or issues, deploy,
subscribe, or write to external services unless the user explicitly authorizes
that action.

## Validation

Discover available package scripts and build commands from the actual project
files; do not invent commands. Run the narrowest relevant tests, lint, type
checks, or builds after a code change. Report exactly what ran and what did not.
For documentation-only changes, check links and consistency. The
`docs/dashboard-preview.html` file is a shared visual reference with fictitious
data, not production code. Its internal lead journal is only an in-page demo;
changes are held in memory and disappear on reload. It does not implement
backend persistence, customer messaging, or a real chat.
