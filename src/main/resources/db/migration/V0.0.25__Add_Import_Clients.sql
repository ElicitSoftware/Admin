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
-- Import clients (UC-032): the machine clients of the registration API and the departments
-- each may register into. A client is matched to a bearer token by its principal name.
--------------------------------
CREATE SEQUENCE survey.import_clients_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE survey.import_clients
(
    id          bigint                 NOT NULL,
    name        character varying(100) NOT NULL,
    description character varying(255),
    active      boolean DEFAULT true   NOT NULL,
    CONSTRAINT import_clients_pk PRIMARY KEY (id),
    CONSTRAINT import_clients_name_un UNIQUE (name)
);

CREATE TABLE survey.import_client_departments
(
    import_client_id bigint NOT NULL,
    department_id    bigint NOT NULL,
    CONSTRAINT import_client_departments_pk PRIMARY KEY (import_client_id, department_id),
    CONSTRAINT import_client_departments_client_fk FOREIGN KEY (import_client_id) REFERENCES survey.import_clients (id),
    CONSTRAINT import_client_departments_dep_fk FOREIGN KEY (department_id) REFERENCES survey.departments (id)
);

GRANT ALL ON SEQUENCE survey.import_clients_seq TO ${surveyadmin_user};
GRANT DELETE, INSERT, SELECT, UPDATE ON survey.import_clients TO ${surveyadmin_user};
GRANT DELETE, INSERT, SELECT, UPDATE ON survey.import_client_departments TO ${surveyadmin_user};

--------------------------------
-- elicit_importer belongs to import clients only (UC-016 BR-054): no user holds it, so the
-- grants that exist are removed and the role table stops accepting it.
--------------------------------
DELETE FROM survey.user_roles WHERE role_name = 'elicit_importer';

ALTER TABLE survey.user_roles DROP CONSTRAINT user_roles_role_name_ck;
ALTER TABLE survey.user_roles
    ADD CONSTRAINT user_roles_role_name_ck
        CHECK (role_name IN ('elicit_admin', 'elicit_user'));
