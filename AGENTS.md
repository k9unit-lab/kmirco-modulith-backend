# KMicro backend (modulith)

E-commerce backend built as a Spring Modulith: one Spring Boot app, strict modules, each one ready to become its own microservice later.

Status: not scaffolded yet. No code, build or test commands exist yet. `/audit` fills those in after the project is scaffolded.

## Source of truth

- `docs/design/backend-design.md`: every locked decision for this repo (modules, data, money, tax, flows, events, security, API, infra, testing, phases).
- `docs/design/frontend-design.md`: how the web and mobile clients use this API. Read it only when an API contract or the login flow matters.
- Read only the section you need, not the whole file.
- Decisions marked **Locked** are final. Do not reopen them. If work needs a decision the docs do not cover, stop and ask the owner. Do not assume.

## Workflow

Skills live in `.agents/skills/` (Claude Code reads them through the `.claude/skills` link).

- First time only: `/scope` (seed the scope from the design doc phases) → `/architect` (record the stack spec from the design doc) → scaffold → `/audit`.
- Per feature: `/architect` → `/develop` → `/check verify` → `/sync`.
- State lives in files (`docs/scope/`, `docs/specs/`, this file), so start a fresh session (`/clear`) between steps to keep context small.

## Rules

- Phase 1 scope only, unless the owner says otherwise. Later phase work is listed in the design doc, section 16.
- No new dependency, skill, MCP server or tool install without the owner's approval. Never run `npx skills add`. Pin exact versions. Prefer versions published at least 7 days ago.
- No secrets in the repo. Config comes from environment variables; commit a `.env.example` only.
- Write for the owner in short, simple English.

## Git

integration: on

- Work on a feature branch, never commit to `main` directly.
- Never push or open a PR without the owner's OK.
