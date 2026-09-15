---
-- ***LICENSE_START***
-- Elicit Survey
-- %%
-- Copyright (C) 2025 The Regents of the University of Michigan - Rogel Cancer Center
-- %%
-- PolyForm Noncommercial License 1.0.0
-- <https://polyformproject.org/licenses/noncommercial/1.0.0>
-- ***LICENSE_END***
---

-- =============================================================================
-- TEST-ONLY bootstrap migration (version 0.0.0.1 -> runs before V0.0.1).
-- =============================================================================
-- The Admin db/migration scripts are NOT self-contained: they FK to and GRANT on
-- objects owned by OTHER Elicit modules (surveys, respondents, answers, ...), and
-- reference DB roles that no migration creates. On the shared production database
-- those objects/roles already exist. On a throwaway test container they do not,
-- so this script creates the minimal set needed for db/migration to apply cleanly.
--
-- It only creates objects that db/migration ASSUMES pre-exist. Tables that
-- db/migration creates itself (departments, subjects, users, messages,
-- message_templates, message_types, user_surveys, user_departments, user_roles,
-- and the survey.status view) are intentionally NOT created here.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Roles. The Dev Services `postgres` user is a superuser, so CREATE ROLE
--    works. PostgreSQL has no CREATE ROLE IF NOT EXISTS, so guard each one.
-- -----------------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'surveyadmin_user') THEN
        CREATE ROLE surveyadmin_user;
    END IF;
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'survey_user') THEN
        CREATE ROLE survey_user;
    END IF;
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'elicit_owner') THEN
        CREATE ROLE elicit_owner;
    END IF;
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'surveyreport_user') THEN
        CREATE ROLE surveyreport_user;
    END IF;
END
$$;

-- -----------------------------------------------------------------------------
-- 2. Schemas. `survey` is also auto-created by quarkus.flyway.owner.schemas, so
--    guard it; `surveyreport` is not managed by Flyway and must be created here.
-- -----------------------------------------------------------------------------
CREATE SCHEMA IF NOT EXISTS survey;
CREATE SCHEMA IF NOT EXISTS surveyreport;

-- -----------------------------------------------------------------------------
-- 3. survey.surveys — FK target for subjects/reports/post_survey_actions/
--    user_surveys. Columns match the Survey entity so Panache can read/write it.
-- -----------------------------------------------------------------------------
CREATE SEQUENCE IF NOT EXISTS survey.surveys_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE IF NOT EXISTS survey.surveys
(
    id                  bigint NOT NULL,
    -- Stable, cross-instance-portable identity — see Survey.java's surveyKey field.
    survey_key          uuid NOT NULL,
    display_order       integer,
    name                character varying(255),
    title               character varying(255),
    description         character varying(2000),
    initial_display_key character varying(255),
    post_survey_url     character varying(2000),
    -- Kimball Type 2 (SCD Type 1 for surveys — in-place change tracking only).
    published_by        text,
    published_comment   text,
    CONSTRAINT surveys_pk PRIMARY KEY (id),
    CONSTRAINT surveys_survey_key_un UNIQUE (survey_key)
);

-- -----------------------------------------------------------------------------
-- 4. survey.respondents — FK target for subjects; drives the survey.status view
--    (r.first_access_dt, r.finalized_dt, r.token). Columns match the Respondent
--    entity. Timestamps use timestamptz to match the post-V0.0.7 world.
-- -----------------------------------------------------------------------------
CREATE SEQUENCE IF NOT EXISTS survey.respondents_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE IF NOT EXISTS survey.respondents
(
    id              bigint NOT NULL,
    created_dt      timestamptz DEFAULT CURRENT_TIMESTAMP,
    first_access_dt timestamptz,
    finalized_dt    timestamptz,
    active          boolean DEFAULT true,
    logins          integer DEFAULT 0,
    survey_id       bigint NOT NULL,
    token           character varying(255),
    CONSTRAINT respondents_pk PRIMARY KEY (id),
    CONSTRAINT respondents_surveys_fk FOREIGN KEY (survey_id) REFERENCES survey.surveys (id)
);

-- -----------------------------------------------------------------------------
-- 5. survey.reports and survey.post_survey_actions — GRANT + FK targets AND
--    eager @OneToMany children of Survey, so loading a Survey queries them.
--    Columns match ReportDefinition / PostSurveyAction.
-- -----------------------------------------------------------------------------
CREATE SEQUENCE IF NOT EXISTS survey.reports_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE IF NOT EXISTS survey.reports
(
    id            bigint NOT NULL,
    survey_id     bigint NOT NULL,
    -- Cross-instance-portable identity — see survey_key on surveys above (UC-017).
    report_key    uuid NOT NULL DEFAULT (md5(random()::text || clock_timestamp()::text))::uuid,
    name          character varying(255),
    description   character varying(2000),
    url           character varying(2000),
    display_order integer,
    CONSTRAINT reports_pk PRIMARY KEY (id),
    CONSTRAINT reports_surveys_fk FOREIGN KEY (survey_id) REFERENCES survey.surveys (id)
);

CREATE SEQUENCE IF NOT EXISTS survey.post_survey_actions_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE IF NOT EXISTS survey.post_survey_actions
(
    id                     bigint NOT NULL,
    survey_id              bigint NOT NULL,
    post_survey_action_key uuid NOT NULL DEFAULT (md5(random()::text || clock_timestamp()::text))::uuid,
    name                   character varying(255),
    description            character varying(2000),
    url                    character varying(2000),
    execution_order        integer,
    CONSTRAINT post_survey_actions_pk PRIMARY KEY (id),
    CONSTRAINT psa_surveys_fk FOREIGN KEY (survey_id) REFERENCES survey.surveys (id)
);

-- -----------------------------------------------------------------------------
-- 6. survey.excluded_xids — owned by the Survey module (V006__CREATE_EXCLUDE_XIDS.sql
--    there), not Admin. Admin only reads/writes it via ExcludedXid/TokenService, so
--    it needs the real columns here. Matches Survey's V006 exactly (no department
--    FK — Survey's version doesn't have one either).
-- -----------------------------------------------------------------------------
CREATE SEQUENCE IF NOT EXISTS survey.excluded_xids_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE IF NOT EXISTS survey.excluded_xids
(
    id         integer NOT NULL,
    xid        character varying(255) NOT NULL,
    department integer NOT NULL,
    reason     character varying(500),
    created_dt timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by character varying(100),
    CONSTRAINT excluded_xids_pk PRIMARY KEY (id),
    CONSTRAINT excluded_xids_xid_dept_un UNIQUE (xid, department)
);

-- -----------------------------------------------------------------------------
-- 7. Cross-module tables owned by the Survey module, not Admin. Admin's
--    SurveyDefinitionExportService/ImportService and RespondentExportService/
--    ImportService read/write these directly via native SQL, so (unlike the
--    truly-unused GRANT-only stubs this section used to contain) they need real
--    columns and FK constraints here, not just an `id` column. Copied verbatim
--    from the Survey module's own V001__Create_Survey_Schema.sql, same
--    approach as the survey.excluded_xids fix above. question_types/
--    operator_types/action_types are lookup tables referenced by questions/
--    relationships that Admin's bootstrap didn't need before; each gets one
--    seed row so a minimal test fixture can reference id=1 directly.
-- -----------------------------------------------------------------------------
CREATE SEQUENCE IF NOT EXISTS survey.question_types_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE IF NOT EXISTS survey.question_types
(
    id          integer NOT NULL,
    name        character varying(255),
    data_type   character varying(255),
    description character varying(255),
    CONSTRAINT question_types_pk PRIMARY KEY (id),
    CONSTRAINT question_types_name_un UNIQUE (name)
);
INSERT INTO survey.question_types (id, name, data_type, description)
VALUES (1, 'Text', 'Text', 'Seeded by test bootstrap')
ON CONFLICT (id) DO NOTHING;

CREATE SEQUENCE IF NOT EXISTS survey.operator_types_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE IF NOT EXISTS survey.operator_types
(
    id          integer NOT NULL,
    name        character varying(255),
    description character varying(255),
    symbol      character varying(10),
    CONSTRAINT operator_types_pk PRIMARY KEY (id),
    CONSTRAINT operator_types_name_un UNIQUE (name)
);
INSERT INTO survey.operator_types (id, name, description, symbol)
VALUES (1, 'Equals', 'Seeded by test bootstrap', '=')
ON CONFLICT (id) DO NOTHING;

CREATE SEQUENCE IF NOT EXISTS survey.action_types_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE IF NOT EXISTS survey.action_types
(
    id          integer NOT NULL,
    name        character varying(255),
    description character varying(255),
    CONSTRAINT action_types_pk PRIMARY KEY (id),
    CONSTRAINT action_types_name_un UNIQUE (name)
);
INSERT INTO survey.action_types (id, name, description)
VALUES (1, 'Show', 'Seeded by test bootstrap')
ON CONFLICT (id) DO NOTHING;

-- Kimball Type 2 durable-key sequences (one per structural table gaining a durable id).
CREATE SEQUENCE IF NOT EXISTS survey.select_groups_durable_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS survey.select_items_durable_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS survey.steps_durable_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS survey.sections_durable_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS survey.steps_sections_durable_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS survey.questions_durable_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS survey.sections_questions_durable_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS survey.relationships_durable_seq START WITH 1 INCREMENT BY 1;

CREATE SEQUENCE IF NOT EXISTS survey.select_groups_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE IF NOT EXISTS survey.select_groups
(
    id                 integer NOT NULL,
    survey_id          integer NOT NULL,
    name               character varying(255),
    description        character varying(255),
    data_type          character varying(50) NOT NULL DEFAULT 'Text',
    select_group_id    integer NOT NULL DEFAULT nextval('survey.select_groups_durable_seq'),
    -- Cross-instance-portable identity — see survey_key on surveys above. Given a
    -- DEFAULT here (unlike production) so existing raw-SQL test helpers that don't
    -- mention this column keep working unmodified.
    select_group_key   uuid NOT NULL DEFAULT (md5(random()::text || clock_timestamp()::text))::uuid,
    version            integer NOT NULL DEFAULT 0,
    effective_from     timestamptz DEFAULT '1970-01-01 00:00:00+00',
    effective_to       timestamptz DEFAULT '9999-12-31 23:59:59+00',
    is_draft           boolean NOT NULL DEFAULT false,
    published_by       text,
    published_comment  text,
    CONSTRAINT select_groups_pk PRIMARY KEY (id),
    CONSTRAINT select_groups_id_version_un UNIQUE (select_group_id, version)
);
CREATE UNIQUE INDEX select_groups_name_un
    ON survey.select_groups (survey_id, name)
    WHERE effective_to = '9999-12-31 23:59:59+00';
CREATE UNIQUE INDEX select_groups_one_current_un
    ON survey.select_groups (select_group_id)
    WHERE effective_to = '9999-12-31 23:59:59+00';
CREATE UNIQUE INDEX select_groups_one_draft_un
    ON survey.select_groups (select_group_id)
    WHERE is_draft = true;

CREATE SEQUENCE IF NOT EXISTS survey.select_items_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE IF NOT EXISTS survey.select_items
(
    id                    integer NOT NULL,
    survey_id             integer NOT NULL,
    select_group_id       integer NOT NULL,
    select_group_version  integer NOT NULL DEFAULT 0,
    display_text          character varying(255),
    display_order         integer NOT NULL,
    coded_value            character varying(255),
    select_item_id         integer NOT NULL DEFAULT nextval('survey.select_items_durable_seq'),
    select_item_key        uuid NOT NULL DEFAULT (md5(random()::text || clock_timestamp()::text))::uuid,
    version                integer NOT NULL DEFAULT 0,
    effective_from         timestamptz DEFAULT '1970-01-01 00:00:00+00',
    effective_to           timestamptz DEFAULT '9999-12-31 23:59:59+00',
    is_draft               boolean NOT NULL DEFAULT false,
    published_by           text,
    published_comment      text,
    CONSTRAINT select_items_pk PRIMARY KEY (id),
    CONSTRAINT select_items_id_version_un UNIQUE (select_item_id, version),
    CONSTRAINT select_items_select_group_version_ck CHECK (select_group_version = 0),
    CONSTRAINT select_items_group_fk FOREIGN KEY (select_group_id, select_group_version)
        REFERENCES survey.select_groups (select_group_id, version)
);
CREATE UNIQUE INDEX select_items_display_text_un
    ON survey.select_items (select_group_id, display_text)
    WHERE effective_to = '9999-12-31 23:59:59+00';
CREATE UNIQUE INDEX select_items_one_current_un
    ON survey.select_items (select_item_id)
    WHERE effective_to = '9999-12-31 23:59:59+00';
CREATE UNIQUE INDEX select_items_one_draft_un
    ON survey.select_items (select_item_id)
    WHERE is_draft = true;

CREATE SEQUENCE IF NOT EXISTS survey.steps_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE IF NOT EXISTS survey.steps
(
    id             integer NOT NULL,
    survey_id      integer NOT NULL,
    display_order  integer NOT NULL,
    name           character varying(255),
    dimension_name character varying(50) NOT NULL,
    description    character varying(255),
    step_id            integer NOT NULL DEFAULT nextval('survey.steps_durable_seq'),
    step_key           uuid NOT NULL DEFAULT (md5(random()::text || clock_timestamp()::text))::uuid,
    version            integer NOT NULL DEFAULT 0,
    effective_from     timestamptz DEFAULT '1970-01-01 00:00:00+00',
    effective_to       timestamptz DEFAULT '9999-12-31 23:59:59+00',
    is_draft           boolean NOT NULL DEFAULT false,
    published_by       text,
    published_comment  text,
    CONSTRAINT steps_pk PRIMARY KEY (id),
    CONSTRAINT steps_id_version_un UNIQUE (step_id, version)
);
CREATE UNIQUE INDEX steps_survey_name_un
    ON survey.steps (survey_id, name)
    WHERE effective_to = '9999-12-31 23:59:59+00';
CREATE UNIQUE INDEX steps_survey_display_order
    ON survey.steps (survey_id, display_order)
    WHERE effective_to = '9999-12-31 23:59:59+00';
CREATE UNIQUE INDEX steps_one_current_un
    ON survey.steps (step_id)
    WHERE effective_to = '9999-12-31 23:59:59+00';
CREATE UNIQUE INDEX steps_one_draft_un
    ON survey.steps (step_id)
    WHERE is_draft = true;

CREATE SEQUENCE IF NOT EXISTS survey.sections_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE IF NOT EXISTS survey.sections
(
    id             integer NOT NULL,
    survey_id      integer NOT NULL,
    display_order  integer NOT NULL,
    name           character varying(255),
    dimension_name character varying(50) NOT NULL,
    description    character varying(255),
    section_id         integer NOT NULL DEFAULT nextval('survey.sections_durable_seq'),
    section_key        uuid NOT NULL DEFAULT (md5(random()::text || clock_timestamp()::text))::uuid,
    version            integer NOT NULL DEFAULT 0,
    effective_from     timestamptz DEFAULT '1970-01-01 00:00:00+00',
    effective_to       timestamptz DEFAULT '9999-12-31 23:59:59+00',
    is_draft           boolean NOT NULL DEFAULT false,
    published_by       text,
    published_comment  text,
    CONSTRAINT sections_pk PRIMARY KEY (id),
    CONSTRAINT sections_id_version_un UNIQUE (section_id, version)
);
CREATE UNIQUE INDEX sections_survey_order_un
    ON survey.sections (survey_id, display_order)
    WHERE effective_to = '9999-12-31 23:59:59+00';
CREATE UNIQUE INDEX sections_one_current_un
    ON survey.sections (section_id)
    WHERE effective_to = '9999-12-31 23:59:59+00';
CREATE UNIQUE INDEX sections_one_draft_un
    ON survey.sections (section_id)
    WHERE is_draft = true;

CREATE SEQUENCE IF NOT EXISTS survey.steps_sections_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE IF NOT EXISTS survey.steps_sections
(
    id                    integer               NOT NULL,
    survey_id             integer               NOT NULL,
    step_id               integer               NOT NULL,
    step_version          integer               NOT NULL DEFAULT 0,
    step_display_order    integer               NOT NULL,
    section_id            integer               NOT NULL,
    section_version       integer               NOT NULL DEFAULT 0,
    section_display_order integer               NOT NULL,
    display_key           character varying(34) NOT NULL,
    steps_sections_id      integer NOT NULL DEFAULT nextval('survey.steps_sections_durable_seq'),
    steps_sections_key     uuid NOT NULL DEFAULT (md5(random()::text || clock_timestamp()::text))::uuid,
    version                integer NOT NULL DEFAULT 0,
    effective_from         timestamptz DEFAULT '1970-01-01 00:00:00+00',
    effective_to           timestamptz DEFAULT '9999-12-31 23:59:59+00',
    is_draft               boolean NOT NULL DEFAULT false,
    published_by           text,
    published_comment      text,
    CONSTRAINT steps_sections_pk PRIMARY KEY (id),
    CONSTRAINT steps_sections_id_version_un UNIQUE (steps_sections_id, version),
    CONSTRAINT steps_sections_ref_versions_ck CHECK (step_version = 0 AND section_version = 0),
    CONSTRAINT steps_sections_un UNIQUE (survey_id, display_key),
    CONSTRAINT steps_sections_fk FOREIGN KEY (section_id, section_version)
        REFERENCES survey.sections (section_id, version),
    CONSTRAINT steps_sections_steps_fk FOREIGN KEY (step_id, step_version)
        REFERENCES survey.steps (step_id, version),
    CONSTRAINT steps_sections_survey_fk FOREIGN KEY (survey_id)
        REFERENCES survey.surveys (id)
);
CREATE UNIQUE INDEX steps_sections_one_current_un
    ON survey.steps_sections (steps_sections_id)
    WHERE effective_to = '9999-12-31 23:59:59+00';
CREATE UNIQUE INDEX steps_sections_one_draft_un
    ON survey.steps_sections (steps_sections_id)
    WHERE is_draft = true;

CREATE SEQUENCE IF NOT EXISTS survey.questions_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE IF NOT EXISTS survey.questions
(
    id              integer NOT NULL,
    survey_id       integer NOT NULL,
    type_id         integer NOT NULL,
    text            character varying(8000) NOT NULL,
    short_text      character varying(100),
    tool_tip        character varying(255),
    required        boolean NOT NULL DEFAULT false,
    min_value       integer,
    max_value       integer,
    validation_text character varying(255),
    select_group_id integer,
    select_group_version  integer NOT NULL DEFAULT 0,
    mask            character varying(255),
    placeholder     character varying(255),
    default_value   character varying(255),
    variant         character varying(255),
    question_id           integer NOT NULL DEFAULT nextval('survey.questions_durable_seq'),
    question_key          uuid NOT NULL DEFAULT (md5(random()::text || clock_timestamp()::text))::uuid,
    version               integer NOT NULL DEFAULT 0,
    effective_from        timestamptz DEFAULT '1970-01-01 00:00:00+00',
    effective_to          timestamptz DEFAULT '9999-12-31 23:59:59+00',
    is_draft              boolean NOT NULL DEFAULT false,
    published_by          text,
    published_comment     text,
    CONSTRAINT questions_pk PRIMARY KEY (id),
    CONSTRAINT questions_id_version_un UNIQUE (question_id, version),
    CONSTRAINT questions_select_group_version_ck CHECK (select_group_version = 0),
    CONSTRAINT select_groups_fk FOREIGN KEY (select_group_id, select_group_version)
        REFERENCES survey.select_groups (select_group_id, version),
    CONSTRAINT type_fk FOREIGN KEY (type_id)
        REFERENCES survey.question_types (id)
);
CREATE UNIQUE INDEX questions_one_current_un
    ON survey.questions (question_id)
    WHERE effective_to = '9999-12-31 23:59:59+00';
CREATE UNIQUE INDEX questions_one_draft_un
    ON survey.questions (question_id)
    WHERE is_draft = true;

CREATE SEQUENCE IF NOT EXISTS survey.sections_questions_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE IF NOT EXISTS survey.sections_questions
(
    id            integer NOT NULL,
    survey_id     integer NOT NULL,
    question_id   integer NOT NULL,
    question_version      integer NOT NULL DEFAULT 0,
    section_id    integer NOT NULL,
    section_version       integer NOT NULL DEFAULT 0,
    display_order integer NOT NULL,
    sections_question_id   integer NOT NULL DEFAULT nextval('survey.sections_questions_durable_seq'),
    sections_question_key  uuid NOT NULL DEFAULT (md5(random()::text || clock_timestamp()::text))::uuid,
    version                integer NOT NULL DEFAULT 0,
    effective_from         timestamptz DEFAULT '1970-01-01 00:00:00+00',
    effective_to           timestamptz DEFAULT '9999-12-31 23:59:59+00',
    is_draft               boolean NOT NULL DEFAULT false,
    published_by           text,
    published_comment      text,
    CONSTRAINT sections_questions_pk PRIMARY KEY (id),
    CONSTRAINT sections_questions_id_version_un UNIQUE (sections_question_id, version),
    CONSTRAINT sections_questions_ref_versions_ck CHECK (question_version = 0 AND section_version = 0),
    CONSTRAINT sections_questions_un UNIQUE (survey_id, question_id, section_id, display_order),
    CONSTRAINT sections_questions_question_fk FOREIGN KEY (question_id, question_version)
        REFERENCES survey.questions (question_id, version),
    CONSTRAINT sections_questions_sections_fk FOREIGN KEY (section_id, section_version)
        REFERENCES survey.sections (section_id, version),
    CONSTRAINT sections_questions_survey_fk FOREIGN KEY (survey_id)
        REFERENCES survey.surveys (id)
);
CREATE UNIQUE INDEX sections_questions_one_current_un
    ON survey.sections_questions (sections_question_id)
    WHERE effective_to = '9999-12-31 23:59:59+00';
CREATE UNIQUE INDEX sections_questions_one_draft_un
    ON survey.sections_questions (sections_question_id)
    WHERE is_draft = true;

CREATE SEQUENCE IF NOT EXISTS survey.relationships_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE IF NOT EXISTS survey.relationships
(
    id                       integer NOT NULL,
    survey_id                integer NOT NULL,
    upstream_step_id         integer,
    upstream_step_version    integer NOT NULL DEFAULT 0,
    upstream_sq_id           integer NOT NULL,
    upstream_sq_version      integer NOT NULL DEFAULT 0,
    downstream_step_id       integer,
    downstream_step_version  integer NOT NULL DEFAULT 0,
    downstream_ss_id         integer,
    downstream_ss_version    integer NOT NULL DEFAULT 0,
    downstream_sq_id         integer,
    downstream_sq_version    integer NOT NULL DEFAULT 0,
    operator_id              integer NOT NULL,
    action_id                integer NOT NULL,
    description              character varying(255),
    token                    character varying(10),
    reference_value          character varying(255),
    default_upstream_value   character varying(255),
    override_upstream_value  character varying(255),
    relationship_id          integer NOT NULL DEFAULT nextval('survey.relationships_durable_seq'),
    relationship_key         uuid NOT NULL DEFAULT (md5(random()::text || clock_timestamp()::text))::uuid,
    version                  integer NOT NULL DEFAULT 0,
    effective_from           timestamptz DEFAULT '1970-01-01 00:00:00+00',
    effective_to             timestamptz DEFAULT '9999-12-31 23:59:59+00',
    is_draft                 boolean NOT NULL DEFAULT false,
    published_by             text,
    published_comment        text,
    CONSTRAINT relationships_pk PRIMARY KEY (id),
    CONSTRAINT relationships_id_version_un UNIQUE (relationship_id, version),
    CONSTRAINT relationships_ref_versions_ck CHECK (
        upstream_step_version   = 0 AND
        upstream_sq_version     = 0 AND
        downstream_step_version = 0 AND
        downstream_ss_version   = 0 AND
        downstream_sq_version   = 0
    ),
    CONSTRAINT action_fk FOREIGN KEY (action_id)
        REFERENCES survey.action_types (id),
    CONSTRAINT downstream_s_fk FOREIGN KEY (downstream_ss_id, downstream_ss_version)
        REFERENCES survey.steps_sections (steps_sections_id, version),
    CONSTRAINT downstream_sq_fk FOREIGN KEY (downstream_sq_id, downstream_sq_version)
        REFERENCES survey.sections_questions (sections_question_id, version),
    CONSTRAINT downstream_step_fk FOREIGN KEY (downstream_step_id, downstream_step_version)
        REFERENCES survey.steps (step_id, version),
    CONSTRAINT operator_fk FOREIGN KEY (operator_id)
        REFERENCES survey.operator_types (id),
    CONSTRAINT relationships_survey_fk FOREIGN KEY (survey_id)
        REFERENCES survey.surveys (id),
    CONSTRAINT upstream_sq_fk FOREIGN KEY (upstream_sq_id, upstream_sq_version)
        REFERENCES survey.sections_questions (sections_question_id, version),
    CONSTRAINT upstream_step_fk FOREIGN KEY (upstream_step_id, upstream_step_version)
        REFERENCES survey.steps (step_id, version),
    CONSTRAINT downstream_ck CHECK ((downstream_step_id + downstream_sq_id + downstream_ss_id) > 0)
);
CREATE UNIQUE INDEX relationships_one_current_un
    ON survey.relationships (relationship_id)
    WHERE effective_to = '9999-12-31 23:59:59+00';
CREATE UNIQUE INDEX relationships_one_draft_un
    ON survey.relationships (relationship_id)
    WHERE is_draft = true;

CREATE SEQUENCE IF NOT EXISTS survey.answers_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE IF NOT EXISTS survey.answers
(
    id                     integer                  NOT NULL,
    survey_id              integer                  NOT NULL,
    respondent_id          integer                  NOT NULL,
    step                   integer                  NOT NULL DEFAULT 0,
    step_instance          integer                  NOT NULL DEFAULT 0,
    section                integer,
    section_instance       integer                  NOT NULL DEFAULT 0,
    question_display_order integer,
    question_instance      integer                  NOT NULL DEFAULT 0,
    section_question_id    integer,
    question_id            integer,
    display_key            character varying(34)    NOT NULL,
    display_text           character varying(8000)  NOT NULL,
    text_value             character varying(255),
    deleted                boolean                  NOT NULL DEFAULT false,
    created_dt             timestamptz              NOT NULL DEFAULT CURRENT_TIMESTAMP,
    saved_dt               timestamptz,
    question_version       integer                  NOT NULL DEFAULT 0,
    CONSTRAINT answers_pk PRIMARY KEY (id),
    CONSTRAINT answers_un UNIQUE (respondent_id, display_key),
    CONSTRAINT answers_questions_fk FOREIGN KEY (question_id)
        REFERENCES survey.questions (id),
    CONSTRAINT answers_respondent_fk FOREIGN KEY (respondent_id)
        REFERENCES survey.respondents (id),
    CONSTRAINT answers_section_fk FOREIGN KEY (section)
        REFERENCES survey.sections (id),
    CONSTRAINT answers_step_fk FOREIGN KEY (step)
        REFERENCES survey.steps (id),
    CONSTRAINT answers_survey_fk FOREIGN KEY (survey_id)
        REFERENCES survey.surveys (id),
    CONSTRAINT section_question_id_fk FOREIGN KEY (section_question_id)
        REFERENCES survey.sections_questions (id)
);

CREATE SEQUENCE IF NOT EXISTS survey.dependents_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE IF NOT EXISTS survey.dependents
(
    id              integer NOT NULL,
    respondent_id   integer NOT NULL,
    upstream_id     integer NOT NULL,
    downstream_id   integer NOT NULL,
    relationship_id integer NOT NULL,
    deleted         boolean NOT NULL DEFAULT false,
    CONSTRAINT dependents_pk PRIMARY KEY (id),
    CONSTRAINT dependents_un UNIQUE (respondent_id, upstream_id, downstream_id, relationship_id),
    CONSTRAINT dependents_downstream_fk FOREIGN KEY (downstream_id)
        REFERENCES survey.answers (id),
    CONSTRAINT dependents_relationships_fk FOREIGN KEY (relationship_id)
        REFERENCES survey.relationships (id),
    CONSTRAINT dependents_respondents_fk FOREIGN KEY (respondent_id)
        REFERENCES survey.respondents (id),
    CONSTRAINT dependents_upstream_fk FOREIGN KEY (upstream_id)
        REFERENCES survey.answers (id)
);

CREATE SEQUENCE IF NOT EXISTS survey.dimensions_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE IF NOT EXISTS survey.dimensions
(
    id            integer NOT NULL DEFAULT NEXTVAL('survey.dimensions_seq'),
    -- Global (not survey-scoped) — a dimension reused by name across surveys still
    -- carries one stable cross-instance key, consistent with the reuse-by-name
    -- semantics import/update already apply to this table.
    dimension_key uuid NOT NULL DEFAULT (md5(random()::text || clock_timestamp()::text))::uuid,
    name character varying(50),
    CONSTRAINT dimensions_pk PRIMARY KEY (id),
    CONSTRAINT dimensions_un UNIQUE (name)
);

CREATE SEQUENCE IF NOT EXISTS survey.ontology_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE IF NOT EXISTS survey.ontology
(
    id           integer                NOT NULL,
    survey_id    integer                NOT NULL,
    ontology_key uuid NOT NULL DEFAULT (md5(random()::text || clock_timestamp()::text))::uuid,
    name         character varying(255) NOT NULL,
    tag          character varying(255) NOT NULL,
    dimension    integer,
    CONSTRAINT ontology_pk PRIMARY KEY (id),
    CONSTRAINT ontology_dimensions_fk FOREIGN KEY (dimension) REFERENCES survey.dimensions (id),
    CONSTRAINT ontology_un UNIQUE (name, tag)
);

CREATE SEQUENCE IF NOT EXISTS survey.metadata_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE IF NOT EXISTS survey.metadata
(
    id                    integer NOT NULL,
    survey_id             integer NOT NULL,
    metadata_key          uuid NOT NULL DEFAULT (md5(random()::text || clock_timestamp()::text))::uuid,
    steps_sections_id     integer,
    question_id           integer,
    sections_question_id  integer,
    ontology_id           integer NOT NULL,
    value                 character varying(255),
    CONSTRAINT metadata_pk PRIMARY KEY (id),
    CONSTRAINT metadata_un UNIQUE (steps_sections_id, question_id, sections_question_id, ontology_id, value),
    CONSTRAINT metadata_ontology_fk FOREIGN KEY (ontology_id)
        REFERENCES survey.ontology (id),
    CONSTRAINT metadata_survey_fk FOREIGN KEY (survey_id)
        REFERENCES survey.surveys (id),
    CONSTRAINT metadata_element_ck CHECK ((steps_sections_id + question_id + sections_question_id) > 0)
);

CREATE SEQUENCE IF NOT EXISTS survey.respondent_psa_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE IF NOT EXISTS survey.respondent_psa
(
    id                    integer      NOT NULL,
    respondent_id         integer      NOT NULL,
    post_survey_action_id integer      NOT NULL,
    tries                 integer      NOT NULL DEFAULT 0,
    status                varchar(255) NOT NULL,
    error_msg             varchar(255),
    created_dt            timestamptz  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    uploaded_dt           timestamptz,
    CONSTRAINT respondent_psa_pk PRIMARY KEY (id),
    CONSTRAINT respondent_psa_psa_fk FOREIGN KEY (post_survey_action_id) REFERENCES survey.post_survey_actions (id),
    CONSTRAINT respondent_psa_respondent_fk FOREIGN KEY (respondent_id) REFERENCES survey.respondents (id),
    CONSTRAINT respondent_psa_un UNIQUE (respondent_id, post_survey_action_id)
);

-- -----------------------------------------------------------------------------
-- 8. surveyreport.fact_respondents — GRANT target only (V0.0.6/V0.0.8).
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS surveyreport.fact_respondents (respondent_id bigint);

-- -----------------------------------------------------------------------------
-- 9. Let the application roles use the schemas (postgres owns every object here,
--    so the later per-object GRANTs succeed regardless).
-- -----------------------------------------------------------------------------
GRANT USAGE ON SCHEMA survey TO surveyadmin_user, survey_user;
GRANT USAGE ON SCHEMA surveyreport TO surveyadmin_user, surveyreport_user;

-- -----------------------------------------------------------------------------
-- 10. Seed survey.surveys(id=1). V0.0.3 dev-data (which runs in test because it
--    lives under db/migration) inserts user_surveys(survey_id=1); without this
--    row that FK fails and every @QuarkusTest fails at boot.
-- -----------------------------------------------------------------------------
INSERT INTO survey.surveys (id, survey_key, display_order, name, title, description)
VALUES (1, '00000000-0000-0000-0000-000000000001', 1, 'Test Survey', 'Test Survey', 'Seeded by test bootstrap')
ON CONFLICT (id) DO NOTHING;
ALTER SEQUENCE survey.surveys_seq RESTART WITH 2;
