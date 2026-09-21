# Use Case: Diagnose Branding

## Overview

**Use Case ID:** UC-023
**Use Case Name:** Diagnose Branding
**Primary Actor:** Survey Administrator
**Goal:** See which brand directory the service resolved and where each brand asset came from, so that a missing or partial brand mount is visible instead of silently becoming the default theme.
**Status:** Tested

## Preconditions

- The administrator is authenticated and holds the `elicit_admin` role (UC-001).

## Main Success Scenario

1. The administrator opens Branding from the System section.
2. The system shows the configured brand path, whether that directory exists, and which of the brand metadata files (`brand-config.json`, `brand-info.json`) are present there.
3. The system shows the brand currently in use: key, display name, organisation and version as read from the metadata, and a preview of the logo.
4. For each expected asset (colour stylesheet, typography stylesheet, theme stylesheet, horizontal logo, icon, favicon) the system reports where it resolved: the mounted directory, the local directory, the embedded default, or nowhere.
5. The administrator compares the result with the directory they mounted.
6. After correcting the mount, the administrator chooses Reload brand; the system discards the cached brand and shows the freshly resolved result.

## Alternative Flows

### A1: No brand directory is found

**Trigger:** The configured path does not exist and no local directory exists (step 2).
**Flow:**

1. The system states that the embedded default theme is in use and shows every asset as embedded.
2. Use case continues at step 5.

### A2: An asset cannot be read

**Trigger:** An asset exists but reading it fails, for example on permissions (step 4).
**Flow:**

1. The system reports the asset as unreadable with the reason.
2. Use case continues at step 5.

## Postconditions

### Success Postconditions

- The administrator has seen which brand is in effect and where each asset came from; the only change is a discarded cache when Reload brand is chosen.

### Failure Postconditions

- None; nothing is written.

## Business Rules

### BR-091: Every asset reports its source

Each asset is reported individually, because a partial mount (metadata and colours present, typography and theme absent) is the case that is otherwise invisible: the pages render, with the wrong fonts.

### BR-092: Reload is explicit

The resolved brand is cached for the life of the service. Reload brand is the only way to re-resolve it without a restart, and it is offered here rather than happening on every request.

---

## Reference

Traces to FR-023. The three-tier resolution it reports (mounted directory, local directory,
embedded default) is the one `AppConfig` and `BrandUtil` already perform; the umbrella
`docs/BRAND_SYSTEM_IMPLEMENTATION_GUIDE.md` describes the expected directory layout.

Implemented by `BrandDiagnostics` (the per-asset resolution and `reload()`, which clears `BrandUtil`'s
cache) and `SystemBrandingView`. `AppConfig.loadBrandCssContent` now logs a warning instead of swallowing
a read failure (A2). Verified by `SystemViewsRenderTest`.
