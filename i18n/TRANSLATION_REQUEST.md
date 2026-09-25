# Translation request: Elicit Admin

This document is generated from the application's English text file and is meant to be handed,
as is, to a translator or to an AI translation agent. It contains everything needed to produce a
complete language file for one target language. The application itself ships only the English
file; a finished translation is placed in the deployment's translations directory
(`elicit-i18n/admin/` in the Elicit umbrella repository, mounted at `/opt/i18n`), never inside
the application.

## About the application

Elicit Admin is the administration console of the Elicit survey platform. Survey administrators
and clinical or research staff use it to register subjects, generate and send survey invitation
access codes, track each subject's progress, download reports, manage departments, message
templates and user accounts, and install survey definitions. The texts below are the console's
own words: navigation items, buttons, grid column headers, form labels, dialogs, notifications
and short help texts. Data entered by staff (names, departments, template bodies) and the survey
definitions themselves are not included and stay as entered.

**Audience and tone:** professional staff in a healthcare or research setting. Use clear,
concise, gender-neutral language and the formal register where the language distinguishes one
(for example *usted* in Spanish). Keep button labels short.

## Glossary and words to keep

| Term | Meaning | Rule |
|------|---------|------|
| Elicit | Product name | Never translate or transliterate |
| access code | The credential a subject types to open their survey | Translate consistently; never call it a "token" or "password" |
| subject / respondent | The person invited to take a survey (subject before, respondent once they answer) | Keep the distinction |
| department | Organizational unit that owns subjects and templates | Translate consistently |
| message template | Stored invitation or reminder email text | Translate consistently |
| survey definition | The authored survey installed into this console (the `.elicit` file) | Translate consistently; "apply" installs or updates it |
| role, administrator, user | Access levels | Translate consistently |
| PDF, CSV, JSON, HTTP, OIDC, API, ID, URL | Technical names | Keep as written |

## Rules for the translation

1. Keep every placeholder such as `{0}`, `{1}` exactly as written; move it inside the sentence
   where the language needs it, but never remove, rename or reorder it with another placeholder.
2. Where a row is flagged **params**, the value is processed by Java MessageFormat: any apostrophe
   in your translation must be doubled (`l''accès`). Rows without the flag may use a single
   apostrophe normally.
3. Where a row is flagged **html**, keep the HTML tags and translate only the text between them.
4. Respect the *Max length* column where given; these strings sit in buttons, menus and grid headers.
5. Do not translate the keys (the first column). Do not add, remove or reorder keys.
6. For right-to-left languages, write the text naturally; the console mirrors the layout.
7. Return exactly one file named `translations_<tag>.properties` (for example
   `translations_es_419.properties`, `translations_ar.properties`), UTF-8 encoded, with the same
   keys in the same order as the English file at the end of this document, one `key=translation`
   per line, and a first line `# Reviewed by <name or agent>, <date>`.

## Brand strings

Deployments mount their own brand. The organization name shown in the header comes from the
brand's own files and is translated there, not in this file, through a `localized` block keyed
by language tag in `brand-config.json` (`name`, `organization`) and `brand-info.json`
(`description`):

```json
{
  "name": "Healthcare Test Brand",
  "organization": "Healthcare Test Organization",
  "localized": {
    "es-419": { "name": "Marca de prueba de salud", "organization": "Organización de prueba de salud" },
    "ar": { "name": "علامة الرعاية الصحية التجريبية", "organization": "مؤسسة الرعاية الصحية التجريبية" }
  }
}
```

The base `name` also derives a technical identifier and must stay as it is.

## Strings to translate

Every row is one key in `translations.properties`. Return a file `translations_<tag>.properties` with exactly these keys in this order, one `key=translation` per line, UTF-8, no additions and no omissions.

| Key | English | Where it appears | Max length | Notes |
|-----|---------|------------------|------------|-------|
| `common.language` | Language | Header · language selector accessible name | 20 |  |
| `common.logoAlt` | {0} logo | Header · logo image alternative text | 40 | params: {0} = organization name |
| `common.appTitle` | {0} {1} | Header · application title | 40 | params: {0} = organization name, {1} = application type; identical in most languages (reorder only) |
| `common.appTitle.default` | Elicit {0} | Header · application title when no brand is mounted | 40 | params: {0} = application type; identical in most languages, Elicit stays |
| `common.appType.admin` | Admin | Header · the word appended to the organization name | 20 |  |
| `common.save` | Save | Any form · button | 15 |  |
| `common.cancel` | Cancel | Any form · button | 15 |  |
| `common.close` | Close | Any dialog · button | 15 |  |
| `common.edit` | Edit | Any grid · button | 15 |  |
| `common.search` | Search | Search screens · button | 15 |  |
| `common.yes` | Yes | Confirmation dialogs · button | 10 |  |
| `common.no` | No | Confirmation dialogs · button | 10 | identical |
| `registerView.pageTitle` | Register subjects | Register subjects page · browser tab title | 30 |  |
| `registerView.firstName` | First name | Register subjects page · form field label | 25 |  |
| `registerView.lastName` | Last name | Register subjects page · form field label | 25 |  |
| `registerView.middleName` | Middle name | Register subjects page · form field label | 25 |  |
| `registerView.dob` | Date of birth | Register subjects page · date picker label | 25 |  |
| `registerView.datePicker.today` | Today | Register subjects page · date picker "today" button | 10 |  |
| `registerView.email` | Email | Register subjects page · form field label | 25 |  |
| `registerView.phone` | Phone | Register subjects page · form field label | 25 |  |
| `registerView.xid` | External ID | Register subjects page · form field label; the caller's own identifier for the subject | 25 |  |
| `registerView.error.departmentRequired` | Department is required | Register subjects page · field error | 60 |  |
| `registerView.error.surveyRequired` | Survey is required | Register subjects page · field error | 60 |  |
| `registerView.error.firstNameRequired` | First name is required | Register subjects page · field error | 60 |  |
| `registerView.error.lastNameRequired` | Last name is required | Register subjects page · field error | 60 |  |
| `registerView.error.dobPast` | Date of birth must be in the past | Register subjects page · field error | 60 |  |
| `registerView.error.emailInvalid` | Enter a valid email address | Register subjects page · field error | 60 |  |
| `registerView.error.phoneFormat` | Phone must be ###-###-#### | Register subjects page · field error; keep ###-###-#### | 60 |  |
| `registerView.error.duplicateXid` | Duplicate entry: a subject with the external ID {0} already exists for this department. | Register subjects page · notification | 120 | params: {0} = external ID |
| `registerView.error.databaseTitle` | Database error | Register subjects page · error dialog title | 30 |  |
| `registerView.error.database` | Database error: {0} | Register subjects page · error dialog text | 120 | params: {0} = technical error text |
| `registerView.btnUpdate` | Update subject | Register subjects page · primary button when editing | 20 |  |
| `registerView.btnUploadCsv` | Upload CSV | Register subjects page · upload button | 20 |  |
| `registerView.upload.dropFile` | Drop a CSV file here | Register subjects page · upload drop-zone hint | 40 |  |
| `registerView.upload.error.tooBig` | The file is too big (5 MB maximum) | Register subjects page · upload error | 60 |  |
| `registerView.upload.error.wrongType` | Only .csv files can be uploaded | Register subjects page · upload error | 60 |  |
| `registerView.upload.error.tooMany` | Upload one file at a time | Register subjects page · upload error | 60 |  |
| `registerView.csvImport.successTitle` | CSV import succeeded | Register subjects page · success dialog title | 30 |  |
| `registerView.csvImport.success` | Successfully imported subjects:  {0} | Register subjects page · success dialog text | 120 | params: {0} = multi-line import summary |
| `registerView.csvImport.errorTitle` | CSV import error | Register subjects page · error dialog title | 30 |  |
| `registerView.apiDoc.title` | REST API instructions | Register subjects page · collapsible section title | 40 |  |
| `registerView.error.subjectNotFound` | Subject not found for access code: {0} | Register subjects page · notification | 80 | params: {0} = access code |
| `registerView.unknownDepartment` | Unknown | Register subjects page · department name fallback | 20 |  |
| `registerView.error.excludedXid` | External ID {0} is in the exclude list for department {1} | Register subjects page · notification | 100 | params: {0} = external ID, {1} = department name |
| `registerView.subjectSaved` | Subject saved | Register subjects page · notification | 30 |  |
| `registerView.error.fixValidation` | Please fix the validation errors | Register subjects page · notification | 60 |  |
| `registerView.error.accessCode` | Error generating a new access code. Please try again | Register subjects page · notification | 80 |  |
| `registerView.subjectUpdated` | Subject updated | Register subjects page · notification | 30 |  |
| `registerView.survey` | Survey | Register subjects page · survey selector label | 20 |  |
| `registerView.department` | Department | Register subjects page · department selector label | 20 |  |
| `registerView.csvDoc.title` | CSV file structure | Register subjects page · collapsible section title | 40 |  |
| `registerView.intro.methods` | You can register subjects individually using the form, a REST API, or by uploading a CSV file with multiple subjects. | Register subjects page · paragraph | 160 |  |
| `registerView.intro.clickBelow` | Click below to see the required CSV file format and examples: | Register subjects page · paragraph | 80 |  |
| `registerView.csvDoc.columnsIntro` | The CSV file should contain the following columns in order: | Register subjects page · paragraph (also used in the API instructions) | 80 |  |
| `registerView.csvDoc.comments` | All rows starting with a '#' character are treated as comments and ignored. | Register subjects page · paragraph; keep the '#' character | 100 |  |
| `registerView.csvDoc.columnDescriptions` | Column descriptions: | Register subjects page · heading | 30 |  |
| `registerView.csvDoc.example` | Example CSV data: | Register subjects page · heading | 30 |  |
| `registerView.csvDoc.col.departmentId` | Integer (required) - must be a valid department ID for the user | Register subjects page · CSV column description; column names stay in English | 80 |  |
| `registerView.csvDoc.col.firstName` | String (required) - the subject's first name | Register subjects page · CSV column description | 60 |  |
| `registerView.csvDoc.col.lastName` | String (required) - the subject's last name | Register subjects page · CSV column description | 60 |  |
| `registerView.csvDoc.col.middleName` | String (optional) - the subject's middle name | Register subjects page · CSV column description | 60 |  |
| `registerView.csvDoc.col.dob` | Date (optional) - date of birth in yyyy-MM-dd or MM/dd/yyyy format | Register subjects page · CSV column description; keep the date patterns | 80 |  |
| `registerView.csvDoc.col.email` | String (required) - a valid email address | Register subjects page · CSV column description | 60 |  |
| `registerView.csvDoc.col.phone` | String (optional) - phone number in ###-###-#### format | Register subjects page · CSV column description; keep ###-###-#### | 60 |  |
| `registerView.csvDoc.col.xid` | String (optional) - external ID for the subject | Register subjects page · CSV column description | 60 |  |
| `registerView.apiDoc.intro` | You can also add subjects programmatically using the REST API endpoints. | Register subjects page (API instructions) · paragraph | 100 |  |
| `registerView.apiDoc.single.h` | 1. Single subject registration | Register subjects page (API instructions) · heading | 40 |  |
| `registerView.apiDoc.endpoint` | Endpoint: | Register subjects page (API instructions) · heading | 20 | identical |
| `registerView.apiDoc.authentication` | Authentication: | Register subjects page (API instructions) · heading | 20 |  |
| `registerView.apiDoc.auth.anyRole` | Requires a Bearer token with the elicit_admin, elicit_user or elicit_importer role | Register subjects page (API instructions) · paragraph; role names and "Bearer" stay as written | 100 |  |
| `registerView.apiDoc.contentType` | Content-Type: | Register subjects page (API instructions) · heading; HTTP header name | 20 | identical |
| `registerView.apiDoc.requestBodyExample` | Request body example: | Register subjects page (API instructions) · heading | 40 |  |
| `registerView.apiDoc.bulk.h` | 2. Bulk subject registration | Register subjects page (API instructions) · heading | 40 |  |
| `registerView.apiDoc.requestBodyArray` | Request body example (array of subjects): | Register subjects page (API instructions) · heading | 60 |  |
| `registerView.apiDoc.csvUpload.h` | 3. CSV file upload (REST API) | Register subjects page (API instructions) · heading | 40 |  |
| `registerView.apiDoc.auth.importerRole` | Requires a Bearer token with the elicit_importer role | Register subjects page (API instructions) · paragraph; role name and "Bearer" stay as written | 80 |  |
| `registerView.apiDoc.requestBody` | Request body: | Register subjects page (API instructions) · heading | 20 |  |
| `registerView.apiDoc.requestBodyFile` | Form field 'file' containing a CSV file with subject data | Register subjects page (API instructions) · paragraph; 'file' is the field name | 80 |  |
| `registerView.apiDoc.csvFormat` | CSV file format: | Register subjects page (API instructions) · heading | 30 |  |
| `registerView.apiDoc.columnRequirements` | Column requirements: | Register subjects page (API instructions) · paragraph | 30 |  |
| `registerView.apiDoc.columnRequirementsList` | • departmentId: Integer (required) - valid department ID • firstName: String (required) - the subject's first name • lastName: String (required) - the subject's last name • middleName: String (optional) - the subject's middle name • dob: Date (optional) - format yyyy-MM-dd or MM/dd/yyyy • email: String (required) - a valid email address • phone: String (optional) - format ###-###-#### • xid: String (optional) - external ID for the subject | Register subjects page (API instructions) · preformatted list, one column per line; keep column names, • and | 600 |  |
| `registerView.apiDoc.csvExample` | CSV example: | Register subjects page (API instructions) · heading | 30 |  |
| `registerView.apiDoc.webUpload.h` | 4. CSV file upload (web interface) | Register subjects page (API instructions) · heading | 40 |  |
| `registerView.apiDoc.webUpload.p` | You can also upload a CSV file using the upload component above in the web interface. | Register subjects page (API instructions) · paragraph | 100 |  |
| `registerView.apiDoc.response.h` | Response format (all endpoints) | Register subjects page (API instructions) · heading | 40 |  |
| `registerView.apiDoc.response.single` | Response example (single subject): | Register subjects page (API instructions) · heading | 40 |  |
| `registerView.apiDoc.response.bulk` | Response example (bulk subjects): | Register subjects page (API instructions) · heading | 40 |  |
| `registerView.apiDoc.notes.h` | Important notes: | Register subjects page (API instructions) · heading | 30 |  |
| `registerView.apiDoc.notes.exclusion` | • XID exclusion: subjects with XIDs in the exclusion list are not created | Register subjects page (API instructions) · bullet paragraph; keep • and XID | 120 |  |
| `registerView.apiDoc.notes.duplicates` | • Duplicate detection: existing subjects (same XID and department) are identified | Register subjects page (API instructions) · bullet paragraph; keep • and XID | 120 |  |
| `registerView.apiDoc.notes.individual` | • Individual processing: in bulk requests, each subject is processed independently | Register subjects page (API instructions) · bullet paragraph; keep • | 120 |  |
| `registerView.apiDoc.notes.auth` | • Authentication: all endpoints require valid Bearer token authentication | Register subjects page (API instructions) · bullet paragraph; keep • and "Bearer" | 120 |  |
| `searchView.pageTitle` | Subject search | Subject search · browser tab title | 30 |  |
| `searchView.heading` | Subject search | Subject search · heading | 40 |  |
| `searchView.noUser.loggedIn` | You have successfully signed in with the identity provider. | Subject search (no matching user) · paragraph | 100 |  |
| `searchView.noUser.before` | Unfortunately, there is no user named | Subject search (no matching user) · text before the user name | 60 |  |
| `searchView.noUser.after` | in the application, or the user is inactive. | Subject search (no matching user) · text after the user name | 60 |  |
| `searchView.noUser.help` | Please ask an Elicit administrator for help. | Subject search (no matching user) · paragraph | 60 |  |
| `searchView.filter.departments` | Department(s) | Subject search · department filter label | 20 |  |
| `searchView.filter.allDepartments` | All departments | Subject search · department filter option selecting every department | 30 |  |
| `searchView.filter.accessCode` | Access code | Subject search · filter field label | 20 |  |
| `searchView.filter.firstName` | First name | Subject search · filter field label | 20 |  |
| `searchView.filter.lastName` | Last name | Subject search · filter field label | 20 |  |
| `searchView.filter.email` | Email | Subject search · filter field label | 20 |  |
| `searchView.filter.phone` | Phone | Subject search · filter field label | 20 |  |
| `searchView.grid.accessCode` | Access code | Subject search · grid column header | 20 |  |
| `searchView.grid.department` | Department | Subject search · grid column header | 20 |  |
| `searchView.grid.firstName` | First name | Subject search · grid column header | 20 |  |
| `searchView.grid.middleName` | Middle name | Subject search · grid column header | 20 |  |
| `searchView.grid.lastName` | Last name | Subject search · grid column header | 20 |  |
| `searchView.grid.email` | Email | Subject search · grid column header | 20 |  |
| `searchView.grid.phone` | Phone | Subject search · grid column header | 20 |  |
| `searchView.grid.created` | Created | Subject search · grid column header (creation date) | 20 |  |
| `searchView.grid.status` | Status | Subject search · grid column header (survey status) | 20 |  |
| `searchView.grid.action` | Action | Subject search · grid column header | 20 |  |
| `searchView.action.placeholder` | Select action | Subject search · action dropdown placeholder | 20 |  |
| `searchView.action.btnSubmit` | Submit | Subject search · button that runs the chosen action | 15 |  |
| `searchView.action.sendEmail` | Send email | Subject search · action option | 20 | dynamic: key built as searchView.action.<action> |
| `searchView.action.printReports` | Print reports | Subject search · action option | 20 | dynamic: key built as searchView.action.<action> |
| `searchView.action.export` | Export | Subject search · action option | 20 | dynamic: key built as searchView.action.<action> |
| `searchView.action.emailSent` | Email sent successfully | Subject search · notification | 60 |  |
| `searchView.action.emailFailed` | Failed to send the email. Check the server logs for details. | Subject search · notification | 100 |  |
| `searchView.action.emailError` | Failed to send the email: {0} | Subject search · notification | 100 | params: {0} = technical error text |
| `searchView.action.reportsGenerated` | Reports generated successfully | Subject search · notification | 60 |  |
| `searchView.action.reportsError` | Failed to generate the reports: {0} | Subject search · notification | 100 | params: {0} = technical error text |
| `searchView.action.exportDownloading` | Export downloading... | Subject search · notification | 40 |  |
| `searchView.error.selectDepartments` | Please select one or more departments | Subject search · notification | 60 |  |
| `editDepartmentView.name` | Department name | Edit department · field label | 30 |  |
| `editDepartmentView.code` | Department code | Edit department · field label | 30 |  |
| `editDepartmentView.code.helper` | Short abbreviation for the department (e.g. CARD, HR) | Edit department · field helper text | 80 |  |
| `editDepartmentView.defaultMessageId` | Default message ID | Edit department · field label | 30 |  |
| `editDepartmentView.defaultMessageId.helper` | ID of an existing message template, sent on registration | Edit department · field helper text | 60 |  |
| `editDepartmentView.fromEmail` | From email | Edit department · field label | 30 |  |
| `editDepartmentView.fromEmail.helper` | Email address that appears as the sender of department communications | Edit department · field helper text | 100 |  |
| `editDepartmentView.btnCreate` | Create department | Edit department · primary button in create mode | 25 |  |
| `editDepartmentView.btnUpdate` | Update department | Edit department · primary button in edit mode | 25 |  |
| `editDepartmentView.title.create` | Create new department | Edit department · browser tab title in create mode | 40 |  |
| `editDepartmentView.title.edit` | Edit department: {0} | Edit department · browser tab title in edit mode | 40 | params: {0} = department name |
| `editDepartmentView.created` | Department created successfully | Edit department · notification | 60 |  |
| `editDepartmentView.updated` | Department updated successfully | Edit department · notification | 60 |  |
| `editDepartmentView.error.nameRequired` | Department name is required | Edit department · field error | 60 |  |
| `editDepartmentView.error.nameLength` | Department name must be 1-255 characters | Edit department · field error | 60 |  |
| `editDepartmentView.error.codeLength` | Department code must be 100 characters or less | Edit department · field error | 60 |  |
| `editDepartmentView.error.defaultMessageIdRequired` | Default message ID is required | Edit department · field error | 60 |  |
| `editDepartmentView.error.defaultMessageIdLength` | Default message ID must be 1-100 characters | Edit department · field error | 60 |  |
| `editDepartmentView.error.fromEmailRequired` | From email is required | Edit department · field error | 60 |  |
| `editDepartmentView.error.fromEmailInvalid` | Please enter a valid email address | Edit department · field error | 60 |  |
| `editDepartmentView.error.fromEmailLength` | From email must be 50 characters or less | Edit department · field error | 60 |  |
| `editDepartmentView.error.notFound` | Department not found | Edit department · notification | 40 |  |
| `editDepartmentView.error.invalidId` | Invalid department ID | Edit department · notification | 40 |  |
| `editDepartmentView.error.fixValidation` | Please fix the validation errors before saving | Edit department · notification | 80 |  |
| `editDepartmentView.error.nameExists` | Department name already exists. Please choose a different name. | Edit department · notification | 100 |  |
| `editDepartmentView.error.codeExists` | Department code already exists. Please choose a different code. | Edit department · notification | 100 |  |
| `editDepartmentView.error.nameOrCodeExists` | A department with this name or code already exists. | Edit department · notification | 100 |  |
| `editDepartmentView.error.save` | Error saving department: {0} | Edit department · notification | 100 | params: {0} = technical error text |
| `editUserView.username` | Username | Edit user · field label | 30 |  |
| `editUserView.firstName` | First name | Edit user · field label | 30 |  |
| `editUserView.lastName` | Last name | Edit user · field label | 30 |  |
| `editUserView.active` | Active | Edit user · checkbox label | 20 |  |
| `editUserView.departments` | Departments | Edit user · multi-select label | 30 |  |
| `editUserView.role` | Role | Edit user · dropdown label | 20 |  |
| `editUserView.role.heading` | Database role assignment | Edit user · section heading | 40 |  |
| `editUserView.role.info` | Sets this user's database role fallback. Choose the user's highest role; elicit_admin and elicit_user each imply the roles below them. | Edit user · explanatory paragraph; elicit_admin and elicit_user are role identifiers, keep as written | 200 |  |
| `editUserView.saved` | User saved | Edit user · notification | 40 |  |
| `editUserView.error.usernameRequired` | Username is required | Edit user · field error | 60 |  |
| `editUserView.error.usernameLength` | Username must be 1-255 characters | Edit user · field error | 60 |  |
| `editUserView.error.firstNameRequired` | First name is required | Edit user · field error | 60 |  |
| `editUserView.error.firstNameLength` | First name must be 1-255 characters | Edit user · field error | 60 |  |
| `editUserView.error.lastNameRequired` | Last name is required | Edit user · field error | 60 |  |
| `editUserView.error.lastNameLength` | Last name must be 1-255 characters | Edit user · field error | 60 |  |
| `editUserView.error.notFound` | User not found | Edit user · notification | 40 |  |
| `editUserView.error.fixValidation` | Please fix the validation errors before saving | Edit user · notification | 80 |  |
| `surveyDefinitionExportView.title` | Export survey definition | Export survey definition · heading | 40 |  |
| `surveyDefinitionExportView.intro` | Download a survey definition file (.elicit) for any survey installed here. The file carries the survey's structure only, never respondent data, and can be applied to another Elicit deployment through Apply Survey Definition or opened in the Author tool. | Export survey definition · paragraph; "Apply Survey Definition" names a console screen, "Author" is a product name | 400 |  |
| `surveyDefinitionExportView.revisionNote` | Every download is a fresh revision of the definition as it stands right now. To roll one revision out to several sites, download once and distribute that file rather than exporting again at each site. | Export survey definition · paragraph | 300 |  |
| `surveyDefinitionExportView.empty` | No survey is installed in this deployment, so there is nothing to export. | Export survey definition · paragraph shown when no survey is installed | 120 |  |
| `surveyDefinitionExportView.grid.name` | Name | Export survey definition · grid column header | 20 |  |
| `surveyDefinitionExportView.grid.title` | Title | Export survey definition · grid column header | 20 |  |
| `surveyDefinitionExportView.grid.surveyKey` | Survey key | Export survey definition · grid column header | 20 |  |
| `surveyDefinitionExportView.grid.installedRevision` | Installed revision | Export survey definition · grid column header | 25 |  |
| `surveyDefinitionExportView.grid.download` | Download | Export survey definition · grid column header | 20 |  |
| `surveyDefinitionExportView.notRecorded` | Not recorded | Export survey definition · grid cell when no revision is recorded | 20 |  |
| `surveyDefinitionExportView.btnDownload` | Download | Export survey definition · button | 15 |  |
| `surveyDefinitionExportView.downloadAriaLabel` | Download the definition of {0} | Export survey definition · download link accessible name | 60 | params: {0} = survey name |
| `editMessageTemplatesView.subject` | Subject | Edit message template · field label | 20 |  |
| `editMessageTemplatesView.body` | Body | Edit message template · field label | 20 |  |
| `editMessageTemplatesView.mimeType` | MIME type | Edit message template · dropdown label; MIME is a technical term | 20 |  |
| `editMessageTemplatesView.department` | Department | Edit message template · dropdown label | 20 |  |
| `editMessageTemplatesView.btnUpdate` | Update | Edit message template · primary button in edit mode | 15 |  |
| `editMessageTemplatesView.preview` | Message preview | Edit message template · heading of the live preview | 30 |  |
| `editMessageTemplatesView.saved` | Message template saved | Edit message template · notification | 40 |  |
| `editMessageTemplatesView.updated` | Message template updated | Edit message template · notification | 40 |  |
| `editMessageTemplatesView.error.subjectRequired` | Subject is required | Edit message template · field error | 60 |  |
| `editMessageTemplatesView.error.subjectLength` | Subject must be 1-255 characters | Edit message template · field error | 60 |  |
| `editMessageTemplatesView.error.bodyRequired` | Body is required | Edit message template · field error | 60 |  |
| `editMessageTemplatesView.error.bodyLength` | Body must be 1-6000 characters | Edit message template · field error | 60 |  |
| `editMessageTemplatesView.error.mimeTypeRequired` | MIME type is required | Edit message template · field error | 60 |  |
| `editMessageTemplatesView.error.departmentRequired` | Department is required | Edit message template · field error | 60 |  |
| `editMessageTemplatesView.error.fixValidation` | Please fix the validation errors | Edit message template · notification | 60 |  |
| `mainLayout.nav.searchSubjects` | Search Subjects | Navigation drawer · menu item | 25 |  |
| `mainLayout.nav.registerSubjects` | Register Subjects | Navigation drawer · menu item | 25 |  |
| `mainLayout.nav.admin` | Admin | Navigation drawer · section heading for administration items | 20 |  |
| `mainLayout.nav.departments` | Departments | Navigation drawer · menu item | 25 |  |
| `mainLayout.nav.messageTemplates` | Message Templates | Navigation drawer · menu item | 25 |  |
| `mainLayout.nav.users` | Users | Navigation drawer · menu item | 20 |  |
| `mainLayout.nav.importRespondent` | Import Respondent | Navigation drawer · menu item | 30 |  |
| `mainLayout.nav.applySurveyDefinition` | Apply Survey Definition | Navigation drawer · menu item | 30 |  |
| `mainLayout.nav.exportSurveyDefinition` | Export Survey Definition | Navigation drawer · menu item | 30 |  |
| `mainLayout.nav.manual` | Manual (PDF) | Navigation drawer · menu item opening the administrator's manual PDF in a new tab; PDF stays | 25 | identical |
| `mainLayout.header.manual` | Manual | Header · link opening the administrator's manual PDF in a new tab | 15 | identical |
| `mainLayout.header.manualTitle` | Open the administrator's manual (PDF) in a new tab | Header · tooltip of the manual link; PDF stays | 60 |  |
| `mainLayout.nav.logout` | Logout | Navigation drawer · menu item | 20 |  |
| `debugView.pageTitle` | Debug - Elicit Admin | Debug page (diagnostics, admins only) · browser tab title | 40 |  |
| `debugView.title` | Debug Information | Debug page (diagnostics) · heading | 40 |  |
| `debugView.user` | User: {0} | Debug page (diagnostics) · label in a monospace block | 40 | params: {0} = user name |
| `debugView.isAnonymous` | Is Anonymous: {0} | Debug page (diagnostics) · label | 40 | params: {0} = true/false |
| `debugView.roles` | Roles: {0} | Debug page (diagnostics) · label | 40 | params: {0} = role list identical |
| `debugView.roleSource` | Role Source: {0} | Debug page (diagnostics) · label | 40 | params: {0} = source name |
| `debugView.hasAdminRole` | Has elicit_admin: {0} | Debug page (diagnostics) · label; elicit_admin is a technical role name, keep it | 40 | params: {0} = true/false |
| `debugView.hasUserRole` | Has elicit_user: {0} | Debug page (diagnostics) · label; elicit_user is a technical role name, keep it | 40 | params: {0} = true/false |
| `debugView.idToken` | ID Token: {0} | Debug page (diagnostics) · label | 40 | params: {0} = masked token |
| `debugView.idTokenUnavailable` | ID Token: Not available or resolvable | Debug page (diagnostics) · label | 60 |  |
| `debugView.idTokenError` | ID Token Error: {0} | Debug page (diagnostics) · label | 60 | params: {0} = technical error text |
| `debugView.accessToken` | Access Token: {0} | Debug page (diagnostics) · label | 40 | params: {0} = masked token |
| `debugView.accessTokenUnavailable` | Access Token: Not available or resolvable | Debug page (diagnostics) · label | 60 |  |
| `debugView.accessTokenError` | Access Token Error: {0} | Debug page (diagnostics) · label | 60 | params: {0} = technical error text |
| `departmentsView.grid.name` | Department Name | Departments screen · grid column header | 25 |  |
| `departmentsView.grid.code` | Code | Departments screen · grid column header | 15 |  |
| `departmentsView.grid.defaultMessageId` | Default Message ID | Departments screen · grid column header | 25 |  |
| `departmentsView.grid.fromEmail` | From Email | Departments screen · grid column header | 20 |  |
| `departmentsView.newDepartment` | New Department | Departments screen · primary button | 25 |  |
| `messageTemplatesView.grid.id` | ID | Message templates screen · grid column header | 10 | identical |
| `messageTemplatesView.grid.department` | Department | Message templates screen · grid column header | 20 |  |
| `messageTemplatesView.grid.subject` | Subject | Message templates screen · grid column header (email subject) | 20 |  |
| `messageTemplatesView.grid.mimeType` | MIME Type | Message templates screen · grid column header; MIME is technical, keep it | 15 |  |
| `messageTemplatesView.newTemplate` | New Message Template | Message templates screen · primary button | 30 |  |
| `missingSurveyNotice.headline` | No survey is installed in this deployment. | Banner on every screen when no survey is installed · status text | 80 |  |
| `missingSurveyNotice.adminRemedy` | Apply a survey definition | Missing-survey banner · link to the Apply Survey Definition screen | 40 |  |
| `missingSurveyNotice.nonAdminRemedy` | Ask a system administrator to apply a survey definition before registering subjects. | Missing-survey banner · text for users who cannot apply a definition | 120 |  |
| `missingDepartmentDialog.title` | No department assigned | Blocking dialog shown when the signed-in account has no department · dialog title | 40 |  |
| `missingDepartmentDialog.admin.message` | Your account is not assigned to a department, so the console has nothing to show you. Create the first department to continue; it will be assigned to you automatically. | No-department dialog · body text for an administrator, who can create one | 200 |  |
| `missingDepartmentDialog.admin.action` | Add a department | No-department dialog · primary button leading to the Departments screen | 25 |  |
| `missingDepartmentDialog.user.message` | Your account is not assigned to a department, so the console has nothing to show you. Ask an administrator to assign you to one, then sign in again. | No-department dialog · body text for a user, who must ask an administrator | 200 |  |
| `missingDepartmentDialog.logout` | Logout | No-department dialog · button that signs the user out | 15 |  |
| `missingDepartmentDialog.manual` | Open the manual | No-department dialog · link opening the administrator's manual PDF in a new tab | 25 |  |
| `paginationControls.pageSize` | Page size | Grid pager · label next to the page size selector | 15 |  |
| `paginationControls.pageOf` | Page {0} of {1} | Grid pager · current page label | 20 | params: {0} = current page, {1} = page count |
| `paginationControls.firstPage` | Go to first page | Grid pager · accessible name of an icon button | 30 |  |
| `paginationControls.lastPage` | Go to last page | Grid pager · accessible name of an icon button | 30 |  |
| `paginationControls.nextPage` | Go to next page | Grid pager · accessible name of an icon button | 30 |  |
| `paginationControls.previousPage` | Go to previous page | Grid pager · accessible name of an icon button | 30 |  |
| `respondentImportView.title` | Import Respondent | Import respondent screen · heading | 30 |  |
| `respondentImportView.intro` | Upload a respondent export file (.elicit) produced by the "Export" action on the Search Subjects grid to import that respondent into this instance. | Import respondent screen · paragraph; "Export" and "Search Subjects" name UI items | 200 |  |
| `respondentImportView.upload` | Upload | Import respondent screen · upload button | 15 |  |
| `respondentImportView.importSuccessful` | Import Successful | Import respondent screen · result dialog title | 30 |  |
| `respondentImportView.importFailed` | Import Failed | Import respondent screen · result dialog title | 30 |  |
| `respondentImportView.recordsImported` | Records imported: {0} | Import respondent screen · result summary line | 40 | params: {0} = count |
| `respondentImportView.errors` | Errors: | Import respondent screen · result summary heading | 15 |  |
| `surveyDefinitionApplyView.title` | Apply Survey Definition | Apply survey definition screen · heading | 30 |  |
| `surveyDefinitionApplyView.intro` | Upload a survey definition file (.elicit) exported from the Author tool. You do not need to say whether this is a new survey or a revision of one already here — the file identifies itself, and this instance works out which it is. | Apply survey definition screen · paragraph; Author is the product name of the authoring tool | 300 |  |
| `surveyDefinitionApplyView.revisionNote` | A revision older than the one already installed is refused. Reverting to an earlier revision is a database restore, not an upload. | Apply survey definition screen · paragraph | 200 |  |
| `surveyDefinitionApplyView.upload` | Upload | Apply survey definition screen · upload button | 15 |  |
| `surveyDefinitionApplyView.applyFailed` | Apply Failed | Apply survey definition screen · result dialog title | 30 |  |
| `surveyDefinitionApplyView.newSurveyInstalled` | New Survey Installed | Apply survey definition screen · result dialog title | 30 |  |
| `surveyDefinitionApplyView.surveyUpdated` | Survey Updated | Apply survey definition screen · result dialog title | 30 |  |
| `surveyDefinitionApplyView.surveyKey` | Survey key: {0} | Apply survey definition screen · result summary line | 40 | params: {0} = survey key (identifier) |
| `surveyDefinitionApplyView.recordsInstalled` | Records installed: {0} | Apply survey definition screen · result summary line | 40 | params: {0} = count |
| `surveyDefinitionApplyView.countsHeader` | created / versioned / unchanged / retired: | Apply survey definition screen · heading above per-table counts in that order | 60 |  |
| `surveyDefinitionApplyView.errors` | Errors: | Apply survey definition screen · result summary heading | 15 |  |
| `unauthorizedView.title` | Access Restricted | Access restricted screen · heading | 30 |  |
| `unauthorizedView.message` | You are authenticated but do not have the required permissions to access this application. Please contact your administrator to request access with the 'elicit_user' or 'elicit_admin' role. | Access restricted screen · paragraph; elicit_user and elicit_admin are technical role names, keep them | 200 |  |
| `unauthorizedView.logout` | Logout | Access restricted screen · button | 15 |  |
| `usersView.info.databaseMode` | Departments are assigned through this interface. Roles (Admin/User/Importer) are also assigned through this interface, on the Edit User screen. | Users screen · explanatory paragraph when roles are managed in the console | 200 |  |
| `usersView.info.oidcMode` | Users must be configured in the OpenID Connect (OIDC) authentication system with the roles "Admin" or "User". Departments are assigned through this interface. | Users screen · explanatory paragraph when roles come from the identity provider; "Admin"/"User" are role names, keep them | 200 |  |
| `usersView.grid.username` | Username | Users screen · grid column header | 15 |  |
| `usersView.grid.firstName` | First Name | Users screen · grid column header | 15 |  |
| `usersView.grid.lastName` | Last Name | Users screen · grid column header | 15 |  |
| `usersView.grid.active` | Active | Users screen · grid column header | 15 |  |
| `usersView.grid.department` | Department | Users screen · grid column header | 20 |  |
| `usersView.addUser` | Add User | Users screen · primary button | 20 |  |
| `loginView.redirecting` | Redirecting to sign-in… | Sign-in redirect page (shown briefly) · text | 40 |  |
| `logoutView.signingOut` | Signing out… | Sign-out page (shown briefly) · text | 40 |  |
| `reporting.error.pdf` | Failed to generate PDF: {0} | Subject report · notification | 100 | params: {0} = technical error text |
| `reporting.error.service` | Service error: {0} | Subject report · error text | 100 | params: {0} = technical error text |
| `reporting.error.forbidden` | Access forbidden - License validation may have failed. Please check your license configuration. | Subject report · error text | 160 |  |
| `reporting.error.serviceHttp` | Service error (HTTP {0}): {1} | Subject report · error text | 100 | params: {0} = HTTP status number, {1} = technical error text |
| `reporting.error.license` | License validation failed - {0} | Subject report · error text | 100 | params: {0} = error text |
| `reporting.error.title` | Error - {0} | Subject report · error card title | 60 | params: {0} = report name; may be identical |
| `reporting.error.html` | <div style="color: red; padding: 20px; border: 1px solid red; background-color: #ffe6e6;"><h3>Report Generation Error</h3><p><strong>Service:</strong> {0}</p><p><strong>Error:</strong> {1}</p>{2}</div> | Subject report · error card body | 400 | html params: {0} = report name, {1} = error text, {2} = optional hint paragraph; keep the style attribute |
| `reporting.error.licenseHint` | <p><em>If this is a license error, please ensure your PREMM5 license is valid and properly configured.</em></p> | Subject report · error card hint | 200 | html |
| `reporting.error.pdfText` | Report Generation Error - Service: {0} - Error: {1}{2} | Subject report PDF · error paragraph (plain text) | 200 | params: {0} = report name, {1} = error text, {2} = optional hint |
| `reporting.error.pdfLicenseHint` | - If this is a license error, please ensure your PREMM5 license is valid and properly configured. | Subject report PDF · appended hint; keep the leading " - " | 160 |  |
| `reporting.error.unknown` | Unknown error | Subject report PDF · fallback error text | 30 |  |
| `common.listAnd` | {0} and {1} | Any notice · joins two items of a short list | 20 | params: {0} = first item, {1} = second item |
| `common.none` | none | System screens · value shown when nothing is set | 15 |  |
| `common.unknown` | unknown | System screens · value shown when a brand field is missing | 15 |  |
| `mainLayout.nav.system` | System | Navigation drawer · section heading for setup and diagnostics screens | 20 |  |
| `mainLayout.nav.systemOverview` | Overview | Navigation drawer · menu item | 25 |  |
| `mainLayout.nav.systemDatabase` | Database | Navigation drawer · menu item | 25 |  |
| `mainLayout.nav.systemBranding` | Branding | Navigation drawer · menu item | 25 |  |
| `mainLayout.nav.systemEmail` | Email | Navigation drawer · menu item | 25 |  |
| `mainLayout.nav.systemConnections` | Connections | Navigation drawer · menu item | 25 |  |
| `mainLayout.nav.systemOidc` | OIDC | Navigation drawer · menu item (the sign-in diagnostics page; OIDC is a protocol name) | 25 | identical |
| `system.grid.item` | Item | System screens · grid column header (the name of a row) | 20 |  |
| `system.grid.value` | Value | System screens · grid column header | 20 |  |
| `system.grid.state` | State | System screens · grid column header (a status badge) | 20 |  |
| `system.grid.detail` | Detail | System screens · grid column header | 20 |  |
| `system.badge.ok` | OK | System screens · status badge for a passed check | 12 | identical |
| `system.badge.failed` | Failed | System screens · status badge for a failed check | 12 |  |
| `system.badge.notChecked` | Not checked | System screens · status badge for a check not yet run | 15 |  |
| `system.badge.present` | Present | System screens · status badge for a setting that is set | 12 |  |
| `system.badge.absent` | Absent | System screens · status badge for a setting that is missing | 12 |  |
| `system.milliseconds` | {0} ms | System screens · duration in milliseconds | 12 | params: {0} = number; identical where "ms" is the usual abbreviation |
| `defaultAccountNotice.instruction.one` | The database migrations seeded the default account {0}. Rename it before this deployment goes live: open Admin > Users and change the username, and when elicit.authorization.mode=DATABASE rename the matching account in the identity provider to the same value. | Console-wide banner · warning that one seeded default account still exists | 400 | params: {0} = the quoted account name; keep Admin > Users and elicit.authorization.mode=DATABASE as they are |
| `defaultAccountNotice.instruction.many` | The database migrations seeded the default accounts {0}. Rename them before this deployment goes live: open Admin > Users and change the username, and when elicit.authorization.mode=DATABASE rename the matching account in the identity provider to the same value. | Console-wide banner · warning that several seeded default accounts still exist | 400 | params: {0} = the quoted account names joined with common.listAnd; keep Admin > Users and elicit.authorization.mode=DATABASE as they are |
| `defaultAccountNotice.openUsers` | Open Users | Console-wide banner · link to the Users screen | 20 |  |
| `systemOverviewView.pageTitle` | System Overview | System overview · browser tab title | 40 |  |
| `systemOverviewView.title` | System Overview | System overview · page heading | 40 |  |
| `systemOverviewView.intro` | What this deployment is running, whether it is healthy, and the setup work still outstanding. Settings are shown as present or absent, never by value; they are startup configuration and cannot be changed here. | System overview · introductory paragraph | 300 |  |
| `systemOverviewView.running` | Running | System overview · section heading (what is running) | 30 |  |
| `systemOverviewView.row.application` | Application | System overview · row label (application name) | 25 |  |
| `systemOverviewView.row.version` | Version | System overview · row label | 25 |  |
| `systemOverviewView.row.built` | Built | System overview · row label (build time) | 25 |  |
| `systemOverviewView.row.profile` | Profile | System overview · row label (runtime profile) | 25 |  |
| `systemOverviewView.row.started` | Started | System overview · row label (start time) | 25 |  |
| `systemOverviewView.row.uptime` | Uptime | System overview · row label | 25 |  |
| `systemOverviewView.uptime.days` | {0} d {1} h {2} min | System overview · uptime value | 30 | params: {0} = days, {1} = hours, {2} = minutes; identical where the abbreviations are the same |
| `systemOverviewView.uptime.hours` | {0} h {1} min | System overview · uptime value | 30 | params: {0} = hours, {1} = minutes; identical where the abbreviations are the same |
| `systemOverviewView.uptime.minutes` | {0} min {1} s | System overview · uptime value | 30 | params: {0} = minutes, {1} = seconds; identical where the abbreviations are the same |
| `systemOverviewView.health` | Health | System overview · section heading | 30 |  |
| `systemOverviewView.area.applicationDatabase` | Application database connection | System overview · health row, link to the Database screen | 40 |  |
| `systemOverviewView.area.ownerDatabase` | Owner database connection | System overview · health row, link to the Database screen | 40 |  |
| `systemOverviewView.area.branding` | Branding | System overview · health row, link to the Branding screen | 40 |  |
| `systemOverviewView.area.email` | Email | System overview · health row, link to the Email screen | 40 |  |
| `systemOverviewView.area.connections` | Connections | System overview · health row, link to the Connections screen | 40 |  |
| `systemOverviewView.connectionsSummary.one` | {0} outbound target configured; open Connections to check it | System overview · health detail when exactly one outbound target is configured | 80 | params: {0} = the number 1 |
| `systemOverviewView.connectionsSummary.many` | {0} outbound targets configured; open Connections to check them | System overview · health detail for several (or zero) outbound targets | 80 | params: {0} = count |
| `systemOverviewView.grid.area` | Area | System overview · grid column header | 20 |  |
| `systemOverviewView.requiredSettings` | Required settings | System overview · section heading | 30 |  |
| `systemOverviewView.grid.setting` | Setting | System overview · grid column header (property name) | 20 |  |
| `systemOverviewView.grid.suppliedBy` | Supplied by | System overview · grid column header (environment variable) | 20 |  |
| `systemOverviewView.grid.purpose` | Purpose | System overview · grid column header | 20 |  |
| `systemOverviewView.setting.datasourcePassword` | password of the application database user | System overview · purpose of a required setting | 60 |  |
| `systemOverviewView.setting.ownerPassword` | password of the owner database user that runs migrations | System overview · purpose of a required setting | 60 |  |
| `systemOverviewView.setting.oidcSecret` | client secret registered with the identity provider | System overview · purpose of a required setting | 60 |  |
| `systemOverviewView.setting.mailerFrom` | sender address of every invitation and reminder | System overview · purpose of a required setting | 60 |  |
| `systemOverviewView.outstanding` | Setup still outstanding | System overview · section heading | 30 |  |
| `systemOverviewView.nothingOutstanding` | Nothing outstanding. | System overview · text when no setup work remains | 30 |  |
| `systemOverviewView.warning.noSurvey` | No survey is installed. Apply a survey definition before registering subjects. | System overview · outstanding setup item | 120 |  |
| `systemOverviewView.warning.noSenderAddress` | No department has a sender address, so invitations have no From address. | System overview · outstanding setup item | 120 |  |
| `systemOverviewView.warning.noAccessCodeTemplate` | No message template contains the <ACCESS_CODE> placeholder, so no invitation can carry a survey link. | System overview · outstanding setup item | 140 | keep <ACCESS_CODE> as it is |
| `systemOverviewView.warning.noDepartment` | No department exists. Create one on the Departments screen before registering subjects. | System overview · outstanding setup item | 120 |  |
| `systemOverviewView.warning.noMessageTemplate` | No message template exists, so registering a subject sends no invitation. Create one on the Message Templates screen. | System overview · outstanding setup item | 140 |  |
| `systemOverviewView.warning.legacySetting` | The setting {0} is configured but no longer read; use {1} instead. | System overview · outstanding setup item | 120 | params: {0} = old property name, {1} = new property name |
| `systemOverviewView.remedy.users` | Open Users | System overview · link after a setup item | 25 |  |
| `systemOverviewView.remedy.applySurveyDefinition` | Apply a survey definition | System overview · link after a setup item | 30 |  |
| `systemOverviewView.remedy.departments` | Open Departments | System overview · link after a setup item | 25 |  |
| `systemOverviewView.remedy.messageTemplates` | Open Message Templates | System overview · link after a setup item | 30 |  |
| `systemDatabaseView.pageTitle` | System Database | System database · browser tab title | 40 |  |
| `systemDatabaseView.title` | Database | System database · page heading | 30 |  |
| `systemDatabaseView.intro` | The application connection serves the console; the owner connection runs the migrations. Readiness probes only the first, so a wrong owner password shows up here and nowhere else. | System database · introductory paragraph | 300 |  |
| `systemDatabaseView.connections` | Connections | System database · section heading | 30 |  |
| `systemDatabaseView.grid.connection` | Connection | System database · grid column header | 20 |  |
| `systemDatabaseView.grid.configuredUser` | Configured user | System database · grid column header (database user from configuration) | 20 |  |
| `systemDatabaseView.grid.connectedAs` | Connected as | System database · grid column header (database user reported by the server) | 20 |  |
| `systemDatabaseView.grid.roundTrip` | Round trip | System database · grid column header (probe duration) | 20 |  |
| `systemDatabaseView.connection.application` | Application | System database · name of the connection the console reads through | 20 |  |
| `systemDatabaseView.connection.owner` | Owner | System database · name of the connection that runs migrations | 20 |  |
| `systemDatabaseView.migrations` | Migrations | System database · section heading | 30 |  |
| `systemDatabaseView.grid.module` | Module | System database · grid column header | 20 |  |
| `systemDatabaseView.grid.historyTable` | History table | System database · grid column header | 20 |  |
| `systemDatabaseView.grid.latestVersion` | Latest version | System database · grid column header | 20 |  |
| `systemDatabaseView.grid.applied` | Applied | System database · grid column header (when the latest migration ran) | 20 |  |
| `systemDatabaseView.version.notInstalled` | not installed | System database · version cell when a module has no history table | 20 |  |
| `systemDatabaseView.version.none` | none | System database · version cell when the history table is empty | 20 |  |
| `systemDatabaseView.badge.notInstalled` | Not installed | System database · status badge | 15 |  |
| `systemDatabaseView.badge.empty` | Empty | System database · status badge | 15 |  |
| `systemDatabaseView.content` | Content | System database · section heading (what is stored) | 30 |  |
| `systemDatabaseView.row.durableSequences` | Kimball durable-key sequences | System database · row label; Kimball is a data-warehouse design name | 40 |  |
| `systemDatabaseView.row.surveysInstalled` | Surveys installed | System database · row label | 40 |  |
| `systemDatabaseView.row.consoleUsers` | Console users | System database · row label | 40 |  |
| `systemDatabaseView.row.seededAccounts` | Seeded default accounts still present | System database · row label | 40 |  |
| `systemDatabaseView.seededAccounts.none` | none | System database · value when no seeded account remains | 15 |  |
| `systemDatabaseView.seededAccounts.present` | {0} (rename them, see Users) | System database · value listing the seeded accounts | 60 | params: {0} = account names |
| `systemDatabaseView.durableSequences.exists` | survey.{0} exists | System database · detail of a passed check | 60 | params: {0} = sequence name; keep the survey. prefix |
| `systemDatabaseView.durableSequences.missing` | survey.{0} is missing: the Survey module has not applied its V3 migrations, and the Admin and Family History migrations depend on it | System database · detail of a failed check | 200 | params: {0} = sequence name; keep the survey. prefix; Survey, Admin and Family History are module names |
| `systemBrandingView.pageTitle` | System Branding | System branding · browser tab title | 40 |  |
| `systemBrandingView.title` | Branding | System branding · page heading | 30 |  |
| `systemBrandingView.intro` | The brand resolves from the mounted directory first, then the local directory, then the default packaged with the application. Each asset is listed on its own, because a partial mount renders with the wrong fonts and no error. | System branding · introductory paragraph | 300 |  |
| `systemBrandingView.reload` | Reload brand | System branding · button | 20 |  |
| `systemBrandingView.reloaded` | Brand cache discarded; the result below is freshly resolved. | System branding · notification after reloading | 80 |  |
| `systemBrandingView.resolution` | Resolution | System branding · section heading (how the brand was found) | 30 |  |
| `systemBrandingView.row.configuredPath` | Configured path (brand.file.system.path) | System branding · row label; keep the property name in parentheses | 50 |  |
| `systemBrandingView.row.localPath` | Local path (brand.local.path) | System branding · row label; keep the property name in parentheses | 50 |  |
| `systemBrandingView.path.exists` | {0} (exists) | System branding · path value | 100 | params: {0} = directory path |
| `systemBrandingView.path.notFound` | {0} (not found) | System branding · path value | 100 | params: {0} = directory path |
| `systemBrandingView.row.metadataFile` | Metadata file | System branding · row label | 30 |  |
| `systemBrandingView.row.brandName` | Brand name | System branding · row label | 30 |  |
| `systemBrandingView.row.organization` | Organization | System branding · row label | 30 |  |
| `systemBrandingView.row.brandVersion` | Brand version | System branding · row label | 30 |  |
| `systemBrandingView.row.inUse` | In use (cached) | System branding · row label (the brand currently applied) | 30 |  |
| `systemBrandingView.inUse` | {0} [{1}] | System branding · value | 60 | params: {0} = brand display name, {1} = brand key; identical (punctuation only) |
| `systemBrandingView.row.summary` | Summary | System branding · row label | 30 |  |
| `systemBrandingView.summary.mounted` | Mounted brand at {0} | System branding · summary line | 80 | params: {0} = directory path |
| `systemBrandingView.summary.mountedNamed` | Mounted brand: {0} at {1} | System branding · summary line | 80 | params: {0} = brand name, {1} = directory path |
| `systemBrandingView.summary.local` | Local brand directory {0} | System branding · summary line | 80 | params: {0} = directory path |
| `systemBrandingView.summary.embedded` | Embedded default theme; no brand directory at {0} | System branding · summary line | 80 | params: {0} = directory path |
| `systemBrandingView.assets` | Assets | System branding · section heading | 30 |  |
| `systemBrandingView.grid.asset` | Asset | System branding · grid column header | 20 |  |
| `systemBrandingView.grid.path` | Path | System branding · grid column header | 20 |  |
| `systemBrandingView.grid.source` | Source | System branding · grid column header (where the asset came from) | 20 |  |
| `systemBrandingView.grid.location` | Location | System branding · grid column header | 20 |  |
| `systemBrandingView.source.mounted` | Mounted | System branding · source badge (from the mounted directory) | 15 |  |
| `systemBrandingView.source.local` | Local | System branding · source badge (from the local directory) | 15 | identical in Romance languages |
| `systemBrandingView.source.embedded` | Embedded default | System branding · source badge (packaged default) | 20 |  |
| `systemBrandingView.source.absent` | Absent | System branding · source badge | 15 |  |
| `systemBrandingView.source.unreadable` | Unreadable | System branding · source badge | 15 |  |
| `systemBrandingView.asset.colourStylesheet` | colour stylesheet | System branding · what an asset is for | 30 |  |
| `systemBrandingView.asset.typographyStylesheet` | typography stylesheet | System branding · what an asset is for | 30 |  |
| `systemBrandingView.asset.themeStylesheet` | theme stylesheet | System branding · what an asset is for | 30 |  |
| `systemBrandingView.asset.horizontalLogo` | horizontal logo | System branding · what an asset is for | 30 |  |
| `systemBrandingView.asset.headerIcon` | header icon | System branding · what an asset is for | 30 |  |
| `systemBrandingView.asset.favicon` | favicon | System branding · what an asset is for (the browser tab icon) | 30 | identical |
| `systemBrandingView.asset.notFound` | not found in any location | System branding · asset location when nothing was found | 40 |  |
| `systemBrandingView.asset.unreadable` | {0} exists but cannot be read | System branding · asset location when the file cannot be read | 80 | params: {0} = file path |
| `systemBrandingView.logoPreview` | Logo preview | System branding · section heading | 30 |  |
| `systemBrandingView.logoAlt` | Brand logo | System branding · logo image alternative text | 30 |  |
| `systemEmailView.pageTitle` | System Email | System email · browser tab title | 40 |  |
| `systemEmailView.title` | Email | System email · page heading | 30 |  |
| `systemEmailView.intro` | These are the mail settings the service started with; they cannot be changed here. The test message is sent by the same service, sender and timeout that invitations use, so a passing test means invitations will send. | System email · introductory paragraph | 300 |  |
| `systemEmailView.effectiveSettings` | Effective settings | System email · section heading | 30 |  |
| `systemEmailView.grid.setting` | Setting | System email · grid column header | 20 |  |
| `systemEmailView.row.sender` | Sender (quarkus.mailer.from) | System email · row label; keep the property name in parentheses | 50 |  |
| `systemEmailView.row.host` | Host (quarkus.mailer.host) | System email · row label; keep the property name in parentheses | 50 |  |
| `systemEmailView.row.port` | Port (quarkus.mailer.port) | System email · row label; keep the property name in parentheses | 50 |  |
| `systemEmailView.row.tls` | TLS (quarkus.mailer.tls) | System email · row label; TLS is a protocol name; keep the property name in parentheses | 50 | identical |
| `systemEmailView.row.startTls` | STARTTLS (quarkus.mailer.start-tls) | System email · row label; STARTTLS is a protocol name; keep the property name in parentheses | 50 | identical |
| `systemEmailView.row.authMethods` | Authentication methods (quarkus.mailer.auth-methods) | System email · row label; keep the property name in parentheses | 60 |  |
| `systemEmailView.row.username` | Username (quarkus.mailer.username) | System email · row label; keep the property name in parentheses | 50 |  |
| `systemEmailView.row.password` | Password (quarkus.mailer.password) | System email · row label; keep the property name in parentheses | 50 |  |
| `systemEmailView.row.mock` | Mocked (quarkus.mailer.mock) | System email · row label (mail is discarded instead of sent); keep the property name in parentheses | 50 |  |
| `systemEmailView.value.present` | present | System email · value: the setting is set (its value is never shown) | 15 |  |
| `systemEmailView.value.absent` | absent | System email · value: the setting is missing | 15 |  |
| `systemEmailView.value.relayDefault` | not set (relay default) | System email · value when no authentication methods are configured | 30 |  |
| `systemEmailView.summary.noSender` | No sender address configured (quarkus.mailer.from) | System overview · email summary when no sender is configured; keep the property name | 60 |  |
| `systemEmailView.summary.from` | From {0} via {1} | System overview · email summary | 60 | params: {0} = sender address, {1} = host:port |
| `systemEmailView.summary.fromTls` | From {0} via {1} with TLS | System overview · email summary, TLS in use | 60 | params: {0} = sender address, {1} = host:port |
| `systemEmailView.summary.mocked` | {0} (mocked) | System overview · email summary wrapper when mail is mocked | 70 | params: {0} = the summary |
| `systemEmailView.sendTest` | Send a test message | System email · section heading | 30 |  |
| `systemEmailView.recipient` | Recipient | System email · email field label | 20 |  |
| `systemEmailView.recipient.invalid` | Enter a valid email address | System email · email field validation message | 40 |  |
| `systemEmailView.send` | Send test email | System email · button | 20 |  |
| `systemEmailView.sendingDisabled` | Sending is disabled because no sender address is configured. Set quarkus.mailer.from and restart the service. | System email · explanation under the disabled button; keep the property name | 150 |  |
| `systemEmailView.recipientRequired` | Enter a valid recipient address first. | System email · notification | 60 |  |
| `systemEmailView.sent` | Test email sent to {0} ({1}). | System email · notification | 100 | params: {0} = recipient address, {1} = technical detail |
| `systemEmailView.failed` | Test email failed: {0} | System email · notification | 100 | params: {0} = technical error text |
| `systemConnectionsView.pageTitle` | System Connections | System connections · browser tab title | 40 |  |
| `systemConnectionsView.title` | Connections | System connections · page heading | 30 |  |
| `systemConnectionsView.intro` | Each outbound dependency this deployment will call, read from the same stored rows and settings the runtime uses. A check sends one read-only request and gives up after {0} seconds; post-survey actions are never invoked and no report is generated. | System connections · introductory paragraph | 300 | params: {0} = timeout in seconds |
| `systemConnectionsView.empty` | No outbound targets are configured or stored yet. | System connections · text when there is nothing to check | 60 |  |
| `systemConnectionsView.checkAll` | Check all | System connections · button | 15 |  |
| `systemConnectionsView.check` | Check | System connections · button in a grid row | 12 |  |
| `systemConnectionsView.grid.dependency` | Dependency | System connections · grid column header (kind of dependency) | 20 |  |
| `systemConnectionsView.grid.source` | Source | System connections · grid column header (where the address comes from) | 20 |  |
| `systemConnectionsView.grid.address` | Address | System connections · grid column header | 20 |  |
| `systemConnectionsView.grid.time` | Time | System connections · grid column header (probe duration) | 20 |  |
| `systemConnectionsView.group.identityProvider` | Identity provider | System connections · kind of dependency | 25 |  |
| `systemConnectionsView.group.reportService` | Report service | System connections · kind of dependency | 25 |  |
| `systemConnectionsView.group.postSurveyAction` | Post-survey action | System connections · kind of dependency (a webhook called after a survey) | 25 |  |
| `systemConnectionsView.group.mailRelay` | Mail relay | System connections · kind of dependency | 25 |  |
| `systemConnectionsView.group.telemetryCollector` | Telemetry collector | System connections · kind of dependency | 25 |  |
| `systemConnectionsView.result.timedOut` | timed out after {0} s | System connections · check detail | 40 | params: {0} = seconds |
| `systemConnectionsView.result.interrupted` | interrupted | System connections · check detail | 20 |  |
| `systemConnectionsView.result.discoveryServed` | discovery document served at {0} | System connections · check detail | 80 | params: {0} = address |
| `systemConnectionsView.result.notDiscovery` | HTTP {0} from {1} is not an OIDC discovery document; check the realm address | System connections · check detail; OIDC is a protocol name | 120 | params: {0} = HTTP status code, {1} = address |
| `systemConnectionsView.result.reachableForbidden` | reachable, HTTP 403: license validation may have failed | System connections · check detail | 80 |  |
| `systemConnectionsView.result.reachable` | reachable, HTTP {0} | System connections · check detail | 40 | params: {0} = HTTP status code |
| `systemConnectionsView.result.noHostPort` | address {0} has no host and port | System connections · check detail | 60 | params: {0} = address |
| `systemConnectionsView.result.connected` | connected to {0}:{1} | System connections · check detail | 40 | params: {0} = host, {1} = port |

## English source file

```properties
common.language=Language
common.logoAlt={0} logo
common.appTitle={0} {1}
common.appTitle.default=Elicit {0}
common.appType.admin=Admin
common.save=Save
common.cancel=Cancel
common.close=Close
common.edit=Edit
common.search=Search
common.yes=Yes
common.no=No
registerView.pageTitle=Register subjects
registerView.firstName=First name
registerView.lastName=Last name
registerView.middleName=Middle name
registerView.dob=Date of birth
registerView.datePicker.today=Today
registerView.email=Email
registerView.phone=Phone
registerView.xid=External ID
registerView.error.departmentRequired=Department is required
registerView.error.surveyRequired=Survey is required
registerView.error.firstNameRequired=First name is required
registerView.error.lastNameRequired=Last name is required
registerView.error.dobPast=Date of birth must be in the past
registerView.error.emailInvalid=Enter a valid email address
registerView.error.phoneFormat=Phone must be ###-###-####
registerView.error.duplicateXid=Duplicate entry: a subject with the external ID {0} already exists for this department.
registerView.error.databaseTitle=Database error
registerView.error.database=Database error: {0}
registerView.btnUpdate=Update subject
registerView.btnUploadCsv=Upload CSV
registerView.upload.dropFile=Drop a CSV file here
registerView.upload.error.tooBig=The file is too big (5 MB maximum)
registerView.upload.error.wrongType=Only .csv files can be uploaded
registerView.upload.error.tooMany=Upload one file at a time
registerView.csvImport.successTitle=CSV import succeeded
registerView.csvImport.success=Successfully imported subjects:

{0}
registerView.csvImport.errorTitle=CSV import error
registerView.apiDoc.title=REST API instructions
registerView.error.subjectNotFound=Subject not found for access code: {0}
registerView.unknownDepartment=Unknown
registerView.error.excludedXid=External ID {0} is in the exclude list for department {1}
registerView.subjectSaved=Subject saved
registerView.error.fixValidation=Please fix the validation errors
registerView.error.accessCode=Error generating a new access code. Please try again
registerView.subjectUpdated=Subject updated
registerView.survey=Survey
registerView.department=Department
registerView.csvDoc.title=CSV file structure
registerView.intro.methods=You can register subjects individually using the form, a REST API, or by uploading a CSV file with multiple subjects.
registerView.intro.clickBelow=Click below to see the required CSV file format and examples:
registerView.csvDoc.columnsIntro=The CSV file should contain the following columns in order:
registerView.csvDoc.comments=All rows starting with a '#' character are treated as comments and ignored.
registerView.csvDoc.columnDescriptions=Column descriptions:
registerView.csvDoc.example=Example CSV data:
registerView.csvDoc.col.departmentId=Integer (required) - must be a valid department ID for the user
registerView.csvDoc.col.firstName=String (required) - the subject's first name
registerView.csvDoc.col.lastName=String (required) - the subject's last name
registerView.csvDoc.col.middleName=String (optional) - the subject's middle name
registerView.csvDoc.col.dob=Date (optional) - date of birth in yyyy-MM-dd or MM/dd/yyyy format
registerView.csvDoc.col.email=String (required) - a valid email address
registerView.csvDoc.col.phone=String (optional) - phone number in ###-###-#### format
registerView.csvDoc.col.xid=String (optional) - external ID for the subject
registerView.apiDoc.intro=You can also add subjects programmatically using the REST API endpoints.
registerView.apiDoc.single.h=1. Single subject registration
registerView.apiDoc.endpoint=Endpoint:
registerView.apiDoc.authentication=Authentication:
registerView.apiDoc.auth.anyRole=Requires a Bearer token with the elicit_admin, elicit_user or elicit_importer role
registerView.apiDoc.contentType=Content-Type:
registerView.apiDoc.requestBodyExample=Request body example:
registerView.apiDoc.bulk.h=2. Bulk subject registration
registerView.apiDoc.requestBodyArray=Request body example (array of subjects):
registerView.apiDoc.csvUpload.h=3. CSV file upload (REST API)
registerView.apiDoc.auth.importerRole=Requires a Bearer token with the elicit_importer role
registerView.apiDoc.requestBody=Request body:
registerView.apiDoc.requestBodyFile=Form field 'file' containing a CSV file with subject data
registerView.apiDoc.csvFormat=CSV file format:
registerView.apiDoc.columnRequirements=Column requirements:
registerView.apiDoc.columnRequirementsList=• departmentId: Integer (required) - valid department ID
• firstName: String (required) - the subject's first name
• lastName: String (required) - the subject's last name
• middleName: String (optional) - the subject's middle name
• dob: Date (optional) - format yyyy-MM-dd or MM/dd/yyyy
• email: String (required) - a valid email address
• phone: String (optional) - format ###-###-####
• xid: String (optional) - external ID for the subject
registerView.apiDoc.csvExample=CSV example:
registerView.apiDoc.webUpload.h=4. CSV file upload (web interface)
registerView.apiDoc.webUpload.p=You can also upload a CSV file using the upload component above in the web interface.
registerView.apiDoc.response.h=Response format (all endpoints)
registerView.apiDoc.response.single=Response example (single subject):
registerView.apiDoc.response.bulk=Response example (bulk subjects):
registerView.apiDoc.notes.h=Important notes:
registerView.apiDoc.notes.exclusion=• XID exclusion: subjects with XIDs in the exclusion list are not created
registerView.apiDoc.notes.duplicates=• Duplicate detection: existing subjects (same XID and department) are identified
registerView.apiDoc.notes.individual=• Individual processing: in bulk requests, each subject is processed independently
registerView.apiDoc.notes.auth=• Authentication: all endpoints require valid Bearer token authentication
searchView.pageTitle=Subject search
searchView.heading=Subject search
searchView.noUser.loggedIn=You have successfully signed in with the identity provider.
searchView.noUser.before=Unfortunately, there is no user named
searchView.noUser.after=in the application, or the user is inactive.
searchView.noUser.help=Please ask an Elicit administrator for help.
searchView.filter.departments=Department(s)
searchView.filter.allDepartments=All departments
searchView.filter.accessCode=Access code
searchView.filter.firstName=First name
searchView.filter.lastName=Last name
searchView.filter.email=Email
searchView.filter.phone=Phone
searchView.grid.accessCode=Access code
searchView.grid.department=Department
searchView.grid.firstName=First name
searchView.grid.middleName=Middle name
searchView.grid.lastName=Last name
searchView.grid.email=Email
searchView.grid.phone=Phone
searchView.grid.created=Created
searchView.grid.status=Status
searchView.grid.action=Action
searchView.action.placeholder=Select action
searchView.action.btnSubmit=Submit
searchView.action.sendEmail=Send email
searchView.action.printReports=Print reports
searchView.action.export=Export
searchView.action.emailSent=Email sent successfully
searchView.action.emailFailed=Failed to send the email. Check the server logs for details.
searchView.action.emailError=Failed to send the email: {0}
searchView.action.reportsGenerated=Reports generated successfully
searchView.action.reportsError=Failed to generate the reports: {0}
searchView.action.exportDownloading=Export downloading...
searchView.error.selectDepartments=Please select one or more departments
editDepartmentView.name=Department name
editDepartmentView.code=Department code
editDepartmentView.code.helper=Short abbreviation for the department (e.g. CARD, HR)
editDepartmentView.defaultMessageId=Default message ID
editDepartmentView.defaultMessageId.helper=ID of an existing message template, sent on registration
editDepartmentView.fromEmail=From email
editDepartmentView.fromEmail.helper=Email address that appears as the sender of department communications
editDepartmentView.btnCreate=Create department
editDepartmentView.btnUpdate=Update department
editDepartmentView.title.create=Create new department
editDepartmentView.title.edit=Edit department: {0}
editDepartmentView.created=Department created successfully
editDepartmentView.updated=Department updated successfully
editDepartmentView.error.nameRequired=Department name is required
editDepartmentView.error.nameLength=Department name must be 1-255 characters
editDepartmentView.error.codeLength=Department code must be 100 characters or less
editDepartmentView.error.defaultMessageIdRequired=Default message ID is required
editDepartmentView.error.defaultMessageIdLength=Default message ID must be 1-100 characters
editDepartmentView.error.fromEmailRequired=From email is required
editDepartmentView.error.fromEmailInvalid=Please enter a valid email address
editDepartmentView.error.fromEmailLength=From email must be 50 characters or less
editDepartmentView.error.notFound=Department not found
editDepartmentView.error.invalidId=Invalid department ID
editDepartmentView.error.fixValidation=Please fix the validation errors before saving
editDepartmentView.error.nameExists=Department name already exists. Please choose a different name.
editDepartmentView.error.codeExists=Department code already exists. Please choose a different code.
editDepartmentView.error.nameOrCodeExists=A department with this name or code already exists.
editDepartmentView.error.save=Error saving department: {0}
editUserView.username=Username
editUserView.firstName=First name
editUserView.lastName=Last name
editUserView.active=Active
editUserView.departments=Departments
editUserView.role=Role
editUserView.role.heading=Database role assignment
editUserView.role.info=Sets this user's database role fallback. Choose the user's highest role; elicit_admin and elicit_user each imply the roles below them.
editUserView.saved=User saved
editUserView.error.usernameRequired=Username is required
editUserView.error.usernameLength=Username must be 1-255 characters
editUserView.error.firstNameRequired=First name is required
editUserView.error.firstNameLength=First name must be 1-255 characters
editUserView.error.lastNameRequired=Last name is required
editUserView.error.lastNameLength=Last name must be 1-255 characters
editUserView.error.notFound=User not found
editUserView.error.fixValidation=Please fix the validation errors before saving
surveyDefinitionExportView.title=Export survey definition
surveyDefinitionExportView.intro=Download a survey definition file (.elicit) for any survey installed here. The file carries the survey's structure only, never respondent data, and can be applied to another Elicit deployment through Apply Survey Definition or opened in the Author tool.
surveyDefinitionExportView.revisionNote=Every download is a fresh revision of the definition as it stands right now. To roll one revision out to several sites, download once and distribute that file rather than exporting again at each site.
surveyDefinitionExportView.empty=No survey is installed in this deployment, so there is nothing to export.
surveyDefinitionExportView.grid.name=Name
surveyDefinitionExportView.grid.title=Title
surveyDefinitionExportView.grid.surveyKey=Survey key
surveyDefinitionExportView.grid.installedRevision=Installed revision
surveyDefinitionExportView.grid.download=Download
surveyDefinitionExportView.notRecorded=Not recorded
surveyDefinitionExportView.btnDownload=Download
surveyDefinitionExportView.downloadAriaLabel=Download the definition of {0}
editMessageTemplatesView.subject=Subject
editMessageTemplatesView.body=Body
editMessageTemplatesView.mimeType=MIME type
editMessageTemplatesView.department=Department
editMessageTemplatesView.btnUpdate=Update
editMessageTemplatesView.preview=Message preview
editMessageTemplatesView.saved=Message template saved
editMessageTemplatesView.updated=Message template updated
editMessageTemplatesView.error.subjectRequired=Subject is required
editMessageTemplatesView.error.subjectLength=Subject must be 1-255 characters
editMessageTemplatesView.error.bodyRequired=Body is required
editMessageTemplatesView.error.bodyLength=Body must be 1-6000 characters
editMessageTemplatesView.error.mimeTypeRequired=MIME type is required
editMessageTemplatesView.error.departmentRequired=Department is required
editMessageTemplatesView.error.fixValidation=Please fix the validation errors
mainLayout.nav.searchSubjects=Search Subjects
mainLayout.nav.registerSubjects=Register Subjects
mainLayout.nav.admin=Admin
mainLayout.nav.departments=Departments
mainLayout.nav.messageTemplates=Message Templates
mainLayout.nav.users=Users
mainLayout.nav.importRespondent=Import Respondent
mainLayout.nav.applySurveyDefinition=Apply Survey Definition
mainLayout.nav.exportSurveyDefinition=Export Survey Definition
mainLayout.nav.manual=Manual (PDF)
mainLayout.header.manual=Manual
mainLayout.header.manualTitle=Open the administrator's manual (PDF) in a new tab
mainLayout.nav.logout=Logout
debugView.pageTitle=Debug - Elicit Admin
debugView.title=Debug Information
debugView.user=User: {0}
debugView.isAnonymous=Is Anonymous: {0}
debugView.roles=Roles: {0}
debugView.roleSource=Role Source: {0}
debugView.hasAdminRole=Has elicit_admin: {0}
debugView.hasUserRole=Has elicit_user: {0}
debugView.idToken=ID Token: {0}
debugView.idTokenUnavailable=ID Token: Not available or resolvable
debugView.idTokenError=ID Token Error: {0}
debugView.accessToken=Access Token: {0}
debugView.accessTokenUnavailable=Access Token: Not available or resolvable
debugView.accessTokenError=Access Token Error: {0}
departmentsView.grid.name=Department Name
departmentsView.grid.code=Code
departmentsView.grid.defaultMessageId=Default Message ID
departmentsView.grid.fromEmail=From Email
departmentsView.newDepartment=New Department
messageTemplatesView.grid.id=ID
messageTemplatesView.grid.department=Department
messageTemplatesView.grid.subject=Subject
messageTemplatesView.grid.mimeType=MIME Type
messageTemplatesView.newTemplate=New Message Template
missingSurveyNotice.headline=No survey is installed in this deployment.
missingSurveyNotice.adminRemedy=Apply a survey definition
missingSurveyNotice.nonAdminRemedy=Ask a system administrator to apply a survey definition before registering subjects.
missingDepartmentDialog.title=No department assigned
missingDepartmentDialog.admin.message=Your account is not assigned to a department, so the console has nothing to show you. Create the first department to continue; it will be assigned to you automatically.
missingDepartmentDialog.admin.action=Add a department
missingDepartmentDialog.user.message=Your account is not assigned to a department, so the console has nothing to show you. Ask an administrator to assign you to one, then sign in again.
missingDepartmentDialog.logout=Logout
missingDepartmentDialog.manual=Open the manual
paginationControls.pageSize=Page size
paginationControls.pageOf=Page {0} of {1}
paginationControls.firstPage=Go to first page
paginationControls.lastPage=Go to last page
paginationControls.nextPage=Go to next page
paginationControls.previousPage=Go to previous page
respondentImportView.title=Import Respondent
respondentImportView.intro=Upload a respondent export file (.elicit) produced by the "Export" action on the Search Subjects grid to import that respondent into this instance.
respondentImportView.upload=Upload
respondentImportView.importSuccessful=Import Successful
respondentImportView.importFailed=Import Failed
respondentImportView.recordsImported=Records imported: {0}
respondentImportView.errors=Errors:
surveyDefinitionApplyView.title=Apply Survey Definition
surveyDefinitionApplyView.intro=Upload a survey definition file (.elicit) exported from the Author tool. You do not need to say whether this is a new survey or a revision of one already here — the file identifies itself, and this instance works out which it is.
surveyDefinitionApplyView.revisionNote=A revision older than the one already installed is refused. Reverting to an earlier revision is a database restore, not an upload.
surveyDefinitionApplyView.upload=Upload
surveyDefinitionApplyView.applyFailed=Apply Failed
surveyDefinitionApplyView.newSurveyInstalled=New Survey Installed
surveyDefinitionApplyView.surveyUpdated=Survey Updated
surveyDefinitionApplyView.surveyKey=Survey key: {0}
surveyDefinitionApplyView.recordsInstalled=Records installed: {0}
surveyDefinitionApplyView.countsHeader=created / versioned / unchanged / retired:
surveyDefinitionApplyView.errors=Errors:
unauthorizedView.title=Access Restricted
unauthorizedView.message=You are authenticated but do not have the required permissions to access this application. Please contact your administrator to request access with the 'elicit_user' or 'elicit_admin' role.
unauthorizedView.logout=Logout
usersView.info.databaseMode=Departments are assigned through this interface. Roles (Admin/User/Importer) are also assigned through this interface, on the Edit User screen.
usersView.info.oidcMode=Users must be configured in the OpenID Connect (OIDC) authentication system with the roles "Admin" or "User". Departments are assigned through this interface.
usersView.grid.username=Username
usersView.grid.firstName=First Name
usersView.grid.lastName=Last Name
usersView.grid.active=Active
usersView.grid.department=Department
usersView.addUser=Add User
loginView.redirecting=Redirecting to sign-in…
logoutView.signingOut=Signing out…
reporting.error.pdf=Failed to generate PDF: {0}
reporting.error.service=Service error: {0}
reporting.error.forbidden=Access forbidden - License validation may have failed. Please check your license configuration.
reporting.error.serviceHttp=Service error (HTTP {0}): {1}
reporting.error.license=License validation failed - {0}
reporting.error.title=Error - {0}
reporting.error.html=<div style="color: red; padding: 20px; border: 1px solid red; background-color: #ffe6e6;"><h3>Report Generation Error</h3><p><strong>Service:</strong> {0}</p><p><strong>Error:</strong> {1}</p>{2}</div>
reporting.error.licenseHint=<p><em>If this is a license error, please ensure your PREMM5 license is valid and properly configured.</em></p>
reporting.error.pdfText=Report Generation Error - Service: {0} - Error: {1}{2}
reporting.error.pdfLicenseHint=- If this is a license error, please ensure your PREMM5 license is valid and properly configured.
reporting.error.unknown=Unknown error
common.listAnd={0} and {1}
common.none=none
common.unknown=unknown
mainLayout.nav.system=System
mainLayout.nav.systemOverview=Overview
mainLayout.nav.systemDatabase=Database
mainLayout.nav.systemBranding=Branding
mainLayout.nav.systemEmail=Email
mainLayout.nav.systemConnections=Connections
mainLayout.nav.systemOidc=OIDC
system.grid.item=Item
system.grid.value=Value
system.grid.state=State
system.grid.detail=Detail
system.badge.ok=OK
system.badge.failed=Failed
system.badge.notChecked=Not checked
system.badge.present=Present
system.badge.absent=Absent
system.milliseconds={0} ms
defaultAccountNotice.instruction.one=The database migrations seeded the default account {0}. Rename it before this deployment goes live: open Admin > Users and change the username, and when elicit.authorization.mode=DATABASE rename the matching account in the identity provider to the same value.
defaultAccountNotice.instruction.many=The database migrations seeded the default accounts {0}. Rename them before this deployment goes live: open Admin > Users and change the username, and when elicit.authorization.mode=DATABASE rename the matching account in the identity provider to the same value.
defaultAccountNotice.openUsers=Open Users
systemOverviewView.pageTitle=System Overview
systemOverviewView.title=System Overview
systemOverviewView.intro=What this deployment is running, whether it is healthy, and the setup work still outstanding. Settings are shown as present or absent, never by value; they are startup configuration and cannot be changed here.
systemOverviewView.running=Running
systemOverviewView.row.application=Application
systemOverviewView.row.version=Version
systemOverviewView.row.built=Built
systemOverviewView.row.profile=Profile
systemOverviewView.row.started=Started
systemOverviewView.row.uptime=Uptime
systemOverviewView.uptime.days={0} d {1} h {2} min
systemOverviewView.uptime.hours={0} h {1} min
systemOverviewView.uptime.minutes={0} min {1} s
systemOverviewView.health=Health
systemOverviewView.area.applicationDatabase=Application database connection
systemOverviewView.area.ownerDatabase=Owner database connection
systemOverviewView.area.branding=Branding
systemOverviewView.area.email=Email
systemOverviewView.area.connections=Connections
systemOverviewView.connectionsSummary.one={0} outbound target configured; open Connections to check it
systemOverviewView.connectionsSummary.many={0} outbound targets configured; open Connections to check them
systemOverviewView.grid.area=Area
systemOverviewView.requiredSettings=Required settings
systemOverviewView.grid.setting=Setting
systemOverviewView.grid.suppliedBy=Supplied by
systemOverviewView.grid.purpose=Purpose
systemOverviewView.setting.datasourcePassword=password of the application database user
systemOverviewView.setting.ownerPassword=password of the owner database user that runs migrations
systemOverviewView.setting.oidcSecret=client secret registered with the identity provider
systemOverviewView.setting.mailerFrom=sender address of every invitation and reminder
systemOverviewView.outstanding=Setup still outstanding
systemOverviewView.nothingOutstanding=Nothing outstanding.
systemOverviewView.warning.noSurvey=No survey is installed. Apply a survey definition before registering subjects.
systemOverviewView.warning.noSenderAddress=No department has a sender address, so invitations have no From address.
systemOverviewView.warning.noAccessCodeTemplate=No message template contains the <ACCESS_CODE> placeholder, so no invitation can carry a survey link.
systemOverviewView.warning.noDepartment=No department exists. Create one on the Departments screen before registering subjects.
systemOverviewView.warning.noMessageTemplate=No message template exists, so registering a subject sends no invitation. Create one on the Message Templates screen.
systemOverviewView.warning.legacySetting=The setting {0} is configured but no longer read; use {1} instead.
systemOverviewView.remedy.users=Open Users
systemOverviewView.remedy.applySurveyDefinition=Apply a survey definition
systemOverviewView.remedy.departments=Open Departments
systemOverviewView.remedy.messageTemplates=Open Message Templates
systemDatabaseView.pageTitle=System Database
systemDatabaseView.title=Database
systemDatabaseView.intro=The application connection serves the console; the owner connection runs the migrations. Readiness probes only the first, so a wrong owner password shows up here and nowhere else.
systemDatabaseView.connections=Connections
systemDatabaseView.grid.connection=Connection
systemDatabaseView.grid.configuredUser=Configured user
systemDatabaseView.grid.connectedAs=Connected as
systemDatabaseView.grid.roundTrip=Round trip
systemDatabaseView.connection.application=Application
systemDatabaseView.connection.owner=Owner
systemDatabaseView.migrations=Migrations
systemDatabaseView.grid.module=Module
systemDatabaseView.grid.historyTable=History table
systemDatabaseView.grid.latestVersion=Latest version
systemDatabaseView.grid.applied=Applied
systemDatabaseView.version.notInstalled=not installed
systemDatabaseView.version.none=none
systemDatabaseView.badge.notInstalled=Not installed
systemDatabaseView.badge.empty=Empty
systemDatabaseView.content=Content
systemDatabaseView.row.durableSequences=Kimball durable-key sequences
systemDatabaseView.row.surveysInstalled=Surveys installed
systemDatabaseView.row.consoleUsers=Console users
systemDatabaseView.row.seededAccounts=Seeded default accounts still present
systemDatabaseView.seededAccounts.none=none
systemDatabaseView.seededAccounts.present={0} (rename them, see Users)
systemDatabaseView.durableSequences.exists=survey.{0} exists
systemDatabaseView.durableSequences.missing=survey.{0} is missing: the Survey module has not applied its V3 migrations, and the Admin and Family History migrations depend on it
systemBrandingView.pageTitle=System Branding
systemBrandingView.title=Branding
systemBrandingView.intro=The brand resolves from the mounted directory first, then the local directory, then the default packaged with the application. Each asset is listed on its own, because a partial mount renders with the wrong fonts and no error.
systemBrandingView.reload=Reload brand
systemBrandingView.reloaded=Brand cache discarded; the result below is freshly resolved.
systemBrandingView.resolution=Resolution
systemBrandingView.row.configuredPath=Configured path (brand.file.system.path)
systemBrandingView.row.localPath=Local path (brand.local.path)
systemBrandingView.path.exists={0} (exists)
systemBrandingView.path.notFound={0} (not found)
systemBrandingView.row.metadataFile=Metadata file
systemBrandingView.row.brandName=Brand name
systemBrandingView.row.organization=Organization
systemBrandingView.row.brandVersion=Brand version
systemBrandingView.row.inUse=In use (cached)
systemBrandingView.inUse={0} [{1}]
systemBrandingView.row.summary=Summary
systemBrandingView.summary.mounted=Mounted brand at {0}
systemBrandingView.summary.mountedNamed=Mounted brand: {0} at {1}
systemBrandingView.summary.local=Local brand directory {0}
systemBrandingView.summary.embedded=Embedded default theme; no brand directory at {0}
systemBrandingView.assets=Assets
systemBrandingView.grid.asset=Asset
systemBrandingView.grid.path=Path
systemBrandingView.grid.source=Source
systemBrandingView.grid.location=Location
systemBrandingView.source.mounted=Mounted
systemBrandingView.source.local=Local
systemBrandingView.source.embedded=Embedded default
systemBrandingView.source.absent=Absent
systemBrandingView.source.unreadable=Unreadable
systemBrandingView.asset.colourStylesheet=colour stylesheet
systemBrandingView.asset.typographyStylesheet=typography stylesheet
systemBrandingView.asset.themeStylesheet=theme stylesheet
systemBrandingView.asset.horizontalLogo=horizontal logo
systemBrandingView.asset.headerIcon=header icon
systemBrandingView.asset.favicon=favicon
systemBrandingView.asset.notFound=not found in any location
systemBrandingView.asset.unreadable={0} exists but cannot be read
systemBrandingView.logoPreview=Logo preview
systemBrandingView.logoAlt=Brand logo
systemEmailView.pageTitle=System Email
systemEmailView.title=Email
systemEmailView.intro=These are the mail settings the service started with; they cannot be changed here. The test message is sent by the same service, sender and timeout that invitations use, so a passing test means invitations will send.
systemEmailView.effectiveSettings=Effective settings
systemEmailView.grid.setting=Setting
systemEmailView.row.sender=Sender (quarkus.mailer.from)
systemEmailView.row.host=Host (quarkus.mailer.host)
systemEmailView.row.port=Port (quarkus.mailer.port)
systemEmailView.row.tls=TLS (quarkus.mailer.tls)
systemEmailView.row.startTls=STARTTLS (quarkus.mailer.start-tls)
systemEmailView.row.authMethods=Authentication methods (quarkus.mailer.auth-methods)
systemEmailView.row.username=Username (quarkus.mailer.username)
systemEmailView.row.password=Password (quarkus.mailer.password)
systemEmailView.row.mock=Mocked (quarkus.mailer.mock)
systemEmailView.value.present=present
systemEmailView.value.absent=absent
systemEmailView.value.relayDefault=not set (relay default)
systemEmailView.summary.noSender=No sender address configured (quarkus.mailer.from)
systemEmailView.summary.from=From {0} via {1}
systemEmailView.summary.fromTls=From {0} via {1} with TLS
systemEmailView.summary.mocked={0} (mocked)
systemEmailView.sendTest=Send a test message
systemEmailView.recipient=Recipient
systemEmailView.recipient.invalid=Enter a valid email address
systemEmailView.send=Send test email
systemEmailView.sendingDisabled=Sending is disabled because no sender address is configured. Set quarkus.mailer.from and restart the service.
systemEmailView.recipientRequired=Enter a valid recipient address first.
systemEmailView.sent=Test email sent to {0} ({1}).
systemEmailView.failed=Test email failed: {0}
systemConnectionsView.pageTitle=System Connections
systemConnectionsView.title=Connections
systemConnectionsView.intro=Each outbound dependency this deployment will call, read from the same stored rows and settings the runtime uses. A check sends one read-only request and gives up after {0} seconds; post-survey actions are never invoked and no report is generated.
systemConnectionsView.empty=No outbound targets are configured or stored yet.
systemConnectionsView.checkAll=Check all
systemConnectionsView.check=Check
systemConnectionsView.grid.dependency=Dependency
systemConnectionsView.grid.source=Source
systemConnectionsView.grid.address=Address
systemConnectionsView.grid.time=Time
systemConnectionsView.group.identityProvider=Identity provider
systemConnectionsView.group.reportService=Report service
systemConnectionsView.group.postSurveyAction=Post-survey action
systemConnectionsView.group.mailRelay=Mail relay
systemConnectionsView.group.telemetryCollector=Telemetry collector
systemConnectionsView.result.timedOut=timed out after {0} s
systemConnectionsView.result.interrupted=interrupted
systemConnectionsView.result.discoveryServed=discovery document served at {0}
systemConnectionsView.result.notDiscovery=HTTP {0} from {1} is not an OIDC discovery document; check the realm address
systemConnectionsView.result.reachableForbidden=reachable, HTTP 403: license validation may have failed
systemConnectionsView.result.reachable=reachable, HTTP {0}
systemConnectionsView.result.noHostPort=address {0} has no host and port
systemConnectionsView.result.connected=connected to {0}:{1}
```
