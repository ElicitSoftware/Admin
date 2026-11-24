---
-- ***LICENSE_START***
-- Elicit Admin
-- %%
-- Copyright (C) 2025 - 2026 Elicit Software LLC
-- %%
-- PolyForm Noncommercial License 1.0.0
-- <https://polyformproject.org/licenses/noncommercial/1.0.0>
-- ***LICENSE_END***
---

--------------------------------
-- User Roles (DB fallback for OIDC role assignment)
--------------------------------
CREATE TABLE survey.user_roles
(
    user_id   bigint NOT NULL,
    role_name character varying(100) NOT NULL,
    CONSTRAINT user_roles_pk PRIMARY KEY (user_id, role_name),
    CONSTRAINT user_roles_users_fk FOREIGN KEY (user_id) REFERENCES survey.users (id)
);

GRANT DELETE, INSERT, SELECT, UPDATE ON survey.user_roles TO ${surveyadmin_user};
