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

--------------------------------
-- Seed database role grants for the dev accounts created in
-- V0.0.3__POPULATE_DEV_DATA.sql, so both have a working role under
-- elicit.authorization.mode=DATABASE. Looked up by username rather than a
-- hardcoded id so this stays correct regardless of sequence state. Has no
-- effect on OIDC-mode deployments -- the augmentor only consults these rows
-- when elicit.authorization.mode=DATABASE.
--------------------------------
INSERT INTO survey.user_roles(user_id, role_name)
SELECT id, 'elicit_admin' FROM survey.users WHERE username = 'admin';

INSERT INTO survey.user_roles(user_id, role_name)
SELECT id, 'elicit_user' FROM survey.users WHERE username = 'user';
