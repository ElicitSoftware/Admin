--------------------------------
-- Message Types
--------------------------------
CREATE SEQUENCE survey.message_types_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE survey.message_types
(
    id   bigint                NOT NULL,
    name character varying(25) NOT NULL,
    CONSTRAINT message_type_pk PRIMARY KEY (id),
    CONSTRAINT message_type_un UNIQUE (name)
);
--------------------------------
-- Departments
--------------------------------
CREATE SEQUENCE survey.departments_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE IF NOT EXISTS survey.departments
(
    id
    bigint
    NOT
    NULL,
    name
    character
    varying
(
    255
) NOT NULL,
    code character varying
(
    100
),
    default_message_type_id character varying
(
    100
) NOT NULL DEFAULT '1',
    notification_emails character varying
(
    2000
),
    from_email character varying
(
    50
) NOT NULL,
    CONSTRAINT department_pk PRIMARY KEY
(
    id
),
    CONSTRAINT department_code_un UNIQUE
(
    code
),
    CONSTRAINT department_name_un UNIQUE
(
    name
)
    );
--------------------------------
-- Subjects
--------------------------------
CREATE SEQUENCE survey.subjects_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE survey.subjects
(
    id            bigint                              NOT NULL,
    xid           character varying(50),
    firstName     character varying(50)               NOT NULL,
    lastName      character varying(50)               NOT NULL,
    middleName    character varying(50),
    dob           date,
    email         character varying(255),
    phone         character varying(20),
    department_id bigint                              NOT NULL,
    survey_id     bigint                              NOT NULL,
    respondent_id bigint                              NOT NULL,
    created_dt    TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT subjects_pk PRIMARY KEY (id),
    CONSTRAINT subjects_xid_department_un UNIQUE (xid, department_id),
    CONSTRAINT subjects_surveys_fk FOREIGN KEY (survey_id) REFERENCES survey.surveys (id),
    CONSTRAINT subjects_respondent_fk FOREIGN KEY (respondent_id) REFERENCES survey.respondents (id),
    CONSTRAINT subjects_departments_fk FOREIGN KEY (department_id) REFERENCES survey.departments (id)
);
--------------------------------
-- Users
--------------------------------
CREATE SEQUENCE survey.users_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE survey.users
(
    id         bigint                 NOT NULL,
    username   character varying(100) NOT NULL,
    first_name character varying(255) NOT NULL,
    last_name  character varying(255) NOT NULL,
    active     boolean DEFAULT true   NOT NULL,
    CONSTRAINT USERS_PK PRIMARY KEY (id),
    CONSTRAINT users_username_un UNIQUE (username)
);
--------------------------------
-- Users Surveys
--------------------------------
CREATE TABLE survey.user_surveys
(
    user_id   bigint NOT NULL,
    survey_id bigint NOT NULL,
    CONSTRAINT user_surveys_pk PRIMARY KEY (user_id, survey_id),
    CONSTRAINT user_surveys_users_fk FOREIGN KEY (user_id) REFERENCES survey.users (id),
    CONSTRAINT user_surveys_surveys_fk FOREIGN KEY (survey_id) REFERENCES survey.surveys (id)
);
--------------------------------
-- Roles
--------------------------------
CREATE SEQUENCE survey.roles_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE survey.roles
(
    id   bigint                 NOT NULL,
    name character varying(255) NOT NULL,
    CONSTRAINT roles_pk PRIMARY KEY (id),
    CONSTRAINT role_name_un UNIQUE (name)
);
--------------------------------
-- User Roles
--------------------------------
CREATE TABLE survey.user_roles
(
    user_id bigint NOT NULL,
    role_id bigint NOT NULL,
    CONSTRAINT user_roles_pk PRIMARY KEY (user_id, role_id),
    CONSTRAINT user_roles_roles_fk FOREIGN KEY (role_id) REFERENCES survey.roles (id),
    CONSTRAINT user_roles_users_fk FOREIGN KEY (user_id) REFERENCES survey.users (id)
);
--------------------------------
-- User Departments
--------------------------------
CREATE TABLE survey.user_departments
(
    department_id bigint NOT NULL,
    user_id       bigint NOT NULL,
    CONSTRAINT user_departments_pk PRIMARY KEY (department_id, user_id),
    CONSTRAINT user_departments_users_fk FOREIGN KEY (user_id) REFERENCES survey.users (id),
    CONSTRAINT user_departments_dep_fk FOREIGN KEY (department_id) REFERENCES survey.departments (id)
);
--------------------------------
-- Message Templates
--------------------------------
CREATE SEQUENCE survey.message_templates_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE survey.message_templates
(
    id              bigint        NOT NULL,
    department_id   bigint        NOT NULL,
    message_type_id bigint        NOT NULL,
    message         varchar(6000) NOT NULL,
    subject         character varying(255),
    cron_schedule   character varying(255),
    mime_type       character varying(100) DEFAULT 'text/plain',
    CONSTRAINT message_templates_type_pk PRIMARY KEY (id),
    CONSTRAINT message_templates_dep_fk FOREIGN KEY (department_id) REFERENCES survey.departments (id),
    CONSTRAINT message_templates_type_fk FOREIGN KEY (message_type_id) REFERENCES survey.message_types (id),
    CONSTRAINT message_templates_un UNIQUE (department_id, message_type_id)
);
--------------------------------
-- Messages
--------------------------------
CREATE SEQUENCE survey.messages_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE survey.messages
(
    id              bigint                              NOT NULL,
    respondent_id   bigint                              NOT NULL,
    department_id   bigint                              NOT NULL,
    sms_sid         character varying(255),
    smtp_sid        character varying(255),
    message_type_id bigint                              NOT NULL,
    message_body    varchar(6000),
    created_dt      TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT messages_pk PRIMARY KEY (id),
    CONSTRAINT message_sms_sid_un UNIQUE (sms_sid),
    CONSTRAINT message_smtp_sid_un UNIQUE (smtp_sid),
    CONSTRAINT messages_token_fk FOREIGN KEY (respondent_id) REFERENCES survey.subjects (id),
    CONSTRAINT messages_department_fk FOREIGN KEY (department_id) REFERENCES survey.departments (id),
    CONSTRAINT messages_message_type_fk FOREIGN KEY (message_type_id) REFERENCES survey.message_types (id)
);
--------------------------------
-- Status
--------------------------------
CREATE VIEW survey.status AS
(
SELECT s.id,
       s.survey_id,
       s.firstname,
       s.lastname,
       s.dob,
       s.email,
       s.middlename,
       s.phone,
       s.xid,
       s.created_dt,
       r.finalized_dt,
       d.name as department_name,
       d.id as department_id,
       r.token,
       CASE
           WHEN ((r.first_access_dt IS NULL) AND (r.finalized_dt IS NULL)) THEN 'Not Started'::text
           WHEN ((r.first_access_dt IS NOT NULL) AND (r.finalized_dt IS NULL)) THEN 'In Progress'::text
           ELSE 'Finished'::text
           END AS status
FROM survey.respondents r
         JOIN survey.subjects s on s.respondent_id = r.id
         JOIN survey.departments d on s.department_id = d.id
    );