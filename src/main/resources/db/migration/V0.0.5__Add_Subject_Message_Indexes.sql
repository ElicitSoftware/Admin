---
-- ***LICENSE_START***
-- Elicit Admin
-- %%
-- Copyright (C) 2025 The Regents of the University of Michigan - Rogel Cancer Center
-- %%
-- PolyForm Noncommercial License 1.0.0
-- <https://polyformproject.org/licenses/noncommercial/1.0.0>
-- ***LICENSE_END***
---

-- ================================================================================================
-- Performance Indexes for survey.subjects and survey.messages tables
-- ================================================================================================
-- These indexes support Admin-owned tables (subjects, messages) created in V0.0.1.
-- Moved from Survey V008 to ensure proper migration ordering.

-- ================================================================================================
-- CRITICAL PRIORITY - Missing Foreign Key Indexes
-- ================================================================================================

-- Subjects table foreign key indexes (critical for JOINs and referential integrity checks)
CREATE INDEX IF NOT EXISTS idx_subjects_survey_fk
ON survey.subjects(survey_id);

CREATE INDEX IF NOT EXISTS idx_subjects_respondent_fk
ON survey.subjects(respondent_id);

-- Note: idx_subjects_department_fk may already exist, but created explicitly for clarity.
CREATE INDEX IF NOT EXISTS idx_subjects_department_fk
ON survey.subjects(department_id);

-- Messages table foreign key indexes
CREATE INDEX IF NOT EXISTS idx_messages_message_type_fk
ON survey.messages(message_type);

-- ================================================================================================
-- HIGH PRIORITY - Text Search Optimization
-- ================================================================================================

-- Functional indexes for case-insensitive text search.
CREATE INDEX IF NOT EXISTS idx_subjects_firstname_lower
ON survey.subjects(LOWER(firstname));

CREATE INDEX IF NOT EXISTS idx_subjects_lastname_lower
ON survey.subjects(LOWER(lastname));

CREATE INDEX IF NOT EXISTS idx_subjects_middlename_lower
ON survey.subjects(LOWER(middlename))
WHERE middlename IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_subjects_email_lower
ON survey.subjects(LOWER(email))
WHERE email IS NOT NULL;

-- Composite index for common name searches (last name + first name)
CREATE INDEX IF NOT EXISTS idx_subjects_name_search
ON survey.subjects(LOWER(lastname), LOWER(firstname));

-- ================================================================================================
-- MEDIUM PRIORITY - Composite Indexes for Common Query Patterns
-- ================================================================================================

-- Subject lookups by department and date (for reporting and filtered searches)
CREATE INDEX IF NOT EXISTS idx_subjects_dept_date
ON survey.subjects(department_id, created_dt DESC);

-- Subject lookups with xid filtering
CREATE INDEX IF NOT EXISTS idx_subjects_dept_date_xid
ON survey.subjects(department_id, created_dt, xid)
WHERE xid IS NOT NULL;

-- Email lookup with department context
CREATE INDEX IF NOT EXISTS idx_subjects_email_dept
ON survey.subjects(email, department_id)
WHERE email IS NOT NULL;

-- ================================================================================================
-- MEDIUM PRIORITY - Message Processing Optimization
-- ================================================================================================

-- Composite index for unsent messages ordered by creation date
CREATE INDEX IF NOT EXISTS idx_messages_unsent_created
ON survey.messages(created_dt DESC)
WHERE sent_dt IS NULL;

-- Composite index for message history by subject and type
CREATE INDEX IF NOT EXISTS idx_messages_subject_type_sent
ON survey.messages(subject_id, message_type, sent_dt);

-- Composite index for sent messages with recency
CREATE INDEX IF NOT EXISTS idx_messages_sent_recent
ON survey.messages(sent_dt DESC, subject_id)
WHERE sent_dt IS NOT NULL;

-- ================================================================================================
-- LOW PRIORITY - Additional Optimizations
-- ================================================================================================

-- Phone number searches (less common but can be filtered)
CREATE INDEX IF NOT EXISTS idx_subjects_phone
ON survey.subjects(phone)
WHERE phone IS NOT NULL;

-- Department + XID lookup is already covered by unique constraint/index:
-- subjects_xid_department_un (xid, department_id)

-- ================================================================================================
-- Index Statistics and Comments
-- ================================================================================================

COMMENT ON INDEX survey.idx_subjects_firstname_lower IS
'Supports case-insensitive filtering on subject first name';

COMMENT ON INDEX survey.idx_subjects_lastname_lower IS
'Supports case-insensitive filtering on subject last name';

COMMENT ON INDEX survey.idx_subjects_email_lower IS
'Supports case-insensitive filtering on subject email';

COMMENT ON INDEX survey.idx_messages_unsent_created IS
'Optimizes scheduled processing of unsent messages';

COMMENT ON INDEX survey.idx_subjects_dept_date IS
'Optimizes department-scoped subject queries ordered by creation time';

-- After creating indexes, update statistics for query planner.
ANALYZE survey.subjects;
ANALYZE survey.messages;
