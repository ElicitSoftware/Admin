# Quarkus + Vaadin 25 Best-Practices Audit — Remaining Items

**Last updated:** 2026-09-03
**Context:** This is the punch list of what's left from the 2026-09-03 best-practices audit of
the JAX-RS/REST + service layer (`com.elicitsoftware.service`, `com.elicitsoftware.report`),
the Hibernate ORM/Panache entity layer (`com.elicitsoftware.model`), the remaining Vaadin views
and navigation/security classes, and build/config (`pom.xml`, `application.properties`).
Everything else from that audit — 25 findings, plus a Quarkus 3.39.2 regression found and
fixed along the way — has been verified fixed in the source and is no longer tracked here.

## Critical — accepted risk, not scheduled for a fix

- [ ] **`quarkus.tls.trust-all=true`** (global outbound TLS certificate verification disabled,
  affecting the mailer, OIDC, and the dynamic report-service REST client). Kept deliberately
  per explicit sign-off during the 2026-09-03 audit; documented with a comment in
  `application.properties`. Revisit only if the team's risk posture changes — don't "fix" this
  without re-confirming first.

## Significant

- [ ] **Most of the `report/` package's rendering pipeline has zero test coverage** —
  `PDFService`'s full rendering flow (beyond the XXE guard, which is tested), `ReportService`,
  `ReportingService`. Same for several import/export flows: `RespondentImportService`,
  `SurveyDefinitionExportService`/`Service`, `CsvImportService` end-to-end (only its per-line
  error-handling path is covered).
- [ ] **No test coverage for `LoginView`, `LogoutView`, `MainLayout`, `AccessDeniedErrorView`,
  or `NavigationControlAccessCheckerInitializer`.** `DebugView` is covered
  (`DebugViewTest`); these still aren't. They likely need either `NavigationAccessControl`
  test scaffolding or a different approach than the attach-to-`UI` pattern used elsewhere,
  since they're OIDC-redirect-flow views rather than plain content views.

## Minor

- [ ] **`com.elicitsoftware.service` mixes JAX-RS `@Path` resources and plain CDI services**
  in one package — e.g. `TokenService` is actually a REST resource despite its name. A
  mechanical rename/move (e.g. `com.elicitsoftware.rest` for the four `*Resource` classes plus
  `TokenService`) would make "what's network-reachable" easier to audit at a glance. No
  functional bug; purely a readability/maintainability cleanup.
- [ ] **`Refresh`/`ListResponse` DTOs live in the `model` package** alongside actual `@Entity`
  classes. Should move to `com.elicitsoftware.response` (where `AddResponse` etc. already
  live) or a similarly-named package.

## Test infrastructure

- [ ] **The jacoco 70% coverage gate configured in `pom.xml` is not actually being met.**
  `./mvnw verify` (the phase that actually binds `jacoco-check` — `./mvnw test` alone skips it)
  currently measures ~26% line coverage. This predates the 2026-09-03 audit and isn't a
  regression from it — the same shortfall reproduces on the unmodified pre-audit baseline — but
  it means the 70% figure hasn't reflected reality for a while, likely because `mvn verify` and
  `mvn test` aren't the same command and most day-to-day runs use the latter. Closing this is a
  substantial, standalone effort (writing tests broadly across the codebase), not a quick fix.

## Using this list

Pick an item, do the work, update the relevant `docs/use_cases/UC-XXX-*.md` if it changes
documented behavior, add tests traceable to a `UC-XXX` (see `CONTRIBUTING.md`), and remove the
item from this list once verified — the same way the rest of the 2026-09-03 audit's findings
were closed out.
