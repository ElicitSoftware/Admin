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

-- Survey's Kimball Type 2 migration added a durable-key sequence per structural table.
-- SurveyDefinitionImportService allocates from these directly during survey definition
-- import, so surveyadmin_user needs the same access it already has on the surrogate *_seq
-- sequences (see V0.0.2__ADMIN_GRANTS.sql).

GRANT ALL ON SEQUENCE survey.questions_durable_seq          TO ${surveyadmin_user};
GRANT ALL ON SEQUENCE survey.select_groups_durable_seq      TO ${surveyadmin_user};
GRANT ALL ON SEQUENCE survey.select_items_durable_seq       TO ${surveyadmin_user};
GRANT ALL ON SEQUENCE survey.sections_durable_seq           TO ${surveyadmin_user};
GRANT ALL ON SEQUENCE survey.steps_durable_seq              TO ${surveyadmin_user};
GRANT ALL ON SEQUENCE survey.sections_questions_durable_seq TO ${surveyadmin_user};
GRANT ALL ON SEQUENCE survey.steps_sections_durable_seq     TO ${surveyadmin_user};
GRANT ALL ON SEQUENCE survey.relationships_durable_seq      TO ${surveyadmin_user};
