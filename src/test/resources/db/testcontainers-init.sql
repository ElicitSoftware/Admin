---
-- ***LICENSE_START***
-- Elicit Survey
-- %%
-- Copyright (C) 2025 - 2026 The Regents of the University of Michigan - Rogel Cancer Center
-- %%
-- PolyForm Noncommercial License 1.0.0
-- <https://polyformproject.org/licenses/noncommercial/1.0.0>
-- ***LICENSE_END***
---

-- Runs once, as the container's superuser, right after the Testcontainers PostgreSQL reports
-- healthy and before Flyway or the application connects. It prepares the cluster the way a site
-- does before any module starts (installation manual, "The database"): the roles and the two
-- empty schemas. No migration creates either, and none of the roles is a superuser (umbrella
-- C-020), so from here on nothing in a test run holds more than the application does on a site:
-- Flyway migrates as elicit_owner and the application connects as surveyadmin_user
-- (PostgresTestResource).
CREATE ROLE dbowner WITH NOLOGIN NOSUPERUSER INHERIT NOCREATEDB NOCREATEROLE NOREPLICATION;
CREATE ROLE dbuser WITH NOLOGIN NOSUPERUSER INHERIT NOCREATEDB NOCREATEROLE NOREPLICATION;

CREATE ROLE elicit_owner WITH LOGIN NOSUPERUSER INHERIT NOCREATEDB NOCREATEROLE NOREPLICATION
    PASSWORD 'SURVEYPW';
GRANT dbowner TO elicit_owner;

CREATE ROLE survey_user WITH LOGIN NOSUPERUSER INHERIT NOCREATEDB NOCREATEROLE NOREPLICATION
    PASSWORD 'SURVEYPW';
GRANT dbuser TO survey_user;

CREATE ROLE surveyadmin_user WITH LOGIN NOSUPERUSER INHERIT NOCREATEDB NOCREATEROLE NOREPLICATION
    PASSWORD 'SAOWNERPW';
GRANT dbuser TO surveyadmin_user;

CREATE ROLE surveyreport_user WITH LOGIN NOSUPERUSER INHERIT NOCREATEDB NOCREATEROLE NOREPLICATION
    PASSWORD 'SRUSERPW';
GRANT dbuser TO surveyreport_user;

GRANT CONNECT, CREATE ON DATABASE survey TO dbowner;
GRANT CONNECT ON DATABASE survey TO dbuser;

GRANT USAGE, CREATE ON SCHEMA public TO dbowner;
GRANT USAGE ON SCHEMA public TO dbuser;

CREATE SCHEMA survey AUTHORIZATION elicit_owner;
GRANT USAGE ON SCHEMA survey TO survey_user;
GRANT USAGE ON SCHEMA survey TO surveyadmin_user;
GRANT USAGE ON SCHEMA survey TO surveyreport_user;

CREATE SCHEMA surveyreport AUTHORIZATION elicit_owner;
GRANT USAGE ON SCHEMA surveyreport TO survey_user;
GRANT USAGE ON SCHEMA surveyreport TO surveyadmin_user;
GRANT USAGE ON SCHEMA surveyreport TO surveyreport_user;
