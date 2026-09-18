-- ***LICENSE_START***
-- Elicit Admin
-- %%
-- Copyright (C) 2025 The Regents of the University of Michigan - Rogel Cancer Center
-- %%
-- PolyForm Noncommercial License 1.0.0
-- <https://polyformproject.org/licenses/noncommercial/1.0.0>
-- ***LICENSE_END***

-- UC-016 BR-054 / UC-020: elicit_analytics joins the recognized role names. It is stored as
-- its own row alongside the user's single ladder grant (BR-055); the composite primary key
-- (user_id, role_name) already permits that.
ALTER TABLE survey.user_roles DROP CONSTRAINT IF EXISTS user_roles_role_name_ck;
ALTER TABLE survey.user_roles
    ADD CONSTRAINT user_roles_role_name_ck
    CHECK (role_name IN ('elicit_admin', 'elicit_user', 'elicit_importer', 'elicit_analytics'));
