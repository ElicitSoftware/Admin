# Elicit Admin — Vision

> **Draft.** This is the AIUP seed document. Edit freely — `/requirements`,
> `/entity-model`, and the rest of the AIUP workflow read from here.

## Mission

Elicit Admin is the administrative module of the
[Elicit Software](https://github.com/ElicitSoftware/) platform. It lets staff
administer surveys end-to-end: upload subjects, generate tokenized invitations,
monitor respondent progress, send reminder emails, and view results. It is the
operational counterpart to the respondent-facing Survey module and the
Authoring tool.

## Target Users

- **Survey administrators** — staff who register subjects, generate invitation
  tokens, dispatch and re-send invitation/reminder emails, and monitor
  completion across a study.
- **Researchers / clinicians** — consumers who review results and derived
  reports produced from respondent submissions.
- **Platform operators** — staff responsible for OIDC-based access control and
  the health of the deployed service.

## Goals

- Bulk-register subjects and generate per-subject invitation tokens.
- Track and display each subject's progress through their survey.
- Send invitation and reminder emails to subjects.
- Present results and reports to authorized staff.
- Enforce role-based access via an OIDC provider (Keycloak in test; any
  OIDC-compliant server in production).
- Operate as a Quarkus service deployable via Docker with health, metrics, and
  tracing endpoints.

## Scope

**In scope**
- Subject upload/registration and token generation.
- Progress monitoring dashboards over the shared survey database.
- Reminder / invitation email workflows.
- Results and report viewing for authorized roles.
- OIDC authentication and authorization (pre-defined roles).
- Observability: OpenTelemetry tracing, SmallRye Health, Micrometer/Prometheus
  metrics.

**Out of scope** (handled by sibling modules)
- Rendering surveys and recording respondent answers → Survey module.
- Authoring surveys / defining question trees → Authoring tool.
- Cross-survey analytics dashboards → downstream reporting tools.

## Constraints

- **License:** PolyForm Noncommercial 1.0.0.
- **Stack (do not deviate without explicit approval):**
  - Java 21
  - Quarkus 3.37.x
  - Vaadin 25.2.x (Flow / server-side UI)
  - Hibernate ORM with Panache (JPA) — *not* jOOQ
  - PostgreSQL (Flyway migrations)
  - Maven build, Docker deploy
- **Auth:** access control via an OIDC provider (Keycloak for testing).
- **Branching:** part of the multi-module Elicit Software platform; database
  schema and message contracts are shared with the Survey and Authoring modules.
- **Compliance:** subject data may include PHI in clinical deployments (e.g.,
  the Family Health History Survey) — treat subject records as sensitive.
- **Accessibility:** Vaadin components should be used in a way that preserves
  the framework's built-in WCAG support; avoid raw HTML that bypasses it.

## Reference Deployment

The [Family Health History Survey (FHHS)](https://github.com/ElicitSoftware/FHHS)
is the canonical reference deployment of the Elicit platform and the best place
to see the Admin module in action end-to-end.
