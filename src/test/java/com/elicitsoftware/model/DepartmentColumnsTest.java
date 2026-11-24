package com.elicitsoftware.model;

/*-
 * ***LICENSE_START***
 * Elicit Admin
 * %%
 * Copyright (C) 2025 - 2026 Elicit Software LLC
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import com.elicitsoftware.test.PostgresTestResource;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * UC-006: the department table after the migrations have run. A department has no notification addresses
 * (docs/entity_model.md, DEPARTMENT; issue #221).
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class DepartmentColumnsTest {

    @Inject
    EntityManager em;

    /** V0.0.28 drops departments.notification_emails, which nothing read. */
    @Test
    @TestTransaction
    void notificationEmailsColumnIsDropped() {
        Number count = (Number) em.createNativeQuery("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema = 'survey' AND table_name = 'departments'
                  AND column_name = 'notification_emails'""").getSingleResult();

        assertEquals(0, count.intValue());
    }
}
