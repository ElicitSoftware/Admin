# Quickstart: Kimball Type 2 SCD Support — Admin v3.0.0

## Prerequisites

- Survey's Kimball Flyway migrations (V011–V019) must be applied to the shared `survey` database before Admin's V0.0.9 can run.
- PostgreSQL 14+ accessible via the connection configured in `application.properties`.
- Java 17 SDK, Maven 3.9+.

---

## Step 1 — Apply Admin V0.0.9 Migration

```bash
cd /Users/matthew/Dev/Elicit/Admin

# Verify Survey migrations are already applied
psql -U postgres -d survey_db -c \
  "SELECT version FROM flyway_schema_history WHERE script LIKE '%Kimball%' ORDER BY version;"
# Expected: V011 through V019 listed as 'Success'

# Run Admin migrations (V0.0.9 will grant durable sequence access)
./mvnw flyway:migrate \
  -Dquarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/survey_db

# Verify
./mvnw flyway:info \
  -Dquarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/survey_db
```

Expected: V0.0.9 shows `Success`.

---

## Step 2 — Verify Sequence Access

```sql
-- Connect as surveyadmin_user and test sequence access
SET ROLE surveyadmin_user;

SELECT nextval('survey.questions_durable_seq');
SELECT nextval('survey.steps_durable_seq');
-- Repeat for all 8 sequences; all should return a value without permission error.
```

---

## Step 3 — Test Survey Definition Export (V2)

Start the Admin application in dev mode:

```bash
./mvnw quarkus:dev
```

Trigger an export via the Admin UI or REST endpoint. Open the resulting file and verify:

```bash
# Check file header
head -5 /path/to/exported-survey.txt
# Expected lines:
# # version: ELICIT_SURVEY_EXPORT_V2
# # generated: <timestamp>
# # schema_version: V2_KIMBALL_TYPE2

# Check that step_id (durable integer) appears as source_id
grep -A2 "^# steps" /path/to/exported-survey.txt | head -10
```

---

## Step 4 — Test Survey Definition Import (V2 Round-Trip)

```bash
# Import the V2 export into a fresh Kimball-migrated test database
# (use Admin UI or call the import endpoint with the file)

# After import, verify FK integrity in the target database:
psql -U postgres -d survey_test -c \
  "SELECT COUNT(*) FROM survey.steps_sections ss
   LEFT JOIN survey.steps s ON ss.step_id = s.step_id
   WHERE s.step_id IS NULL;"
# Expected: 0

psql -U postgres -d survey_test -c \
  "SELECT COUNT(*) FROM survey.questions WHERE effective_to = '9999-12-31 23:59:59+00';"
# Expected: same count as rows in the export file's questions section
```

---

## Step 5 — Test V1 Backward-Compatible Import

```bash
# Use a pre-Kimball V1 export file (no schema_version header line)
# Import via Admin UI or REST endpoint

# Verify defaults were applied:
psql -U postgres -d survey_test -c \
  "SELECT version, effective_from, effective_to, is_draft
   FROM survey.questions LIMIT 5;"
# Expected: version=0, effective_from='1970-01-01 00:00:00+00',
#           effective_to='9999-12-31 23:59:59+00', is_draft=false
```

---

## Step 6 — Test Respondent Export/Import with `question_version`

```bash
# Export a known respondent
# Inspect the answers section of the export file:
grep -A2 "^# answers" /path/to/respondent-export.txt | head -5
# Expected: each pipe-delimited row has 7 fields; last field is question_version (e.g. 0)

# Import the export into the target database and verify:
psql -U postgres -d survey_test -c \
  "SELECT question_version FROM survey.answers WHERE respondent_id = <id>;"
# Expected: matches values from the export file
```

---

## Run All Tests

```bash
cd /Users/matthew/Dev/Elicit/Admin
./mvnw verify
```
