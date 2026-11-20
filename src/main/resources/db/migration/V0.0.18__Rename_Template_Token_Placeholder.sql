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

-- Message templates substitute the respondent's access code for a placeholder. That
-- placeholder was <TOKEN>; it is now <ACCESS_CODE>, and <TOKEN> is no longer recognized.
-- Convert every stored template (including the V0.0.3 seed row) so existing invitations
-- keep working.
UPDATE survey.message_templates
SET message = replace(message, '<TOKEN>', '<ACCESS_CODE>')
WHERE message LIKE '%<TOKEN>%';

UPDATE survey.message_templates
SET subject = replace(subject, '<TOKEN>', '<ACCESS_CODE>')
WHERE subject LIKE '%<TOKEN>%';
