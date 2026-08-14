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
-- Restrict survey.user_roles.role_name to the three recognized Elicit roles
-- (elicit_admin, elicit_user, elicit_importer -- see ElicitRoles.ALL /
-- RoleSecurityIdentityAugmentor). Additive/backward-compatible: no existing
-- row can violate this, since these are the only role names the application
-- has ever written to this table.
--------------------------------
ALTER TABLE survey.user_roles
    ADD CONSTRAINT user_roles_role_name_ck
    CHECK (role_name IN ('elicit_admin', 'elicit_user', 'elicit_importer'));
