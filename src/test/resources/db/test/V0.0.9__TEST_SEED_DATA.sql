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
-- TEST SEED DATA for @QuarkusTest
-- =============================================================================
-- Runs after V0.0.8 so that tables created by V0.0.1 (departments,
-- message_types) are already present. Inserts minimal reference rows
-- that round-trip tests rely on (department id=1, message_type id=1).
-- ON CONFLICT DO NOTHING guards against duplicate inserts if test data
-- is already present (e.g. from V0.0.3__POPULATE_DEV_DATA.sql).
-- =============================================================================

INSERT INTO survey.departments (id, name, code, default_message_id, from_email)
VALUES (1, 'Test Dept', 'TEST', '1', 'test@localhost')
ON CONFLICT DO NOTHING;

INSERT INTO survey.message_types (id, name)
VALUES (1, 'EMAIL')
ON CONFLICT DO NOTHING;
