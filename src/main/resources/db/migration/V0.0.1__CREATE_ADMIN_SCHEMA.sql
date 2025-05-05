--------------------------------
-- Surveys
--------------------------------
CREATE SEQUENCE IF NOT EXISTS surveyadmin.surveys_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE IF NOT EXISTS surveyadmin.surveys
(
    id bigint NOT NULL,
    survey_id bigint NOT NULL,
    name character varying(255) NOT NULL,
    title character varying(100),
    description character varying(255),
    CONSTRAINT survey_pk PRIMARY KEY (id),
    CONSTRAINT survey_id_un UNIQUE (survey_id),
    CONSTRAINT survey_name_un UNIQUE (name)
);
--------------------------------
-- Reports
--------------------------------
CREATE SEQUENCE surveyadmin.reports_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE surveyadmin.reports(
    id bigint NOT NULL,
    survey_id bigint NOT NULL,
    name varchar(255) NOT NULL,
    description varchar(255) NOT NULL,
    URL varchar(200),
    display_order bigint NOT NULL,
    CONSTRAINT reports_pk PRIMARY KEY (id),
    CONSTRAINT reports_survey_fk FOREIGN KEY (survey_id) REFERENCES surveyadmin.surveys(survey_id),
    CONSTRAINT reports_un UNIQUE (survey_id, name)
);
--------------------------------
-- Message Types
--------------------------------
CREATE SEQUENCE surveyadmin.message_types_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE surveyadmin.message_types(
    id bigint NOT NULL,
    name character varying(25) NOT NULL,
    CONSTRAINT message_type_pk PRIMARY KEY (id),
    CONSTRAINT message_type_un UNIQUE (name)
);
--------------------------------
-- Departments
--------------------------------
CREATE SEQUENCE surveyadmin.departments_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE IF NOT EXISTS surveyadmin.departments
(
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    code character varying(100),
    default_message_type_id character varying(100) NOT NULL DEFAULT '1',
    notification_emails character varying(2000),
    from_email character varying(50) NOT NULL,
    CONSTRAINT department_pk PRIMARY KEY (id),
    CONSTRAINT department_code_un UNIQUE (code),
    CONSTRAINT department_name_un UNIQUE (name)
);
--------------------------------
-- Respondents
--------------------------------
CREATE SEQUENCE surveyadmin.respondents_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE surveyadmin.respondents(
  id bigint NOT NULL,
  xid character varying(50) NOT NULL,
  firstName character varying(50) NOT NULL,
  lastName character varying(50) NOT NULL,
  middleName character varying(50),
  dob date,
  email character varying(255),
  mobile character varying(20),
  department_id bigint NOT NULL,
  survey_id bigint NOT NULL,
  token character varying(255) NOT NULL,
  created_dt TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  CONSTRAINT respondents_pk PRIMARY KEY (id),
  CONSTRAINT respondents_xid_department_un UNIQUE (xid,department_id),
  CONSTRAINT respondents_surveys_fk FOREIGN KEY (survey_id) REFERENCES surveyadmin.surveys (id),
  CONSTRAINT respondents_departments_fk FOREIGN KEY (department_id) REFERENCES surveyadmin.departments (id)
);
--------------------------------
-- Users
--------------------------------
CREATE SEQUENCE surveyadmin.users_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE surveyadmin.users(
  id bigint NOT NULL,
  username character varying(100) NOT NULL,
  first_name character varying(255) NOT NULL,
  last_name character varying(255) NOT NULL,
  active boolean DEFAULT true NOT NULL,
  CONSTRAINT USERS_PK PRIMARY KEY (id),
  CONSTRAINT users_username_un UNIQUE (username)
 );
--------------------------------
-- Users Surveys
--------------------------------
CREATE TABLE surveyadmin.user_surveys(
  user_id bigint NOT NULL,
  survey_id bigint NOT NULL,
  CONSTRAINT user_surveys_pk PRIMARY KEY (user_id, survey_id),
  CONSTRAINT user_surveys_users_fk FOREIGN KEY (user_id) REFERENCES surveyadmin.users (id),
  CONSTRAINT user_surveys_surveys_fk FOREIGN KEY (survey_id) REFERENCES surveyadmin.surveys (id)
 );
--------------------------------
-- Roles
--------------------------------
CREATE SEQUENCE surveyadmin.roles_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE surveyadmin.roles(
  id bigint NOT NULL,
  name character varying(255) NOT NULL,
  CONSTRAINT roles_pk PRIMARY KEY (id),
  CONSTRAINT role_name_un UNIQUE (name)
);
--------------------------------
-- User Roles
--------------------------------
CREATE TABLE surveyadmin.user_roles(
  user_id bigint NOT NULL,
  role_id bigint NOT NULL,
  CONSTRAINT user_roles_pk PRIMARY KEY (user_id, role_id),
  CONSTRAINT user_roles_roles_fk FOREIGN KEY (role_id) REFERENCES surveyadmin.roles (id),
  CONSTRAINT user_roles_users_fk FOREIGN KEY (user_id) REFERENCES surveyadmin.users (id)
 );
--------------------------------
-- User Departments
--------------------------------
CREATE TABLE surveyadmin.user_departments(
  department_id bigint NOT NULL,
  user_id bigint NOT NULL,
  CONSTRAINT user_departments_pk PRIMARY KEY (department_id, user_id),
  CONSTRAINT user_departments_users_fk FOREIGN KEY (user_id) REFERENCES surveyadmin.users (id),
  CONSTRAINT user_departments_dep_fk FOREIGN KEY (department_id) REFERENCES surveyadmin.departments (id)
);
--------------------------------
-- Message Templates
--------------------------------
CREATE SEQUENCE surveyadmin.message_templates_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE surveyadmin.message_templates(
  id bigint NOT NULL,
  department_id bigint NOT NULL,
  message_type_id bigint NOT NULL,
  message varchar(6000) NOT NULL,
  subject character varying(255),
  cron_schedule character varying(255),
  mime_type character varying(100) DEFAULT 'text/plain',
  CONSTRAINT message_templates_type_pk PRIMARY KEY (id),
  CONSTRAINT message_templates_dep_fk FOREIGN KEY (department_id) REFERENCES surveyadmin.departments (id),
  CONSTRAINT message_templates_type_fk FOREIGN KEY (message_type_id) REFERENCES surveyadmin.message_types (id),
  CONSTRAINT message_templates_un UNIQUE (department_id,message_type_id)
);
--------------------------------
-- Messages
--------------------------------
CREATE SEQUENCE surveyadmin.messages_seq START WITH 1 INCREMENT BY 1;
  CREATE TABLE surveyadmin.messages(
  id bigint NOT NULL,
  respondent_id bigint NOT NULL,
  department_id bigint NOT NULL,
  sms_sid character varying(255),
  smtp_sid character varying(255),
  message_type_id bigint NOT NULL,
  message_body varchar(6000),
  created_dt TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  CONSTRAINT messages_pk PRIMARY KEY (id),
  CONSTRAINT message_sms_sid_un UNIQUE (sms_sid),
  CONSTRAINT message_smtp_sid_un UNIQUE (smtp_sid),
  CONSTRAINT messages_token_fk FOREIGN KEY (respondent_id) REFERENCES surveyadmin.respondents (id),
  CONSTRAINT messages_department_fk FOREIGN KEY (department_id) REFERENCES surveyadmin.departments (id),
  CONSTRAINT messages_message_type_fk FOREIGN KEY (message_type_id) REFERENCES surveyadmin.message_types (id)
);