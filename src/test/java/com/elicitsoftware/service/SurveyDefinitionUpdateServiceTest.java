package com.elicitsoftware.service;

/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2025 - 2026 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import com.elicitsoftware.model.PostSurveyAction;
import com.elicitsoftware.model.ReportDefinition;
import com.elicitsoftware.model.Survey;
import com.elicitsoftware.test.PostgresTestResource;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Booted tests for {@link SurveyDefinitionUpdateService}.
 *
 * <p>Traceability: UC-017 (Update Survey Definition). Covers the main success scenario
 * (unchanged/versioned/created reconciliation across the Type 2 and Type 1 survey-definition
 * tables) and every alternative flow (A1 is REST-layer and covered by
 * {@code SurveyDefinitionUpdateResource}, A2-A5 here).</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class SurveyDefinitionUpdateServiceTest {

    @Inject
    SurveyDefinitionExportService surveyDefinitionExportService;

    @Inject
    SurveyDefinitionUpdateService surveyDefinitionUpdateService;

    @Inject
    EntityManager em;

    private InputStream toStream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    private long nextVal(String sequence) {
        return ((Number) em.createNativeQuery("SELECT nextval('" + sequence + "')").getSingleResult()).longValue();
    }

    @SuppressWarnings("unchecked")
    private <T> T queryOne(String sql, Object... params) {
        Query query = em.createNativeQuery(sql);
        for (int i = 0; i < params.length; i++) {
            query.setParameter(i + 1, params[i]);
        }
        return (T) query.getSingleResult();
    }

    private long queryLong(String sql, Object... params) {
        Number n = queryOne(sql, params);
        return n.longValue();
    }

    /**
     * Fixture writes run in their own committed transaction ({@code requiringNew}), suspending
     * and later resuming the test method's {@code @TestTransaction} — unlike everything the test
     * method itself does directly, which rolls back at the end. This matters here specifically
     * because {@link SurveyLogService#log} runs in a {@code REQUIRES_NEW} transaction of its own:
     * it can only see rows that have actually been committed to the database, not rows still
     * sitting in the ambient (never-to-commit) {@code @TestTransaction}. In production the
     * target survey a real Update call references was always committed by some earlier request,
     * so this is purely a test-fixture concern, not a production one.
     */
    private Survey newSurvey(String name) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Survey survey = new Survey();
            survey.name = name;
            survey.displayOrder = 1000 + (int) (Math.random() * 100000);
            survey.title = "Title " + name;
            survey.persist();
            return survey;
        });
    }

    private long insertStep(int surveyId, int displayOrder, String name) {
        return QuarkusTransaction.requiringNew().call(() -> {
            long id = nextVal("survey.steps_seq");
            em.createNativeQuery("INSERT INTO survey.steps (id, survey_id, display_order, name, dimension_name) "
                            + "VALUES (?1, ?2, ?3, ?4, 'D')")
                    .setParameter(1, id).setParameter(2, surveyId).setParameter(3, displayOrder).setParameter(4, name)
                    .executeUpdate();
            return id;
        });
    }

    private long insertReport(Survey survey, String name) {
        return QuarkusTransaction.requiringNew().call(() -> {
            ReportDefinition report = new ReportDefinition();
            report.survey = Survey.findById(survey.id);
            report.name = name;
            report.displayOrder = 1;
            report.persist();
            return report.id.longValue();
        });
    }

    /**
     * Seeds one row of every survey-definition table type (all 8 Type 2 tables plus the 5
     * remaining Type 1 tables) on a brand-new survey, committed for real so it's visible to
     * {@link SurveyLogService}'s {@code REQUIRES_NEW} logging just like {@link #newSurvey}.
     */
    private Survey persistFullDefinitionTree(String token) {
        return persistFullDefinitionTree(token, BigDecimal.ONE);
    }

    /**
     * As {@link #persistFullDefinitionTree(String)}, with every display order of the four Type 2
     * tables whose order columns are {@code NUMERIC} (steps, sections, steps_sections,
     * sections_questions) set to {@code displayOrder}.
     */
    private Survey persistFullDefinitionTree(String token, BigDecimal displayOrder) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Survey survey = new Survey();
            survey.name = "Full " + token;
            survey.displayOrder = 1000 + (int) (Math.random() * 100000);
            survey.title = "Full Title " + token;
            survey.persist();
            int surveyId = survey.id;

            long groupId = nextVal("survey.select_groups_seq");
            em.createNativeQuery("INSERT INTO survey.select_groups (id, survey_id, name, description, data_type) "
                            + "VALUES (?1, ?2, ?3, 'seeded', 'Text')")
                    .setParameter(1, groupId).setParameter(2, surveyId).setParameter(3, "Group " + token)
                    .executeUpdate();
            long groupDurableId = queryLong("SELECT select_group_id FROM survey.select_groups WHERE id = ?1", groupId);

            long itemId = nextVal("survey.select_items_seq");
            em.createNativeQuery("INSERT INTO survey.select_items (id, survey_id, select_group_id, display_text, display_order, coded_value) "
                            + "VALUES (?1, ?2, ?3, ?4, 1, 'CODE')")
                    .setParameter(1, itemId).setParameter(2, surveyId).setParameter(3, groupDurableId).setParameter(4, "Item " + token)
                    .executeUpdate();

            long stepId = nextVal("survey.steps_seq");
            em.createNativeQuery("INSERT INTO survey.steps (id, survey_id, display_order, name, dimension_name) "
                            + "VALUES (?1, ?2, ?4, ?3, 'D')")
                    .setParameter(1, stepId).setParameter(2, surveyId).setParameter(3, "Step " + token).setParameter(4, displayOrder)
                    .executeUpdate();
            long stepDurableId = queryLong("SELECT step_id FROM survey.steps WHERE id = ?1", stepId);

            long sectionId = nextVal("survey.sections_seq");
            em.createNativeQuery("INSERT INTO survey.sections (id, survey_id, display_order, name, dimension_name) "
                            + "VALUES (?1, ?2, ?4, ?3, 'D')")
                    .setParameter(1, sectionId).setParameter(2, surveyId).setParameter(3, "Section " + token).setParameter(4, displayOrder)
                    .executeUpdate();
            long sectionDurableId = queryLong("SELECT section_id FROM survey.sections WHERE id = ?1", sectionId);

            long stepsSectionId = nextVal("survey.steps_sections_seq");
            em.createNativeQuery("INSERT INTO survey.steps_sections "
                            + "(id, survey_id, step_id, step_display_order, section_id, section_display_order, display_key) "
                            + "VALUES (?1, ?2, ?3, ?6, ?4, ?6, ?5)")
                    .setParameter(1, stepsSectionId).setParameter(2, surveyId).setParameter(3, stepDurableId)
                    // A realistically shaped display key already carrying THIS survey's id. The
                    // importer/updater rebases the leading component onto the target survey, so a
                    // self-update must find it identical and report unchanged rather than
                    // re-versioning the row on every apply.
                    .setParameter(4, sectionDurableId)
                    .setParameter(5, String.format("%04d", surveyId) + "-0001-0000-0001-0000-0000-0000")
                    .setParameter(6, displayOrder)
                    .executeUpdate();
            long stepsSectionDurableId = queryLong("SELECT steps_sections_id FROM survey.steps_sections WHERE id = ?1", stepsSectionId);

            long questionId = nextVal("survey.questions_seq");
            em.createNativeQuery("INSERT INTO survey.questions (id, survey_id, type_id, text, select_group_id) "
                            + "VALUES (?1, ?2, 1, ?3, ?4)")
                    .setParameter(1, questionId).setParameter(2, surveyId).setParameter(3, "Question " + token + "?")
                    .setParameter(4, groupDurableId)
                    .executeUpdate();
            long questionDurableId = queryLong("SELECT question_id FROM survey.questions WHERE id = ?1", questionId);

            long sqId = nextVal("survey.sections_questions_seq");
            em.createNativeQuery("INSERT INTO survey.sections_questions (id, survey_id, question_id, section_id, display_order) "
                            + "VALUES (?1, ?2, ?3, ?4, ?5)")
                    .setParameter(1, sqId).setParameter(2, surveyId).setParameter(3, questionDurableId).setParameter(4, sectionDurableId).setParameter(5, displayOrder)
                    .executeUpdate();
            long sqDurableId = queryLong("SELECT sections_question_id FROM survey.sections_questions WHERE id = ?1", sqId);

            // Minimal relationship: only the required upstream_sq_id/operator/action — downstream_*
            // left null, which satisfies relationships' downstream_ck CHECK (a NULL sum is not FALSE).
            long relId = nextVal("survey.relationships_seq");
            em.createNativeQuery("INSERT INTO survey.relationships (id, survey_id, upstream_sq_id, operator_id, action_id) "
                            + "VALUES (?1, ?2, ?3, 1, 1)")
                    .setParameter(1, relId).setParameter(2, surveyId).setParameter(3, sqDurableId)
                    .executeUpdate();

            ReportDefinition report = new ReportDefinition();
            report.survey = survey;
            report.name = "Report " + token;
            report.displayOrder = 1;
            report.persist();

            PostSurveyAction psa = new PostSurveyAction();
            psa.survey = survey;
            psa.name = "PSA " + token;
            psa.persistAndFlush();

            long dimId = nextVal("survey.dimensions_seq");
            em.createNativeQuery("INSERT INTO survey.dimensions (id, name) VALUES (?1, ?2)")
                    .setParameter(1, dimId).setParameter(2, "Dim-" + token)
                    .executeUpdate();

            long ontId = nextVal("survey.ontology_seq");
            em.createNativeQuery("INSERT INTO survey.ontology (id, survey_id, name, tag, dimension) "
                            + "VALUES (?1, ?2, ?3, 'Tag-" + token + "', ?4)")
                    .setParameter(1, ontId).setParameter(2, surveyId).setParameter(3, "Ont-" + token).setParameter(4, dimId)
                    .executeUpdate();

            long metaId = nextVal("survey.metadata_seq");
            em.createNativeQuery("INSERT INTO survey.metadata (id, survey_id, steps_sections_id, ontology_id, value) "
                            + "VALUES (?1, ?2, ?3, ?4, ?5)")
                    .setParameter(1, metaId).setParameter(2, surveyId).setParameter(3, stepsSectionDurableId)
                    .setParameter(4, ontId).setParameter(5, "Meta-" + token)
                    .executeUpdate();

            return survey;
        });
    }

    /**
     * UC-017 main success scenario, full breadth: applying a survey's own just-exported file
     * back onto itself reconciles every element_key in every one of the 13 non-survey
     * survey-definition tables (8 Type 2, 5 Type 1) to the row it came from — nothing is created
     * or versioned anywhere, and the survey shell itself comes back unchanged too.
     */
    @Test
    @TestTransaction
    void selfUpdateOfFullDefinitionTreeLeavesEverythingUnchanged() {
        Survey survey = persistFullDefinitionTree("FULL1");

        String exported = surveyDefinitionExportService.exportSurvey(survey.id);

        SurveyDefinitionUpdateService.UpdateResult result =
                surveyDefinitionUpdateService.updateFromFile(toStream(exported), "full.elicit", survey.id);

        assertTrue(result.isSuccess(), () -> "update errors: " + result.getErrors());
        SurveyDefinitionUpdateService.TableUpdateCounts unchangedOne =
                new SurveyDefinitionUpdateService.TableUpdateCounts(0, 0, 1, 0);
        for (String table : new String[] {
                "surveys", "select_groups", "select_items", "steps", "sections", "steps_sections",
                "questions", "sections_questions", "relationships", "reports", "post_survey_actions",
                "dimensions", "ontology", "metadata"}) {
            assertEquals(unchangedOne, result.getCounts().get(table), table + " should be entirely unchanged");
        }
    }

    private static final SurveyDefinitionUpdateService.TableUpdateCounts UNCHANGED_ONE =
            new SurveyDefinitionUpdateService.TableUpdateCounts(0, 0, 1, 0);

    /** The four Type 2 tables whose display orders are {@code NUMERIC} in the survey schema. */
    private static final String[] DISPLAY_ORDERED_TABLES = {"steps", "sections", "steps_sections", "sections_questions"};

    private SurveyDefinitionUpdateService.UpdateResult applyTo(Survey survey, String content, String fileName) {
        SurveyDefinitionUpdateService.UpdateResult result =
                surveyDefinitionUpdateService.updateFromFile(toStream(content), fileName, survey.id);
        assertTrue(result.isSuccess(), () -> fileName + " update errors: " + result.getErrors());
        return result;
    }

    private void assertAllUnchanged(SurveyDefinitionUpdateService.UpdateResult result, String... tables) {
        for (String table : tables) {
            assertEquals(UNCHANGED_ONE, result.getCounts().get(table), table + " should be entirely unchanged");
        }
    }

    /** Every row of {@code table} for the survey — one per durable id as seeded, more once versioned. */
    private long rowsFor(String table, Survey survey) {
        return queryLong("SELECT count(*) FROM survey." + table + " WHERE survey_id = ?1", survey.id);
    }

    /**
     * UC-017 BR-109 regression: {@code steps.display_order}, {@code sections.display_order},
     * {@code steps_sections.step_display_order}/{@code section_display_order} and
     * {@code sections_questions.display_order} are {@code NUMERIC} in the survey schema (decimal
     * midpoint insertion), so the stored value reads back as a {@code BigDecimal}. Comparing it
     * with the file's value as an {@code Integer} never matched, and one apply re-versioned every
     * step, section, step-section and question assignment even though nothing had changed.
     * Re-applying an identical file must version nothing, and a file that changes a single
     * question's text must version that question alone.
     */
    @Test
    @TestTransaction
    void reapplyingIdenticalFileVersionsNothingAndAChangedQuestionVersionsOnlyItself() {
        Survey survey = persistFullDefinitionTree("NUM1");
        String exported = surveyDefinitionExportService.exportSurvey(survey.id);

        SurveyDefinitionUpdateService.UpdateResult first = applyTo(survey, exported, "first.elicit");
        SurveyDefinitionUpdateService.UpdateResult second = applyTo(survey, exported, "second.elicit");
        for (SurveyDefinitionUpdateService.UpdateResult result : new SurveyDefinitionUpdateService.UpdateResult[] {first, second}) {
            assertAllUnchanged(result, DISPLAY_ORDERED_TABLES);
            assertAllUnchanged(result, "questions", "relationships", "select_groups", "select_items");
            for (String table : DISPLAY_ORDERED_TABLES) {
                assertEquals(0, result.getCounts().get(table).versioned(), table + " must not be versioned by an identical file");
            }
        }
        for (String table : DISPLAY_ORDERED_TABLES) {
            assertEquals(1L, rowsFor(table, survey), table + " must still hold one row per durable id after two identical applies");
        }

        String changed = exported.replace("Question NUM1?", "Question NUM1 (revised)?");
        assertNotEquals(exported, changed, "the fixture's question text must be present in the export");
        SurveyDefinitionUpdateService.UpdateResult third = applyTo(survey, changed, "third.elicit");

        assertEquals(new SurveyDefinitionUpdateService.TableUpdateCounts(0, 1, 0, 0), third.getCounts().get("questions"),
                "only the question whose text changed is versioned");
        assertAllUnchanged(third, DISPLAY_ORDERED_TABLES);
        assertAllUnchanged(third, "relationships", "select_groups", "select_items");
        assertEquals(2L, rowsFor("questions", survey), "versioning closes the old question row and inserts a new one");
        for (String table : DISPLAY_ORDERED_TABLES) {
            assertEquals(1L, rowsFor(table, survey), table + " must not gain a version because a question's text changed");
        }
        assertEquals(1L, queryLong(
                "SELECT count(*) FROM survey.questions WHERE survey_id = ?1 AND text = 'Question NUM1 (revised)?' "
                        + "AND version = 1 AND effective_to = '9999-12-31 23:59:59+00'", survey.id));
    }

    /**
     * UC-017 BR-109: a decimal display order ({@code 1.5}, the midpoint-insertion case the
     * {@code NUMERIC} columns exist for) is exported verbatim, parsed as a decimal rather than
     * dropped to {@code null}, and compares equal to the stored value on re-apply.
     */
    @Test
    @TestTransaction
    void decimalDisplayOrderRoundTripsUnchanged() {
        Survey survey = persistFullDefinitionTree("NUM2", new BigDecimal("1.5"));
        String exported = surveyDefinitionExportService.exportSurvey(survey.id);
        assertTrue(exported.contains("|1.5|"), () -> "the export must carry the decimal display order verbatim:\n" + exported);

        SurveyDefinitionUpdateService.UpdateResult result = applyTo(survey, exported, "decimal.elicit");

        assertAllUnchanged(result, DISPLAY_ORDERED_TABLES);
        for (String table : DISPLAY_ORDERED_TABLES) {
            assertEquals(1L, rowsFor(table, survey), table + " must not be versioned by its own decimal display order");
        }
        BigDecimal stored = queryOne("SELECT display_order FROM survey.steps WHERE survey_id = ?1", survey.id);
        assertEquals(0, new BigDecimal("1.5").compareTo(stored), "the stored decimal display order is untouched: " + stored);
    }

    /**
     * UC-017 main success scenario: applying a survey's own just-exported file back onto itself
     * (self-update) reconciles every element_key to the same row it came from, so nothing is
     * created or versioned across any table — everything comes back unchanged.
     */
    @Test
    @TestTransaction
    void selfUpdateWithUnmodifiedFileLeavesEverythingUnchanged() {
        Survey survey = newSurvey("UpdSelf");
        long stepId = insertStep(survey.id, 1, "Step A");
        long reportId = insertReport(survey, "Report A");

        String exported = surveyDefinitionExportService.exportSurvey(survey.id);

        SurveyDefinitionUpdateService.UpdateResult result =
                surveyDefinitionUpdateService.updateFromFile(toStream(exported), "self.elicit", survey.id);

        assertTrue(result.isSuccess(), () -> "update errors: " + result.getErrors());
        assertEquals(new SurveyDefinitionUpdateService.TableUpdateCounts(0, 0, 1, 0), result.getCounts().get("surveys"));
        assertEquals(new SurveyDefinitionUpdateService.TableUpdateCounts(0, 0, 1, 0), result.getCounts().get("steps"));
        assertEquals(new SurveyDefinitionUpdateService.TableUpdateCounts(0, 0, 1, 0), result.getCounts().get("reports"));

        assertEquals("Step A", queryOne("SELECT name FROM survey.steps WHERE id = ?1", stepId));
        assertEquals("Report A", queryOne("SELECT name FROM survey.reports WHERE id = ?1", reportId));
        assertEquals(1L, queryLong("SELECT count(*) FROM survey.steps WHERE survey_id = ?1", survey.id),
                "no extra step row should have been created by a no-op update");
    }

    /**
     * UC-017 step 4/BR-064: a changed structural child is versioned (old row closed, new row
     * inserted under the same durable id with version+1), a brand-new element_key is created,
     * and a changed Type 1 table (reports) is updated in place with no version history.
     */
    @Test
    @TestTransaction
    void changedStepIsVersionedNewStepIsCreatedAndReportIsUpdatedInPlace() {
        Survey survey = newSurvey("UpdChg");
        long stepSurrogateId = insertStep(survey.id, 1, "Original Step");
        long reportId = insertReport(survey, "Original Report");

        UUID stepKey = queryOne("SELECT step_key FROM survey.steps WHERE id = ?1", stepSurrogateId);
        long stepDurableId = queryLong("SELECT step_id FROM survey.steps WHERE id = ?1", stepSurrogateId);
        int stepVersion = queryOne("SELECT version FROM survey.steps WHERE id = ?1", stepSurrogateId);
        UUID reportKey = queryOne("SELECT report_key FROM survey.reports WHERE id = ?1", reportId);

        UUID newStepKey = UUID.randomUUID();
        String content = "# ELICIT_SURVEY_EXPORT_V1\n\n"
                + "surveys: " + survey.id + "|" + survey.surveyKey + "|" + survey.name + "|1|New Title|||||\n"
                // existing step, changed name -> VERSIONED
                + "steps: 100|" + stepKey + "|1|Renamed Step|D|||||||\n"
                // brand-new step, fresh element_key, no prior match -> CREATED
                + "steps: 101|" + newStepKey + "|2|Brand New Step|D|||||||\n"
                // existing report, changed name -> VERSIONED (Type 1, in place)
                + "reports: 1|" + reportKey + "|Renamed Report|||1\n";

        SurveyDefinitionUpdateService.UpdateResult result =
                surveyDefinitionUpdateService.updateFromFile(toStream(content), "changed.elicit", survey.id);

        assertTrue(result.isSuccess(), () -> "update errors: " + result.getErrors());
        assertEquals(new SurveyDefinitionUpdateService.TableUpdateCounts(0, 1, 0, 0), result.getCounts().get("surveys"));
        assertEquals(new SurveyDefinitionUpdateService.TableUpdateCounts(1, 1, 0, 0), result.getCounts().get("steps"));
        assertEquals(new SurveyDefinitionUpdateService.TableUpdateCounts(0, 1, 0, 0), result.getCounts().get("reports"));

        assertEquals("New Title", queryOne("SELECT title FROM survey.surveys WHERE id = ?1", survey.id));

        // Old step row closed, no longer current.
        String oldEffectiveTo = queryOne("SELECT effective_to::text FROM survey.steps WHERE id = ?1", stepSurrogateId);
        assertFalse(oldEffectiveTo.startsWith("9999-12-31"), "closed row's effective_to must no longer be open-ended: " + oldEffectiveTo);
        assertEquals(2L, queryLong("SELECT count(*) FROM survey.steps WHERE step_id = ?1", stepDurableId),
                "versioning a step must close the old row and insert a new one under the same durable id");
        assertEquals(1L, queryLong(
                "SELECT count(*) FROM survey.steps WHERE step_id = ?1 AND name = 'Renamed Step' "
                        + "AND version = ?2 AND effective_to = '9999-12-31 23:59:59+00'",
                stepDurableId, stepVersion + 1),
                "the new current row must carry version+1 and the file's new name");
        assertEquals(1L, queryLong(
                "SELECT count(*) FROM survey.steps WHERE survey_id = ?1 AND name = 'Brand New Step' "
                        + "AND effective_to = '9999-12-31 23:59:59+00'",
                survey.id));

        assertEquals("Renamed Report", queryOne("SELECT name FROM survey.reports WHERE id = ?1", reportId));
        assertEquals(1L, queryLong("SELECT count(*) FROM survey.reports WHERE survey_id = ?1", survey.id),
                "a Type 1 table update must not create a second row for the same element_key");
    }

    /**
     * BR-063/A3: a file whose survey_key does not match the selected target survey's key aborts
     * the update entirely, with no changes applied.
     */
    @Test
    @TestTransaction
    void mismatchedSurveyKeyAbortsWithNoChanges() {
        Survey target = newSurvey("UpdMismatchTarget");
        String otherKey = UUID.randomUUID().toString();
        String content = "# ELICIT_SURVEY_EXPORT_V1\n\n"
                + "surveys: 1|" + otherKey + "|SomeOtherSurvey|1|Title|||||\n";

        SurveyDefinitionUpdateService.UpdateResult result =
                surveyDefinitionUpdateService.updateFromFile(toStream(content), "mismatch.elicit", target.id);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrors().get(0).contains("does not match"), result.getErrors().toString());
        assertEquals("Title " + target.name, queryOne("SELECT title FROM survey.surveys WHERE id = ?1", target.id),
                "target survey must be untouched when the key doesn't match");
    }

    /**
     * A4: a file predating stable-key assignment (empty survey_key) cannot be matched to any
     * target and is rejected outright.
     */
    @Test
    @TestTransaction
    void missingSurveyKeyInFileIsRejected() {
        Survey target = newSurvey("UpdNoKeyTarget");
        String content = "# ELICIT_SURVEY_EXPORT_V1\n\n"
                + "surveys: 1||NoKeySurvey|1|Title|||||\n";

        SurveyDefinitionUpdateService.UpdateResult result =
                surveyDefinitionUpdateService.updateFromFile(toStream(content), "nokey.elicit", target.id);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrors().get(0).contains("predates stable-key assignment"), result.getErrors().toString());
    }

    /** A2: a file missing the format-version header returns a failed result, not an exception. */
    @Test
    @TestTransaction
    void malformedHeaderReturnsFailedResultWithoutThrowing() {
        Survey target = newSurvey("UpdBadHeaderTarget");
        String content = "surveys: 1|" + target.surveyKey + "|Name|1|Title|||||\n";

        SurveyDefinitionUpdateService.UpdateResult result =
                surveyDefinitionUpdateService.updateFromFile(toStream(content), "badheader.elicit", target.id);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrors().get(0).contains("valid format header"), result.getErrors().toString());
    }

    /** No target survey with the given id exists. */
    @Test
    @TestTransaction
    void unknownTargetSurveyIsRejected() {
        String content = "# ELICIT_SURVEY_EXPORT_V1\n\nsurveys: 1|" + UUID.randomUUID() + "|Name|1|Title|||||\n";

        SurveyDefinitionUpdateService.UpdateResult result =
                surveyDefinitionUpdateService.updateFromFile(toStream(content), "notfound.elicit", 987654321);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrors().get(0).contains("Target survey not found"), result.getErrors().toString());
    }

    /**
     * A5: a select_items line referencing a select_group source_id never defined earlier in the
     * same file throws, aborting and rolling back the whole update.
     */
    @Test
    @TestTransaction
    void danglingForeignKeyReferenceThrowsRuntimeException() {
        Survey target = newSurvey("UpdDanglingTarget");
        String content = "# ELICIT_SURVEY_EXPORT_V1\n\n"
                + "surveys: 1|" + target.surveyKey + "|" + target.name + "|1|Title|||||\n"
                + "select_items: 1|" + UUID.randomUUID() + "|999|Text|1|CODE||||||\n";

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> surveyDefinitionUpdateService.updateFromFile(toStream(content), "dangling.elicit", target.id));

        assertTrue(ex.getMessage().contains("No ID mapping found"), ex.getMessage());
    }

    /**
     * UC-017/A5-adjacent: a structural row with no element_key at all cannot be matched and is
     * rejected as a malformed record, distinct from the missing-survey_key case (A4).
     */
    @Test
    @TestTransaction
    void structuralRowWithoutElementKeyThrows() {
        Survey target = newSurvey("UpdNoElementKeyTarget");
        String content = "# ELICIT_SURVEY_EXPORT_V1\n\n"
                + "surveys: 1|" + target.surveyKey + "|" + target.name + "|1|Title|||||\n"
                + "steps: 1||1|Step With No Key|D|||||||\n";

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> surveyDefinitionUpdateService.updateFromFile(toStream(content), "nokey2.elicit", target.id));

        assertTrue(ex.getMessage().contains("no element_key to match against"), ex.getMessage());
    }

    /**
     * UC-017: dimensions/ontology are Type 1 lookups matched by element_key (ontology) or by
     * name (dimensions, a shared global lookup) — a changed ontology tag updates in place, and a
     * dimension already present by name is reused rather than duplicated even though the file
     * carries a different dimension_key.
     */
    @Test
    @TestTransaction
    void ontologyIsUpdatedInPlaceAndDimensionIsReusedByName() {
        Survey survey = newSurvey("UpdOntology");

        long ontId = QuarkusTransaction.requiringNew().call(() -> {
            long dimId = nextVal("survey.dimensions_seq");
            em.createNativeQuery("INSERT INTO survey.dimensions (id, name) VALUES (?1, 'SharedDim')")
                    .setParameter(1, dimId).executeUpdate();

            long newOntId = nextVal("survey.ontology_seq");
            em.createNativeQuery("INSERT INTO survey.ontology (id, survey_id, name, tag, dimension) "
                            + "VALUES (?1, ?2, 'OntName', 'OldTag', ?3)")
                    .setParameter(1, newOntId).setParameter(2, survey.id).setParameter(3, dimId)
                    .executeUpdate();
            return newOntId;
        });
        UUID ontologyKey = queryOne("SELECT ontology_key FROM survey.ontology WHERE id = ?1", ontId);

        String content = "# ELICIT_SURVEY_EXPORT_V1\n\n"
                + "surveys: 1|" + survey.surveyKey + "|" + survey.name + "|1|Title|||||\n"
                + "dimensions: 5|" + UUID.randomUUID() + "|SharedDim\n"
                + "ontology: 6|" + ontologyKey + "|OntName|NewTag|5\n";

        SurveyDefinitionUpdateService.UpdateResult result =
                surveyDefinitionUpdateService.updateFromFile(toStream(content), "ontology.elicit", survey.id);

        assertTrue(result.isSuccess(), () -> "update errors: " + result.getErrors());
        assertEquals(new SurveyDefinitionUpdateService.TableUpdateCounts(0, 0, 1, 0), result.getCounts().get("dimensions"));
        assertEquals(new SurveyDefinitionUpdateService.TableUpdateCounts(0, 1, 0, 0), result.getCounts().get("ontology"));

        assertEquals(1L, queryLong("SELECT count(*) FROM survey.dimensions WHERE name = 'SharedDim'"),
                "the existing dimension must be reused by name, not duplicated");
        assertEquals("NewTag", queryOne("SELECT tag FROM survey.ontology WHERE id = ?1", ontId));
    }

    // -------------------------------------------------------------------------
    // Revision identifier and regression detection
    // -------------------------------------------------------------------------

    /** Rewrites a file's "# survey_revision:" header to a specific instant. */
    private String withRevision(String content, OffsetDateTime revision) {
        StringBuilder out = new StringBuilder();
        boolean replaced = false;
        for (String line : content.split("\n", -1)) {
            if (line.trim().startsWith(SurveyDefinitionFileFields.REVISION_HEADER)) {
                out.append(SurveyDefinitionFileFields.REVISION_HEADER).append(' ').append(revision);
                replaced = true;
            } else {
                out.append(line);
            }
            out.append('\n');
        }
        if (!replaced) {
            throw new IllegalStateException("export carried no revision header to rewrite");
        }
        return out.toString();
    }

    /** Strips the revision header entirely, simulating a file written before it existed. */
    private String withoutRevision(String content) {
        StringBuilder out = new StringBuilder();
        for (String line : content.split("\n", -1)) {
            if (!line.trim().startsWith(SurveyDefinitionFileFields.REVISION_HEADER)) {
                out.append(line).append('\n');
            }
        }
        return out.toString();
    }

    /** Reads back the logged revision, normalising whatever temporal type the driver returns. */
    private Instant loggedRevision(UUID surveyKey) {
        Object value = em.createNativeQuery(
                        "SELECT MAX(revision) FROM survey.survey_log WHERE survey_key = ?1 AND outcome = 'SUCCESS'")
                .setParameter(1, surveyKey)
                .getSingleResult();
        return switch (value) {
            case null -> null;
            case Instant instant -> instant;
            case OffsetDateTime odt -> odt.toInstant();
            case java.sql.Timestamp ts -> ts.toInstant();
            default -> throw new IllegalStateException("Unexpected revision type: " + value.getClass());
        };
    }

    /**
     * UC-017: every export carries a survey_revision header, and a successful update records it
     * against the survey so the site can report which authored revision it is running.
     */
    @Test
    @TestTransaction
    void successfulUpdateRecordsTheFilesRevision() {
        Survey survey = newSurvey("UpdRev");
        insertStep(survey.id, 1, "Step A");

        String exported = surveyDefinitionExportService.exportSurvey(survey.id);
        assertTrue(exported.contains(SurveyDefinitionFileFields.REVISION_HEADER),
                "export should carry a revision header");

        OffsetDateTime revision = OffsetDateTime.parse("2026-09-15T12:00:00Z");
        SurveyDefinitionUpdateService.UpdateResult result = surveyDefinitionUpdateService.updateFromFile(
                toStream(withRevision(exported, revision)), "rev.elicit", survey.id);

        assertTrue(result.isSuccess(), () -> "update errors: " + result.getErrors());
        assertEquals(revision.toInstant(), loggedRevision(survey.surveyKey),
                "the applied file's revision should be recorded against the survey");
    }

    /**
     * UC-017: a file whose revision predates the newest one already applied here is refused, and
     * refused before anything is written — applying it would open NEW versions carrying OLDER
     * content rather than reverting the survey.
     */
    @Test
    @TestTransaction
    void updateWithOlderRevisionIsRejectedAndChangesNothing() {
        Survey survey = newSurvey("UpdRegress");
        long stepId = insertStep(survey.id, 1, "Step A");

        String exported = surveyDefinitionExportService.exportSurvey(survey.id);
        OffsetDateTime newer = OffsetDateTime.parse("2026-09-15T12:00:00Z");
        assertTrue(surveyDefinitionUpdateService.updateFromFile(
                toStream(withRevision(exported, newer)), "newer.elicit", survey.id).isSuccess());

        String older = withRevision(exported.replace("Step A", "Step A renamed"),
                OffsetDateTime.parse("2026-09-01T12:00:00Z"));
        SurveyDefinitionUpdateService.UpdateResult result =
                surveyDefinitionUpdateService.updateFromFile(toStream(older), "older.elicit", survey.id);

        assertFalse(result.isSuccess(), "an older revision should be refused");
        assertTrue(String.join("; ", result.getErrors()).contains("predates the newest revision"),
                () -> "unexpected errors: " + result.getErrors());
        assertEquals("Step A", queryOne("SELECT name FROM survey.steps WHERE id = ?1", stepId),
                "the rejected file must not have changed anything");
        assertEquals(1L, queryLong("SELECT count(*) FROM survey.steps WHERE survey_id = ?1", survey.id),
                "the rejected file must not have opened a new version");
    }

    /**
     * UC-017: re-applying the identical file is how an operator retries, so an equal revision is
     * not a regression.
     */
    @Test
    @TestTransaction
    void updateWithSameRevisionIsAllowed() {
        Survey survey = newSurvey("UpdSameRev");
        insertStep(survey.id, 1, "Step A");

        String exported = withRevision(surveyDefinitionExportService.exportSurvey(survey.id),
                OffsetDateTime.parse("2026-09-15T12:00:00Z"));
        assertTrue(surveyDefinitionUpdateService.updateFromFile(
                toStream(exported), "first.elicit", survey.id).isSuccess());

        SurveyDefinitionUpdateService.UpdateResult result = surveyDefinitionUpdateService.updateFromFile(
                toStream(exported), "again.elicit", survey.id);

        assertTrue(result.isSuccess(), () -> "re-applying the same revision should be allowed: "
                + result.getErrors());
        assertEquals(0, result.getCounts().get("steps").versioned(), "a replay should change nothing");
    }

    /**
     * UC-017: a file predating the revision header carries no revision to compare, so it is
     * applied rather than blocked — otherwise the first update after this upgrade would be
     * impossible.
     */
    @Test
    @TestTransaction
    void updateWithNoRevisionHeaderIsAllowedAndLogsNoRevision() {
        Survey survey = newSurvey("UpdNoRev");
        insertStep(survey.id, 1, "Step A");

        String exported = surveyDefinitionExportService.exportSurvey(survey.id);
        assertTrue(surveyDefinitionUpdateService.updateFromFile(
                toStream(withRevision(exported, OffsetDateTime.parse("2026-09-15T12:00:00Z"))),
                "newer.elicit", survey.id).isSuccess());

        SurveyDefinitionUpdateService.UpdateResult result = surveyDefinitionUpdateService.updateFromFile(
                toStream(withoutRevision(exported)), "legacy.elicit", survey.id);

        assertTrue(result.isSuccess(), () -> "a pre-revision file should still apply: " + result.getErrors());
    }

    /**
     * UC-017: a revision header that is present but unparseable is a rejection, not a silent
     * fallback to "unknown" — silently ignoring it would disable the regression check.
     */
    @Test
    @TestTransaction
    void updateWithCorruptRevisionHeaderIsRejected() {
        Survey survey = newSurvey("UpdBadRev");
        insertStep(survey.id, 1, "Step A");

        String exported = surveyDefinitionExportService.exportSurvey(survey.id);
        String corrupt = exported.replaceAll("(?m)^" + SurveyDefinitionFileFields.REVISION_HEADER + ".*$",
                SurveyDefinitionFileFields.REVISION_HEADER + " not-a-timestamp");

        SurveyDefinitionUpdateService.UpdateResult result =
                surveyDefinitionUpdateService.updateFromFile(toStream(corrupt), "corrupt.elicit", survey.id);

        assertFalse(result.isSuccess(), "a corrupt revision header should be refused");
        assertTrue(String.join("; ", result.getErrors()).contains("Unparseable"),
                () -> "unexpected errors: " + result.getErrors());
    }

    /**
     * UC-017 A3: a file for a different survey is reported as the key mismatch it is, not as a
     * revision regression — the revision check is deliberately deferred until after the key
     * match confirms the file belongs to this target.
     */
    @Test
    @TestTransaction
    void keyMismatchIsReportedAheadOfRevisionRegression() {
        Survey target = newSurvey("UpdKeyFirst");
        insertStep(target.id, 1, "Step A");
        Survey other = newSurvey("UpdKeyOther");
        insertStep(other.id, 1, "Other Step");

        assertTrue(surveyDefinitionUpdateService.updateFromFile(
                toStream(withRevision(surveyDefinitionExportService.exportSurvey(target.id),
                        OffsetDateTime.parse("2026-09-15T12:00:00Z"))),
                "newer.elicit", target.id).isSuccess());

        String foreign = withRevision(surveyDefinitionExportService.exportSurvey(other.id),
                OffsetDateTime.parse("2026-09-01T12:00:00Z"));
        SurveyDefinitionUpdateService.UpdateResult result =
                surveyDefinitionUpdateService.updateFromFile(toStream(foreign), "foreign.elicit", target.id);

        assertFalse(result.isSuccess());
        assertTrue(String.join("; ", result.getErrors()).contains("does not match the selected survey's key"),
                () -> "should report the key mismatch, not a regression: " + result.getErrors());
    }

    /**
     * A record the file marks as retired (closed effective_to, written by the authoring tool
     * when an element is removed) closes the target's current row and inserts nothing; applying
     * the same file again is a no-op for that record.
     */
    @Test
    @TestTransaction
    void retiredRecordInFileClosesTheTargetsCurrentRowWithoutInsertingOne() {
        Survey survey = newSurvey("UpdRetire");
        long stepSurrogateId = insertStep(survey.id, 1, "Doomed Step");
        UUID stepKey = queryOne("SELECT step_key FROM survey.steps WHERE id = ?1", stepSurrogateId);
        long stepDurableId = queryLong("SELECT step_id FROM survey.steps WHERE id = ?1", stepSurrogateId);

        String content = "# ELICIT_SURVEY_EXPORT_V1\n\n"
                + "surveys: " + survey.id + "|" + survey.surveyKey + "|" + survey.name + "|1|Title UpdRetire|||||\n"
                + "steps: 100|" + stepKey + "|1|Doomed Step|D||0|1970-01-01 00:00:00+00|2026-09-17 12:00:00+00||\n";

        SurveyDefinitionUpdateService.UpdateResult result =
                surveyDefinitionUpdateService.updateFromFile(toStream(content), "retire.elicit", survey.id);

        assertTrue(result.isSuccess(), () -> "update errors: " + result.getErrors());
        assertEquals(new SurveyDefinitionUpdateService.TableUpdateCounts(0, 0, 0, 1), result.getCounts().get("steps"));
        String effectiveTo = queryOne("SELECT effective_to::text FROM survey.steps WHERE id = ?1", stepSurrogateId);
        assertFalse(effectiveTo.startsWith("9999-12-31"), "the current row must be closed: " + effectiveTo);
        assertEquals(1L, queryLong("SELECT count(*) FROM survey.steps WHERE step_id = ?1", stepDurableId),
                "retiring must not insert a new version row");

        SurveyDefinitionUpdateService.UpdateResult again =
                surveyDefinitionUpdateService.updateFromFile(toStream(content), "retire.elicit", survey.id);
        assertTrue(again.isSuccess());
        assertEquals(new SurveyDefinitionUpdateService.TableUpdateCounts(0, 0, 1, 0), again.getCounts().get("steps"),
                "an already-retired element is left alone");
    }

    /**
     * Export carries an element whose every version is closed as its latest retired row, so the
     * removal travels to other sites; an element with a current row is exported as that row only.
     */
    @Test
    @TestTransaction
    void exportCarriesTheLatestRetiredRowWhenNoCurrentRowExists() {
        Survey survey = newSurvey("ExpRetire");
        long keptId = insertStep(survey.id, 1, "Kept Step");
        long goneId = insertStep(survey.id, 2, "Gone Step");
        em.createNativeQuery("UPDATE survey.steps SET effective_to = '2026-09-17 12:00:00+00' WHERE id = ?1")
                .setParameter(1, goneId).executeUpdate();

        String exported = surveyDefinitionExportService.exportSurvey(survey.id);
        java.util.List<String> stepLines = exported.lines().filter(l -> l.startsWith("steps: ")).toList();
        assertEquals(2, stepLines.size(), exported);
        String gone = stepLines.stream().filter(l -> l.contains("Gone Step")).findFirst().orElseThrow();
        String[] fields = SurveyDefinitionFileFields.parseFields(gone.substring("steps: ".length()));
        assertTrue(SurveyDefinitionFileFields.isRetired("steps", fields), "retired row keeps its closing instant: " + gone);
        String kept = stepLines.stream().filter(l -> l.contains("Kept Step")).findFirst().orElseThrow();
        assertFalse(SurveyDefinitionFileFields.isRetired("steps", SurveyDefinitionFileFields.parseFields(kept.substring("steps: ".length()))));
        assertEquals(1L, queryLong("SELECT count(*) FROM survey.steps WHERE id = ?1", keptId));
    }
}
