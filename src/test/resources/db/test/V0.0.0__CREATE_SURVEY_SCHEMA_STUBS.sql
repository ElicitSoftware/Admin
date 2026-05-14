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
-- SURVEY SCHEMA STUBS for @QuarkusTest
-- =============================================================================
-- Flyway version 0.0.0 sorts before V0.0.1 so these stubs run first.
-- Creates all survey-schema tables that Admin migrations FK-reference or that
-- the export/import services query via native SQL.
-- The surveyreport schema stub is also included so V0.0.2 grants succeed.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- surveyreport schema stub (needed for V0.0.2 GRANT on fact_respondents)
-- -----------------------------------------------------------------------------
CREATE SCHEMA IF NOT EXISTS surveyreport;
CREATE TABLE IF NOT EXISTS surveyreport.fact_respondents (
    id bigint NOT NULL,
    CONSTRAINT fact_respondents_pk PRIMARY KEY (id)
);

-- -----------------------------------------------------------------------------
-- Dimensions (no FK dependencies)
-- -----------------------------------------------------------------------------
CREATE SEQUENCE survey.dimensions_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE IF NOT EXISTS survey.dimensions (
    id      bigint NOT NULL,
    name    character varying(255) NOT NULL,
    CONSTRAINT dimensions_pk PRIMARY KEY (id),
    CONSTRAINT dimensions_name_un UNIQUE (name)
);

-- -----------------------------------------------------------------------------
-- Surveys
-- -----------------------------------------------------------------------------
CREATE SEQUENCE survey.surveys_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE IF NOT EXISTS survey.surveys (
    id                    bigint NOT NULL,
    name                  character varying(255) NOT NULL,
    display_order         integer NOT NULL DEFAULT 0,
    title                 character varying(255),
    description           character varying(2000),
    initial_display_key   character varying(255),
    post_survey_url       character varying(2000),
    CONSTRAINT surveys_pk PRIMARY KEY (id)
);

-- Seed survey id=1 so V0.0.3 dev-data user_surveys INSERT succeeds
INSERT INTO survey.surveys (id, name, display_order, title, description, initial_display_key, post_survey_url)
VALUES (1, 'Dev Stub Survey', 0, 'Dev Stub', null, 'S1', null);
SELECT setval('survey.surveys_seq', 1);

-- -----------------------------------------------------------------------------
-- Select Groups
-- -----------------------------------------------------------------------------
CREATE SEQUENCE survey.select_groups_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE IF NOT EXISTS survey.select_groups (
    id          bigint NOT NULL,
    survey_id   bigint NOT NULL,
    name        character varying(255) NOT NULL,
    description character varying(2000),
    data_type   character varying(100) NOT NULL,
    CONSTRAINT select_groups_pk PRIMARY KEY (id),
    CONSTRAINT select_groups_surveys_fk FOREIGN KEY (survey_id) REFERENCES survey.surveys (id)
);

-- -----------------------------------------------------------------------------
-- Select Items
-- -----------------------------------------------------------------------------
CREATE SEQUENCE survey.select_items_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE IF NOT EXISTS survey.select_items (
    id            bigint NOT NULL,
    survey_id     bigint NOT NULL,
    group_id      bigint NOT NULL,
    display_text  character varying(255) NOT NULL,
    display_order integer NOT NULL DEFAULT 0,
    coded_value   character varying(255),
    CONSTRAINT select_items_pk PRIMARY KEY (id),
    CONSTRAINT select_items_surveys_fk FOREIGN KEY (survey_id) REFERENCES survey.surveys (id),
    CONSTRAINT select_items_groups_fk  FOREIGN KEY (group_id)  REFERENCES survey.select_groups (id)
);

-- -----------------------------------------------------------------------------
-- Steps
-- -----------------------------------------------------------------------------
CREATE SEQUENCE survey.steps_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE IF NOT EXISTS survey.steps (
    id             bigint NOT NULL,
    survey_id      bigint NOT NULL,
    display_order  integer NOT NULL DEFAULT 0,
    name           character varying(255) NOT NULL,
    dimension_name character varying(255),
    description    character varying(2000),
    CONSTRAINT steps_pk PRIMARY KEY (id),
    CONSTRAINT steps_surveys_fk FOREIGN KEY (survey_id) REFERENCES survey.surveys (id)
);

-- -----------------------------------------------------------------------------
-- Sections
-- -----------------------------------------------------------------------------
CREATE SEQUENCE survey.sections_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE IF NOT EXISTS survey.sections (
    id             bigint NOT NULL,
    survey_id      bigint NOT NULL,
    display_order  integer NOT NULL DEFAULT 0,
    name           character varying(255) NOT NULL,
    dimension_name character varying(255),
    description    character varying(2000),
    CONSTRAINT sections_pk PRIMARY KEY (id),
    CONSTRAINT sections_surveys_fk FOREIGN KEY (survey_id) REFERENCES survey.surveys (id)
);

-- -----------------------------------------------------------------------------
-- Steps-Sections join
-- -----------------------------------------------------------------------------
CREATE SEQUENCE survey.steps_sections_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE IF NOT EXISTS survey.steps_sections (
    id                   bigint NOT NULL,
    survey_id            bigint NOT NULL,
    step_id              bigint NOT NULL,
    step_display_order   integer NOT NULL DEFAULT 0,
    section_id           bigint NOT NULL,
    section_display_order integer NOT NULL DEFAULT 0,
    display_key          character varying(255) NOT NULL,
    CONSTRAINT steps_sections_pk PRIMARY KEY (id),
    CONSTRAINT steps_sections_surveys_fk  FOREIGN KEY (survey_id)  REFERENCES survey.surveys (id),
    CONSTRAINT steps_sections_steps_fk    FOREIGN KEY (step_id)    REFERENCES survey.steps (id),
    CONSTRAINT steps_sections_sections_fk FOREIGN KEY (section_id) REFERENCES survey.sections (id)
);

-- -----------------------------------------------------------------------------
-- Question Types (static lookup)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS survey.question_types (
    id   bigint NOT NULL,
    name character varying(100) NOT NULL,
    CONSTRAINT question_types_pk PRIMARY KEY (id)
);
INSERT INTO survey.question_types (id, name) VALUES (1, 'text');
INSERT INTO survey.question_types (id, name) VALUES (2, 'select');
INSERT INTO survey.question_types (id, name) VALUES (3, 'date');

-- -----------------------------------------------------------------------------
-- Questions
-- -----------------------------------------------------------------------------
CREATE SEQUENCE survey.questions_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE IF NOT EXISTS survey.questions (
    id               bigint NOT NULL,
    survey_id        bigint NOT NULL,
    type_id          bigint NOT NULL,
    text             text NOT NULL,
    short_text       character varying(255),
    tool_tip         character varying(255),
    required         boolean NOT NULL DEFAULT false,
    min_value        character varying(255),
    max_value        character varying(255),
    validation_text  character varying(255),
    select_group_id  bigint,
    mask             character varying(255),
    placeholder      character varying(255),
    default_value    character varying(255),
    variant          character varying(255),
    CONSTRAINT questions_pk PRIMARY KEY (id),
    CONSTRAINT questions_surveys_fk       FOREIGN KEY (survey_id)       REFERENCES survey.surveys (id),
    CONSTRAINT questions_types_fk         FOREIGN KEY (type_id)         REFERENCES survey.question_types (id),
    CONSTRAINT questions_select_groups_fk FOREIGN KEY (select_group_id) REFERENCES survey.select_groups (id)
);

-- -----------------------------------------------------------------------------
-- Sections-Questions join
-- -----------------------------------------------------------------------------
CREATE SEQUENCE survey.sections_questions_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE IF NOT EXISTS survey.sections_questions (
    id            bigint NOT NULL,
    survey_id     bigint NOT NULL,
    question_id   bigint NOT NULL,
    section_id    bigint NOT NULL,
    display_order integer NOT NULL DEFAULT 0,
    CONSTRAINT sections_questions_pk PRIMARY KEY (id),
    CONSTRAINT sections_questions_surveys_fk   FOREIGN KEY (survey_id)   REFERENCES survey.surveys (id),
    CONSTRAINT sections_questions_questions_fk FOREIGN KEY (question_id) REFERENCES survey.questions (id),
    CONSTRAINT sections_questions_sections_fk  FOREIGN KEY (section_id)  REFERENCES survey.sections (id)
);

-- -----------------------------------------------------------------------------
-- Operators (static lookup)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS survey.operators (
    id   bigint NOT NULL,
    name character varying(100) NOT NULL,
    CONSTRAINT operators_pk PRIMARY KEY (id)
);
INSERT INTO survey.operators (id, name) VALUES (1, 'equals');
INSERT INTO survey.operators (id, name) VALUES (2, 'not_equals');

-- -----------------------------------------------------------------------------
-- Actions (static lookup)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS survey.actions (
    id   bigint NOT NULL,
    name character varying(100) NOT NULL,
    CONSTRAINT actions_pk PRIMARY KEY (id)
);
INSERT INTO survey.actions (id, name) VALUES (1, 'show');
INSERT INTO survey.actions (id, name) VALUES (2, 'hide');

-- -----------------------------------------------------------------------------
-- Relationships
-- -----------------------------------------------------------------------------
CREATE SEQUENCE survey.relationships_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE IF NOT EXISTS survey.relationships (
    id                       bigint NOT NULL,
    survey_id                bigint NOT NULL,
    upstream_step_id         bigint NOT NULL,
    upstream_sq_id           bigint,
    downstream_step_id       bigint,
    downstream_s_id          bigint,
    downstream_sq_id         bigint,
    operator_id              bigint NOT NULL,
    action_id                bigint NOT NULL,
    description              character varying(2000),
    token                    character varying(255),
    reference_value          character varying(255),
    default_upstream_value   character varying(255),
    override_upstream_value  character varying(255),
    CONSTRAINT relationships_pk PRIMARY KEY (id),
    CONSTRAINT relationships_surveys_fk     FOREIGN KEY (survey_id)        REFERENCES survey.surveys (id),
    CONSTRAINT relationships_up_step_fk     FOREIGN KEY (upstream_step_id) REFERENCES survey.steps (id),
    CONSTRAINT relationships_up_sq_fk       FOREIGN KEY (upstream_sq_id)   REFERENCES survey.sections_questions (id),
    CONSTRAINT relationships_dn_step_fk     FOREIGN KEY (downstream_step_id) REFERENCES survey.steps (id),
    CONSTRAINT relationships_dn_s_fk        FOREIGN KEY (downstream_s_id)  REFERENCES survey.sections (id),
    CONSTRAINT relationships_dn_sq_fk       FOREIGN KEY (downstream_sq_id) REFERENCES survey.sections_questions (id),
    CONSTRAINT relationships_operators_fk   FOREIGN KEY (operator_id)      REFERENCES survey.operators (id),
    CONSTRAINT relationships_actions_fk     FOREIGN KEY (action_id)        REFERENCES survey.actions (id)
);

-- -----------------------------------------------------------------------------
-- Reports
-- -----------------------------------------------------------------------------
CREATE SEQUENCE survey.reports_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE IF NOT EXISTS survey.reports (
    id            bigint NOT NULL,
    survey_id     bigint NOT NULL,
    name          character varying(255) NOT NULL,
    description   character varying(2000),
    url           character varying(2000) NOT NULL,
    display_order integer NOT NULL DEFAULT 0,
    CONSTRAINT reports_pk PRIMARY KEY (id),
    CONSTRAINT reports_surveys_fk FOREIGN KEY (survey_id) REFERENCES survey.surveys (id)
);

-- -----------------------------------------------------------------------------
-- Post-Survey Actions
-- -----------------------------------------------------------------------------
CREATE SEQUENCE survey.post_survey_actions_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE IF NOT EXISTS survey.post_survey_actions (
    id              bigint NOT NULL,
    survey_id       bigint NOT NULL,
    name            character varying(255) NOT NULL,
    description     character varying(2000),
    url             character varying(2000) NOT NULL,
    execution_order integer NOT NULL DEFAULT 0,
    CONSTRAINT post_survey_actions_pk PRIMARY KEY (id),
    CONSTRAINT post_survey_actions_surveys_fk FOREIGN KEY (survey_id) REFERENCES survey.surveys (id)
);

-- -----------------------------------------------------------------------------
-- Ontology
-- -----------------------------------------------------------------------------
CREATE SEQUENCE survey.ontology_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE IF NOT EXISTS survey.ontology (
    id         bigint NOT NULL,
    survey_id  bigint NOT NULL,
    name       character varying(255) NOT NULL,
    tag        character varying(255) NOT NULL,
    dimension  bigint,
    CONSTRAINT ontology_pk PRIMARY KEY (id),
    CONSTRAINT ontology_surveys_fk    FOREIGN KEY (survey_id) REFERENCES survey.surveys (id),
    CONSTRAINT ontology_dimensions_fk FOREIGN KEY (dimension) REFERENCES survey.dimensions (id)
);

-- -----------------------------------------------------------------------------
-- Metadata
-- -----------------------------------------------------------------------------
CREATE SEQUENCE survey.metadata_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE IF NOT EXISTS survey.metadata (
    id                  bigint NOT NULL,
    survey_id           bigint NOT NULL,
    step_section_id     bigint,
    question_id         bigint,
    section_question_id bigint,
    ontology_id         bigint NOT NULL,
    value               character varying(2000) NOT NULL,
    CONSTRAINT metadata_pk PRIMARY KEY (id),
    CONSTRAINT metadata_surveys_fk  FOREIGN KEY (survey_id)  REFERENCES survey.surveys (id),
    CONSTRAINT metadata_ontology_fk FOREIGN KEY (ontology_id) REFERENCES survey.ontology (id)
);

-- -----------------------------------------------------------------------------
-- Respondents
-- -----------------------------------------------------------------------------
CREATE SEQUENCE survey.respondents_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE IF NOT EXISTS survey.respondents (
    id               bigint NOT NULL,
    survey_id        bigint NOT NULL,
    token            character varying(255) NOT NULL,
    active           boolean NOT NULL DEFAULT true,
    logins           integer NOT NULL DEFAULT 0,
    created_dt       timestamptz NOT NULL DEFAULT now(),
    first_access_dt  timestamptz,
    finalized_dt     timestamptz,
    CONSTRAINT respondents_pk PRIMARY KEY (id),
    CONSTRAINT respondents_surveys_fk FOREIGN KEY (survey_id) REFERENCES survey.surveys (id)
);

-- -----------------------------------------------------------------------------
-- Answers
-- -----------------------------------------------------------------------------
CREATE SEQUENCE survey.answers_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE IF NOT EXISTS survey.answers (
    id                    bigint NOT NULL,
    respondent_id         bigint NOT NULL,
    survey_id             bigint NOT NULL,
    step                  integer NOT NULL,
    step_instance         integer NOT NULL DEFAULT 1,
    section               integer NOT NULL,
    section_instance      integer NOT NULL DEFAULT 1,
    question_display_order integer NOT NULL DEFAULT 0,
    question_instance     integer NOT NULL DEFAULT 1,
    section_question_id   bigint NOT NULL,
    question_id           bigint NOT NULL,
    display_key           character varying(255) NOT NULL,
    display_text          character varying(255),
    text_value            text,
    deleted               boolean NOT NULL DEFAULT false,
    created_dt            timestamptz NOT NULL DEFAULT now(),
    saved_dt              timestamptz,
    CONSTRAINT answers_pk PRIMARY KEY (id),
    CONSTRAINT answers_respondents_fk       FOREIGN KEY (respondent_id)       REFERENCES survey.respondents (id),
    CONSTRAINT answers_surveys_fk           FOREIGN KEY (survey_id)           REFERENCES survey.surveys (id),
    CONSTRAINT answers_sections_questions_fk FOREIGN KEY (section_question_id) REFERENCES survey.sections_questions (id),
    CONSTRAINT answers_questions_fk         FOREIGN KEY (question_id)         REFERENCES survey.questions (id)
);

-- -----------------------------------------------------------------------------
-- Dependents
-- -----------------------------------------------------------------------------
CREATE SEQUENCE survey.dependents_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE IF NOT EXISTS survey.dependents (
    id              bigint NOT NULL,
    respondent_id   bigint NOT NULL,
    upstream_id     bigint NOT NULL,
    downstream_id   bigint NOT NULL,
    relationship_id bigint NOT NULL,
    deleted         boolean NOT NULL DEFAULT false,
    CONSTRAINT dependents_pk PRIMARY KEY (id),
    CONSTRAINT dependents_respondents_fk   FOREIGN KEY (respondent_id)   REFERENCES survey.respondents (id),
    CONSTRAINT dependents_upstream_fk      FOREIGN KEY (upstream_id)     REFERENCES survey.answers (id),
    CONSTRAINT dependents_downstream_fk    FOREIGN KEY (downstream_id)   REFERENCES survey.answers (id),
    CONSTRAINT dependents_relationships_fk FOREIGN KEY (relationship_id) REFERENCES survey.relationships (id)
);

-- -----------------------------------------------------------------------------
-- Respondent PSA (Post-Survey Actions log)
-- -----------------------------------------------------------------------------
CREATE SEQUENCE survey.respondent_psa_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE IF NOT EXISTS survey.respondent_psa (
    id                    bigint NOT NULL,
    respondent_id         bigint NOT NULL,
    post_survey_action_id bigint NOT NULL,
    tries                 integer NOT NULL DEFAULT 0,
    status                character varying(100),
    error_msg             character varying(2000),
    created_dt            timestamptz NOT NULL DEFAULT now(),
    uploaded_dt           timestamptz,
    CONSTRAINT respondent_psa_pk PRIMARY KEY (id),
    CONSTRAINT respondent_psa_respondents_fk FOREIGN KEY (respondent_id) REFERENCES survey.respondents (id)
);
