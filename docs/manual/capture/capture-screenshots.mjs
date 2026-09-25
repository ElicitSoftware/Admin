/*
 * UC-029 BR-002: every procedure in the administrator's manual carries a captioned screenshot of
 * the screen it describes.
 *
 * Drives a real Chromium against a running Admin (the umbrella compose stack) and writes the
 * manual's 29 figures to docs/manual/images/ at 2x for print. See docs/manual/README.md for how
 * to run it.
 *
 * THE DIFFERENCE FROM AUTHOR'S CAPTURE
 * ------------------------------------
 * Author's walks a sample survey that must already exist. Admin's needs seeded *console* state,
 * and a fresh install seeds none of it (UC-028): no department, no survey, no message template,
 * no subject. So this script creates what is missing on its way through the chapters — every
 * step first asks whether the record is already there and skips the save if it is, so a second
 * run against the same stack produces the same figures and no duplicate data.
 *
 * Where a setup step *is* one of the manual's figures — the create-department form, the apply
 * screen, the register form — the figure is taken from the setup work itself, with the form
 * fully filled before the shutter. On a re-run the same form is filled and then abandoned rather
 * than saved, so the figure never degrades to a half-filled form.
 *
 * TWO FIGURES ONLY EXIST BEFORE THE SETUP RUNS
 * --------------------------------------------
 * `03-department-required` (the UC-028 blocking dialog) and `21-missing-survey-notice` (the
 * UC-019 banner) are states a working console has left behind. They are captured first, before
 * the department is created and before the definition is applied. On a stack that is already
 * set up they cannot be reproduced; the script says so and leaves the committed figure alone.
 * That is why the capture order below is not the figure numbering.
 *
 * WHAT THE FIGURES WILL SHOW
 * --------------------------
 * The seeded `admin` and `user` accounts are still present on a stack this script can sign into,
 * so UC-021's seeded-account banner sits above every administrator screen. It is not noise: it
 * is what a fresh install looks like, and `28-default-account-warning` is that banner. Renaming
 * the accounts to clear it would desynchronise the console from Keycloak and lock the capture
 * out, so the banner stays.
 */
import { chromium } from 'playwright';
import { mkdir } from 'node:fs/promises';
import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';

const BASE = (process.env.ADMIN_URL || 'http://localhost:8081').replace(/\/+$/, '');
const USER = process.env.ADMIN_USER || 'admin';
const PASS = process.env.ADMIN_PASSWORD || 'admin';
const OUT = process.env.MANUAL_IMAGES
  ? path.resolve(process.env.MANUAL_IMAGES)
  : path.resolve(import.meta.dirname, '../images');
/**
 * The survey the figures install, and the finished respondents figure 15 needs. Both come from
 * `fixtures/`, so the capture depends on nothing outside this repository. MANUAL_SURVEY_DEFINITION
 * points it at another definition — `../../../../FHHS/family-history-survey.elicit` in an umbrella
 * checkout — but then the respondent fixtures no longer match it and figure 15 falls back to
 * whatever the deployment already has.
 */
const FIXTURES = path.resolve(import.meta.dirname, 'fixtures');
const DEFINITION = process.env.MANUAL_SURVEY_DEFINITION
  ? path.resolve(process.env.MANUAL_SURVEY_DEFINITION)
  : path.join(FIXTURES, 'household-survey.elicit');
/** Three respondents who finished the fixture survey, exported from the multi-site e2e run. */
const RESPONDENT_FIXTURES = [
  'respondent-marcus-bell.elicit',
  'respondent-nadia-okafor.elicit',
  'respondent-theo-lindqvist.elicit',
].map((name) => path.join(FIXTURES, name));

/**
 * The survey name that definition installs, as the Register and Export screens list it — and as
 * the example message template names it. An export carries it in its header, so pointing
 * MANUAL_SURVEY_DEFINITION at another definition does not also need MANUAL_SURVEY.
 */
function surveyNameFrom(definition) {
  if (!existsSync(definition)) return null;
  const header = readFileSync(definition, 'utf8').slice(0, 2000);
  return header.match(/^# survey_name:\s*(.+)$/m)?.[1].trim() || null;
}

const SURVEY = process.env.MANUAL_SURVEY || surveyNameFrom(DEFINITION) || 'Family History Survey';

/**
 * The figures of the plan's "Chapters and figures" table, in numbering order. `shot()` refuses a
 * name that is not here, and the run ends with a report of anything not captured.
 */
const FIGURES = [
  '01-navigation',
  '02-sign-in',
  '03-department-required',
  '04-departments',
  '05-edit-department',
  '06-users',
  '07-edit-user',
  '08-message-templates',
  '09-edit-message-template',
  '10-register-subject',
  '11-register-result',
  '12-search-email-action',
  '13-search-filters',
  '14-search-results',
  '15-report-download',
  '16-respondent-export',
  '17-respondent-import',
  '18-apply-survey-definition',
  '19-apply-result',
  '20-export-survey-definition',
  '21-missing-survey-notice',
  '22-system-overview',
  '23-system-database',
  '24-system-branding',
  '25-system-email',
  '26-system-connections',
  '27-system-oidc',
  '28-default-account-warning',
  '29-language-selector',
];

/** The console state the walk leaves behind. Named so a re-run recognises its own work. */
const DEPARTMENT = {
  name: 'Manual Clinic',
  code: 'MANUAL',
  // EditDepartmentView validates this with Vaadin's EmailValidator, which rejects a bare host
  // such as elicit@localhost and leaves "Create department" disabled. Same domain as the
  // subject fixture below.
  fromEmail: 'elicit@example.org',
  // Required by EditDepartmentView: the id of the template sent on registration. The template
  // created below is the first one on a fresh database, so it is id 1.
  defaultMessageId: '1',
};

/** One non-default account per console role (UC-008, UC-016). */
const ACCOUNTS = [
  { username: 'manual.admin', firstName: 'Avery', lastName: 'Stone', role: 'elicit_admin' },
  { username: 'manual.user', firstName: 'Robin', lastName: 'Patel', role: 'elicit_user' },
];

/**
 * text/plain, not text/html: the preview column then shows the <ACCESS_CODE> placeholder as
 * written, which is the point of the figure (UC-007, C-011).
 */
const TEMPLATE = {
  mimeType: 'text/plain',
  subject: `Your ${SURVEY} invitation`,
  body: [
    'Hello,',
    '',
    `You have been invited to complete the ${SURVEY}.`,
    'Open http://localhost:8080 and enter your access code:',
    '',
    '    <ACCESS_CODE>',
    '',
    'Thank you,',
    'The study team',
  ].join('\n'),
};

const SUBJECT = {
  firstName: 'Dana',
  lastName: 'Winters',
  email: 'dana.winters@example.org',
  phone: '734-555-0142',
};

/** SearchView's grid: 9 data columns, then Edit, then Action (SearchView.getSubjectGrid). */
const GRID_COLUMNS = 11;
const COL_ACCESS_CODE = 0;
const COL_STATUS = 8;
const COL_ACTION = 10;

/** Vaadin 25 slots a field's label as a <label> child of the field host, so fields match on that. */
const FIELD_TAGS = [
  'vaadin-text-field', 'vaadin-text-area', 'vaadin-email-field', 'vaadin-password-field',
  'vaadin-integer-field', 'vaadin-number-field', 'vaadin-date-picker',
  'vaadin-combo-box', 'vaadin-multi-select-combo-box', 'vaadin-select', 'vaadin-checkbox',
];

let page;
const captured = new Set();
const skipped = [];

const host = (label) =>
  page.locator(FIELD_TAGS.map((t) => `${t}:has(> label:text-is("${label}"))`).join(',')).first();
const field = (label) => host(label).locator('input, textarea').first();
const byId = (id) => page.locator(`[id="${id}"]`);
const inputById = (id) => page.locator(`[id="${id}"] input, [id="${id}"] textarea`);
const button = (text) =>
  page.locator('vaadin-button').filter({ hasText: text }).first();

const pause = (ms) => page.waitForTimeout(ms);

async function shot(name, options = {}) {
  if (!FIGURES.includes(name)) {
    throw new Error(`${name} is not one of the manual's figures`);
  }
  await pause(700);
  await page.screenshot({ path: path.join(OUT, `${name}.png`), ...options });
  captured.add(name);
  console.log('  ✓', name);
}

/** Records a figure the running stack's state cannot produce, with the reason. */
function skip(name, why) {
  skipped.push({ name, why });
  console.log('  –', name, '—', why);
}

async function goto(pathname) {
  const url = pathname.startsWith('http') ? pathname : `${BASE}${pathname}`;
  try {
    await page.goto(url, { waitUntil: 'networkidle' });
  } catch {
    // Vaadin's push connection can keep the network busy; the load itself is what matters.
    await page.goto(url, { waitUntil: 'domcontentloaded' });
  }
  await pause(1800);
}

/**
 * Fills a field and tabs out of it.
 *
 * A Vaadin text field only sends its value to the server on blur or Enter, and the view's Binder
 * validates on that server-side value change — the save button stays disabled until it does. A
 * bare fill() sets the DOM value and returns, which races the round trip.
 */
async function fill(label, value) {
  const input = field(label);
  await input.fill(value);
  await input.press('Tab');
  await pause(250);
}

/** Picks a value in a single-select ComboBox or Select located by its label. */
async function combo(label, value) {
  const input = host(label).locator('input').first();
  await input.click();
  await pause(400);
  const item = page.locator('vaadin-combo-box-item, vaadin-select-item, vaadin-item')
    .filter({ hasText: value }).first();
  await item.waitFor();
  await item.click();
  await pause(500);
}

/**
 * Adds a value to a MultiSelectComboBox located by its label. Picking an overlay item and then
 * pressing Escape leaves the selection in place and closes the overlay.
 */
async function multiCombo(label, value) {
  const box = host(label);
  const input = box.locator('input').first();
  await input.click();
  await pause(400);
  const item = page.locator('vaadin-multi-select-combo-box-item').filter({ hasText: value }).first();
  await item.waitFor();
  await item.click({ force: true });
  await input.press('Escape');
  await pause(400);
}

/** Whether a grid on the current screen shows a cell reading exactly this. */
async function gridHas(text, gridSelector = 'vaadin-grid') {
  const cells = page.locator(`${gridSelector} vaadin-grid-cell-content:visible`);
  const count = await cells.count();
  for (let i = 0; i < count; i++) {
    if ((await cells.nth(i).innerText()).trim() === text) return true;
  }
  return false;
}

/**
 * SideNav sections ("Admin", "System") are collapsed until opened; the navigation figure has to
 * show what the console offers, so both are expanded before the shutter.
 */
async function expandNav(label) {
  const item = page.locator('vaadin-side-nav-item').filter({ hasText: label }).first();
  if (!(await item.count())) return;
  if ((await item.getAttribute('expanded')) !== null) return;
  const toggle = item.locator('[part="toggle-button"]').first();
  if (await toggle.count()) {
    await toggle.click();
  } else {
    await item.click();
  }
  await pause(600);
}

/**
 * Index of every SearchView grid row that holds real data.
 *
 * Grid cell content is numbered flat and row-major: block 0 is the header, and each later block
 * of GRID_COLUMNS is one virtualised row slot. Vaadin renders enough slots to fill the viewport
 * even when there are fewer results and un-slots the unused ones rather than removing them, so
 * the query filters to :visible and the access-code cell decides whether a block is real.
 */
async function resultRows() {
  const cells = page.locator('#subject-grid vaadin-grid-cell-content:visible');
  const blocks = Math.floor((await cells.count()) / GRID_COLUMNS);
  const rows = [];
  for (let block = 1; block < blocks; block++) {
    const accessCode = (await cells.nth(block * GRID_COLUMNS + COL_ACCESS_CODE).innerText()).trim();
    if (accessCode) {
      const status = (await cells.nth(block * GRID_COLUMNS + COL_STATUS).innerText()).trim();
      rows.push({ block, accessCode, status });
    }
  }
  return rows;
}

/**
 * Chooses a row action in the grid. Selecting one reveals that row's Submit button, which is
 * what the figure shows; nothing is submitted, so no email is sent and no report is generated.
 */
async function chooseRowAction(block, action) {
  const cell = page.locator('#subject-grid vaadin-grid-cell-content:visible')
    .nth(block * GRID_COLUMNS + COL_ACTION);
  await cell.locator('vaadin-combo-box input').click();
  await pause(400);
  const item = page.locator('vaadin-combo-box-item').filter({ hasText: action }).first();
  await item.waitFor();
  await item.click();
  await pause(900);
}

/** Clears every SearchView text filter and searches again. */
async function searchAll() {
  for (const id of ['access-code-filter', 'first-name-filter', 'last-name-filter',
    'email-filter', 'phone-filter']) {
    await inputById(id).fill('');
  }
  await pause(300);
  await byId('search-button').click();
  await pause(1500);
}

// ---------------------------------------------------------------------------------------------
// The walk
// ---------------------------------------------------------------------------------------------

/**
 * UC-001: the identity provider's own sign-in page, then the console.
 *
 * Admin bounces through its own /login route before Keycloak, so the first URL seen is not
 * necessarily the one to photograph — wait for the redirect to settle. Keycloak's login page
 * lives outside both apps' DOM, so it is driven by its default theme's element ids.
 */
async function signIn() {
  await page.goto(BASE, { waitUntil: 'domcontentloaded' });
  for (let i = 0; i < 30 && !page.url().includes('/protocol/openid-connect/auth'); i++) {
    await pause(500);
  }
  if (page.url().includes('/protocol/openid-connect/auth')) {
    await shot('02-sign-in');
    await page.fill('#username', USER);
    await page.fill('#password', PASS);
    await pause(150);
    await page.click('#kc-login');
    await page.waitForLoadState('domcontentloaded');
  } else {
    skip('02-sign-in', 'the sign-in page never appeared; the browser was already signed in');
  }
  // The console is a Vaadin application: the first load fetches its bundle before any view or
  // dialog exists to be looked for.
  await page.locator('vaadin-app-layout').first().waitFor({ timeout: 60_000 }).catch(() => {});
  await pause(3000);
}

/**
 * UC-028 / UC-006. The blocking notice is captured before it is cleared, then the department is
 * created — which is also figure 05, because the create form is what chapter 3 describes.
 *
 * The dialog is found by a button in its footer, not by its id: Vaadin renders a Dialog's
 * contents into an overlay element, so setId() lands on the hidden host and is not what the page
 * shows.
 */
async function ensureDepartment() {
  const blocked = await byId('missing-department-logout').first().isVisible().catch(() => false);
  if (blocked) {
    await shot('03-department-required');
    await byId('missing-department-add').click();
    await page.waitForURL((u) => u.pathname.endsWith('/departments'));
    await pause(1500);
  } else {
    skip('03-department-required', 'this account already has a department, so the notice cannot appear');
    await goto('/departments');
  }

  const exists = await gridHas(DEPARTMENT.name);
  await goto('/edit-department/0');
  await fill('Department name', DEPARTMENT.name);
  await fill('Department code', DEPARTMENT.code);
  if (!(await field('Default message ID').inputValue())) {
    await fill('Default message ID', DEPARTMENT.defaultMessageId);
  }
  await fill('From email', DEPARTMENT.fromEmail);
  await shot('05-edit-department');

  if (exists) {
    console.log('    (the department is already there — the form above was not saved)');
    return;
  }
  const save = button('Create department');
  await save.waitFor();
  await save.click();
  await page.waitForURL((u) => u.pathname.endsWith('/departments'));
  // A full reload, not a router navigation: the signed-in user's departments are read once per
  // UI, so every later screen needs a UI built after the department existed.
  await goto('/departments');
}

/** UC-019: the banner a deployment carries until a survey definition is applied. */
async function captureMissingSurvey() {
  await goto('/');
  if (await byId('missing-survey-banner').first().isVisible().catch(() => false)) {
    await shot('21-missing-survey-notice');
    return;
  }
  skip('21-missing-survey-notice', 'a survey is already installed, so the notice cannot appear');
}

/** UC-018 and the reporting rebuild of FR-027. Re-applying an identical revision is allowed. */
async function applySurveyDefinition() {
  if (!existsSync(DEFINITION)) {
    skip('18-apply-survey-definition', `no definition file at ${DEFINITION}`);
    skip('19-apply-result', `no definition file at ${DEFINITION}`);
    return;
  }
  await goto('/survey-apply');
  await shot('18-apply-survey-definition');
  await page.locator('#survey-apply-upload input[type=file]').setInputFiles(DEFINITION);

  // The apply rebuilds the reporting star schema, so give it room. The dialog host renders with
  // display: contents and is never "visible" to Playwright — wait for attachment.
  const dialog = page.locator('vaadin-dialog[opened]');
  await dialog.waitFor({ state: 'attached', timeout: 180_000 });
  await pause(1200);
  await shot('19-apply-result');
  await dialog.locator('vaadin-button').filter({ hasText: 'Close' }).first().click();
  await pause(1200);
}

/** Chapter 1: what the console offers, with both administrator sections open. */
async function captureNavigation() {
  await goto('/');
  const drawerShut = !(await page.locator('vaadin-side-nav').first().isVisible().catch(() => false));
  if (drawerShut) {
    await page.locator('vaadin-drawer-toggle').first().click();
    await pause(800);
  }
  await expandNav('Admin');
  await expandNav('System');
  await shot('01-navigation');
}

/** UC-008 / UC-016: the console's accounts, and the form that assigns a department and a role. */
async function ensureUsers() {
  await goto('/users');
  await shot('06-users');

  for (const [index, account] of ACCOUNTS.entries()) {
    const exists = await gridHas(account.username);
    await goto('/edit-user/0');
    await fill('Username', account.username);
    await fill('First name', account.firstName);
    await fill('Last name', account.lastName);
    const active = host('Active');
    // The native <input> inside vaadin-checkbox is visually hidden, so force the click past
    // Playwright's actionability check rather than clicking the host and hoping.
    if (!(await active.evaluate((el) => el.checked))) {
      await active.locator('input').first().click({ force: true });
      await pause(300);
    }
    await multiCombo('Departments', DEPARTMENT.name);
    // The Database role assignment section only exists when elicit.authorization.mode=DATABASE;
    // under the compose stack's default (OIDC) roles come from the identity provider instead.
    if (await host('Role').isVisible().catch(() => false)) {
      await combo('Role', account.role);
    }
    // One figure is enough for chapter 4, and the first account is the administrator's.
    if (index === 0) {
      await shot('07-edit-user');
    }
    if (exists) {
      console.log(`    (${account.username} is already there — the form above was not saved)`);
      continue;
    }
    await button('Save').click();
    await page.waitForURL((u) => u.pathname.endsWith('/users'), { timeout: 30_000 }).catch(() => {});
    await pause(1200);
  }

  // UC-021: the seeded accounts are still named admin and user, so the warning stands above the
  // very grid it is asking to be changed.
  await goto('/users');
  if (await byId('default-account-banner').first().isVisible().catch(() => false)) {
    await shot('28-default-account-warning');
  } else {
    skip('28-default-account-warning', 'the seeded default accounts have been renamed already');
  }
}

/** UC-007: the invitation the console sends, and the <ACCESS_CODE> placeholder it carries. */
async function ensureMessageTemplate() {
  await goto('/message-templates');
  const exists = await gridHas(TEMPLATE.subject);

  await goto('/edit-message-template/0');
  await combo('MIME type', TEMPLATE.mimeType);
  await combo('Department', DEPARTMENT.name);
  await fill('Subject', TEMPLATE.subject);
  await field('Body').fill(TEMPLATE.body);
  await field('Body').press('Tab');
  // The preview column is debounced by 300 ms; let it catch up before the shutter.
  await pause(900);
  await shot('09-edit-message-template');

  if (!exists) {
    await button('Save').click();
    await page.waitForURL((u) => u.pathname.endsWith('/message-templates'), { timeout: 30_000 })
      .catch(() => {});
    await pause(1200);
  } else {
    console.log('    (the template is already there — the form above was not saved)');
  }

  await goto('/message-templates');
  await shot('08-message-templates');
}

/** UC-003 and the access code it issues (UC-015). */
async function ensureSubject() {
  // Is this subject already registered? The search is the only place that answers.
  await goto('/');
  await inputById('last-name-filter').fill(SUBJECT.lastName);
  await pause(300);
  await byId('search-button').click();
  await pause(1500);
  const exists = (await resultRows()).length > 0;

  await goto('/register');
  if (await host('Survey').isVisible().catch(() => false)) {
    await combo('Survey', SURVEY);
  }
  if (!(await host('Department').locator('input').first().inputValue())) {
    await combo('Department', DEPARTMENT.name);
  }
  await fill('First name', SUBJECT.firstName);
  await fill('Last name', SUBJECT.lastName);
  await fill('Email', SUBJECT.email);
  await fill('Phone', SUBJECT.phone);
  await shot('10-register-subject');

  if (exists) {
    console.log('    (the subject is already registered — the form above was not saved)');
    skip('11-register-result', 'the subject is already registered, so no confirmation is raised');
    return;
  }
  await pause(300);
  await byId('register-save-button').click();
  // The confirmation is a notification that clears itself after three seconds.
  await page.locator('vaadin-notification-card').filter({ hasText: 'Subject saved' }).first()
    .waitFor({ timeout: 30_000 });
  await shot('11-register-result');
}

/** UC-002, UC-004, UC-005, UC-011: finding a subject and the actions offered on its row. */
async function captureSearch() {
  await goto('/');
  await inputById('last-name-filter').fill(SUBJECT.lastName);
  await pause(400);
  await shot('13-search-filters');

  await byId('search-button').click();
  await pause(1500);
  await shot('14-search-results');

  const rows = await resultRows();
  if (!rows.length) {
    skip('12-search-email-action', `no subject named ${SUBJECT.lastName} in the results`);
    skip('16-respondent-export', `no subject named ${SUBJECT.lastName} in the results`);
  } else {
    await chooseRowAction(rows[0].block, 'Send email');
    await shot('12-search-email-action');

    await chooseRowAction(rows[0].block, 'Export');
    await shot('16-respondent-export');
  }

  // UC-005: Print reports is offered only on a respondent who has finished the survey, so this
  // figure needs one. Nothing the console can do produces it — the answers are given in the
  // Survey app — so when there is none the committed figure is left alone.
  await searchAll();
  const finished = (await resultRows()).find((row) => row.status === 'Finished');
  if (finished) {
    await chooseRowAction(finished.block, 'Print reports');
    await shot('15-report-download');
  } else {
    skip('15-report-download',
      'no respondent has finished the survey; complete one at http://localhost:8080 with an '
      + 'issued access code and re-run');
  }
}

/**
 * UC-005 needs a respondent who has finished the survey, and no console action produces one —
 * the answers are given in the Survey app. The respondent fixtures are three who finished the
 * fixture survey elsewhere, so importing them gives figure 15 its row. Their subjects carry
 * DEPARTMENT.code, which the import matches by code and never creates (BR-102), so they land in
 * the department this walk made. Nothing is imported when the deployment already has a finished
 * respondent, and an import the installed survey rejects is reported rather than fatal.
 */
async function ensureFinishedRespondents() {
  await goto('/');
  await searchAll();
  if ((await resultRows()).some((row) => row.status === 'Finished')) {
    console.log('    (a finished respondent is already there — nothing imported)');
    return;
  }
  for (const file of RESPONDENT_FIXTURES) {
    if (!existsSync(file)) continue;
    await goto('/respondent-import');
    await pause(1200);
    await page.locator('#respondent-import-upload input[type=file]').setInputFiles(file);
    await pause(3500);
    const overlay = page.locator('vaadin-dialog-overlay').first();
    const message = await overlay.innerText().catch(() => '');
    if (/error|fail|no department|already exists/i.test(message)) {
      console.log(`    (${path.basename(file)} was not imported: ${message.replace(/\s+/g, ' ').trim()})`);
    }
    const close = overlay.locator('vaadin-button').filter({ hasText: 'Close' }).first();
    if (await close.isVisible().catch(() => false)) await close.click();
    await pause(600);
  }
}

/** UC-012 and UC-013: moving a respondent in, and taking a definition out. */
async function captureTransfers() {
  await goto('/respondent-import');
  await shot('17-respondent-import');

  await goto('/survey-export');
  await shot('20-export-survey-definition');
}

/** UC-020 to UC-025: the System screens. Nothing here edits configuration (C-012). */
async function captureSystem() {
  // Stacked grids that run past the fold, so these are shot whole rather than to the viewport.
  await goto('/system');
  await shot('22-system-overview', { fullPage: true });

  await goto('/system/database');
  await shot('23-system-database', { fullPage: true });

  await goto('/system/branding');
  await shot('24-system-branding', { fullPage: true });

  await goto('/system/email');
  await shot('25-system-email', { fullPage: true });

  await goto('/system/connections');
  const checkAll = byId('system-connections-check-all');
  if (await checkAll.first().isVisible().catch(() => false)) {
    // Each check is one read-only request with its own timeout; post-survey actions are never
    // invoked and no report is generated.
    await checkAll.click();
    await pause(12_000);
  }
  await shot('26-system-connections', { fullPage: true });

  await goto('/oidc');
  await shot('27-system-oidc');
}

/** UC-026: choosing a language for your own session. Making one available is the installer's job. */
async function captureLanguageSelector() {
  await goto('/');
  const selector = byId('language-switcher');
  if (!(await selector.first().isVisible().catch(() => false))) {
    skip('29-language-selector',
      'only English is available, so the selector stays out of the header; mount elicit-i18n '
      + 'and re-run');
    return;
  }
  await selector.click();
  // The overlay's items are rendered after it opens; at 900 ms the figure caught them blank.
  await pause(2500);
  await shot('29-language-selector');
  // Escape, never a selection: choosing a language reloads the console in it.
  await page.keyboard.press('Escape');
  await pause(600);
}

async function main() {
  await mkdir(OUT, { recursive: true });
  const browser = await chromium.launch();
  const context = await browser.newContext({
    viewport: { width: 1440, height: 900 },
    deviceScaleFactor: 2,
  });
  page = await context.newPage();

  try {
    await signIn();
    await ensureDepartment();
    await captureMissingSurvey();
    await applySurveyDefinition();

    await captureNavigation();
    await goto('/departments');
    await shot('04-departments');
    await ensureUsers();
    await ensureMessageTemplate();
    await ensureSubject();
    await ensureFinishedRespondents();
    await captureSearch();
    await captureTransfers();
    await captureSystem();
    await captureLanguageSelector();
  } finally {
    await browser.close();
  }

  console.log(`\n${captured.size} of ${FIGURES.length} figures written to ${OUT}`);
  const missing = FIGURES.filter((name) => !captured.has(name));
  if (missing.length) {
    console.log('\nNot captured on this run — the committed figure is unchanged:');
    for (const name of missing) {
      const reason = skipped.find((s) => s.name === name);
      console.log(`  ${name}${reason ? ` — ${reason.why}` : ''}`);
    }
  }
  console.log(
    '\nCheck every \\screenshot{…} caption in elicit-admin-manual.tex still matches what the '
    + 'figure shows: the caption is part of the instruction (UC-029 BR-002).',
  );
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
