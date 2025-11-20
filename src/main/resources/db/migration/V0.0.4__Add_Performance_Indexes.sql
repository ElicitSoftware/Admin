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
-- Performance Optimization Indexes for Admin Schema
-- ================================================================================================
-- This migration adds indexes to improve query performance for user management,
-- subject lookups, and messaging operations.
-- ================================================================================================

-- ================================================================================================
-- HIGH PRIORITY INDEXES
-- ================================================================================================

-- Subjects table: Index for email lookup queries
-- Use case: Finding subjects by email address for communication and reporting
CREATE INDEX IF NOT EXISTS idx_subjects_email 
ON survey.subjects(email) 
WHERE email IS NOT NULL;

-- Subjects table: Index for external ID lookups
-- Use case: Finding subjects by external identifier (xid) for integration queries
-- Note: This complements the existing unique constraint on (xid, department_id)
CREATE INDEX IF NOT EXISTS idx_subjects_xid_lookup 
ON survey.subjects(xid) 
WHERE xid IS NOT NULL;

-- Subjects table: Index for recent subject queries
-- Use case: Finding recently created subjects for reporting and monitoring
CREATE INDEX IF NOT EXISTS idx_subjects_created_dt 
ON survey.subjects(created_dt DESC);

-- Messages table: Index for unsent message queries
-- Use case: Finding pending messages that need to be sent
CREATE INDEX IF NOT EXISTS idx_messages_pending 
ON survey.messages(sent_dt) 
WHERE sent_dt IS NULL;

-- Messages table: Composite index for message history lookups
-- Use case: Retrieving all messages for a subject by type
CREATE INDEX IF NOT EXISTS idx_messages_subject_type 
ON survey.messages(subject_id, message_type);

-- User Surveys junction table: Reverse index for finding users by survey
-- Use case: Finding all users who have access to a specific survey
CREATE INDEX IF NOT EXISTS idx_user_surveys_survey 
ON survey.user_surveys(survey_id);

-- User Departments junction table: Reverse index for finding users by department
-- Use case: Finding all users assigned to a specific department
CREATE INDEX IF NOT EXISTS idx_user_departments_dept 
ON survey.user_departments(department_id);

-- ================================================================================================
-- MEDIUM PRIORITY INDEXES
-- ================================================================================================

-- Message Templates table: Composite index for template selection
-- Use case: Finding the appropriate message template for a department and message type
CREATE INDEX IF NOT EXISTS idx_message_templates_lookup 
ON survey.message_templates(department_id, message_type_id);

-- Messages table: Index for sent message queries
-- Use case: Finding recently sent messages for reporting and auditing
CREATE INDEX IF NOT EXISTS idx_messages_sent_dt 
ON survey.messages(sent_dt DESC) 
WHERE sent_dt IS NOT NULL;

-- Subjects table: Composite index for department-based queries
-- Use case: Finding all subjects for a specific department and survey
CREATE INDEX IF NOT EXISTS idx_subjects_dept_survey 
ON survey.subjects(department_id, survey_id);

-- ================================================================================================
-- Index Statistics and Comments
-- ================================================================================================

COMMENT ON INDEX survey.idx_subjects_email IS 
'Optimizes subject lookups by email address for communication and reporting';

COMMENT ON INDEX survey.idx_subjects_xid_lookup IS 
'Optimizes subject lookups by external identifier for integration queries';

COMMENT ON INDEX survey.idx_subjects_created_dt IS 
'Optimizes queries for recently created subjects';

COMMENT ON INDEX survey.idx_messages_pending IS 
'Optimizes queries for unsent messages that need processing';

COMMENT ON INDEX survey.idx_messages_subject_type IS 
'Optimizes message history retrieval by subject and type';

COMMENT ON INDEX survey.idx_user_surveys_survey IS 
'Optimizes finding users with access to specific surveys';

COMMENT ON INDEX survey.idx_user_departments_dept IS 
'Optimizes finding users assigned to specific departments';

COMMENT ON INDEX survey.idx_message_templates_lookup IS 
'Optimizes message template selection by department and type';

COMMENT ON INDEX survey.idx_messages_sent_dt IS 
'Optimizes queries for recently sent messages';

COMMENT ON INDEX survey.idx_subjects_dept_survey IS 
'Optimizes subject lookups by department and survey combination';
