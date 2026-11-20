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
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-014 BR-110 and UC-017 BR-110/BR-111: how content translations ride the apply pipeline.
 * <p>
 * The cases that matter are the ones where a translation behaves differently from the structural
 * rows around it, because it is keyed to what it translates by that element's key and to itself by
 * its own: a wording change versions the question and leaves the translation alone, a retirement
 * closes the translation even when the file forgets to say so, and a stale hash travels verbatim
 * rather than being recomputed against this site's text.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class SurveyDefinitionTranslationUpdateTest {

    private static final String HASH_A = "a".repeat(64);
    private static final String HASH_B = "b".repeat(64);

    @Inject
    SurveyDefinitionUpdateService updateService;

    @Inject
    EntityManager em;

    private InputStream toStream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
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
        return ((Number) queryOne(sql, params)).longValue();
    }

    private long nextVal(String sequence) {
        return ((Number) em.createNativeQuery("SELECT nextval('" + sequence + "')").getSingleResult()).longValue();
    }

    /** A committed survey with one question, as the update service's logging requires. */
    private Survey surveyWithQuestion(String token, UUID questionKey) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Survey survey = new Survey();
            survey.name = "Tr " + token;
            survey.displayOrder = 1000 + (int) (Math.random() * 100000);
            survey.title = "Tr Title " + token;
            survey.persist();
            long id = nextVal("survey.questions_seq");
            em.createNativeQuery("INSERT INTO survey.questions (id, survey_id, question_key, type_id, text, short_text, required) "
                            + "VALUES (?1, ?2, ?3, 1, ?4, 'Q', false)")
                    .setParameter(1, id).setParameter(2, survey.id).setParameter(3, questionKey)
                    .setParameter(4, "Original wording").executeUpdate();
            return survey;
        });
    }

    private void seedTranslation(Survey survey, UUID translationKey, UUID questionKey, String value, String hash) {
        QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery("""
                        INSERT INTO survey.translations
                            (id, survey_id, translation_key, element_type, element_key, field, language, value, source_hash)
                        VALUES (nextval('survey.translations_seq'), ?1, ?2, 'questions', ?3, 'text', 'es-419', ?4, ?5)
                        """)
                .setParameter(1, survey.id).setParameter(2, translationKey).setParameter(3, questionKey)
                .setParameter(4, value).setParameter(5, hash).executeUpdate());
    }

    private String header(Survey survey) {
        return "# ELICIT_SURVEY_EXPORT_V1\n\nsurveys: " + survey.id + "|" + survey.surveyKey + "|" + survey.name
                + "|1|" + survey.title + "|||||\n";
    }

    private String translationRecord(UUID translationKey, UUID questionKey, String value, String hash, String effectiveTo) {
        return "translations: 7|" + translationKey + "|questions|" + questionKey + "|text|es-419|" + value + "|"
                + hash + "|0||" + effectiveTo + "||\n";
    }

    @Test
    @TestTransaction
    void aTranslationAbsentFromTheTargetIsCreated() {
        UUID questionKey = UUID.randomUUID();
        UUID translationKey = UUID.randomUUID();
        Survey survey = surveyWithQuestion("create", questionKey);

        var result = updateService.updateFromFile(
                toStream(header(survey) + translationRecord(translationKey, questionKey, "Redacción original", HASH_A, "")),
                "create.elicit", survey.id);

        assertTrue(result.isSuccess(), () -> "update errors: " + result.getErrors());
        assertEquals(new SurveyDefinitionUpdateService.TableUpdateCounts(1, 0, 0, 0),
                result.getCounts().get("translations"));
        assertEquals("Redacción original", queryOne(
                "SELECT value FROM survey.translations WHERE translation_key = ?1 AND effective_to = '9999-12-31 23:59:59+00'",
                translationKey));
    }

    @Test
    @TestTransaction
    void anIdenticalTranslationIsLeftAlone() {
        UUID questionKey = UUID.randomUUID();
        UUID translationKey = UUID.randomUUID();
        Survey survey = surveyWithQuestion("same", questionKey);
        seedTranslation(survey, translationKey, questionKey, "Redacción original", HASH_A);

        var result = updateService.updateFromFile(
                toStream(header(survey) + translationRecord(translationKey, questionKey, "Redacción original", HASH_A, "")),
                "same.elicit", survey.id);

        assertTrue(result.isSuccess(), () -> "update errors: " + result.getErrors());
        assertEquals(new SurveyDefinitionUpdateService.TableUpdateCounts(0, 0, 1, 0),
                result.getCounts().get("translations"));
        assertEquals(1, queryLong("SELECT COUNT(*) FROM survey.translations WHERE translation_key = ?1", translationKey),
                "an unchanged translation must not open a version");
    }

    @Test
    @TestTransaction
    void acorrectedTranslationClosesTheCurrentRowAndOpensVersionOne() {
        UUID questionKey = UUID.randomUUID();
        UUID translationKey = UUID.randomUUID();
        Survey survey = surveyWithQuestion("fix", questionKey);
        seedTranslation(survey, translationKey, questionKey, "Redacción original", HASH_A);
        long durableId = queryLong("SELECT translation_id FROM survey.translations WHERE translation_key = ?1", translationKey);

        var result = updateService.updateFromFile(
                toStream(header(survey) + translationRecord(translationKey, questionKey, "Redacción corregida", HASH_A, "")),
                "fix.elicit", survey.id);

        assertTrue(result.isSuccess(), () -> "update errors: " + result.getErrors());
        assertEquals(new SurveyDefinitionUpdateService.TableUpdateCounts(0, 1, 0, 0),
                result.getCounts().get("translations"));
        assertEquals(2, queryLong("SELECT COUNT(*) FROM survey.translations WHERE translation_id = ?1", durableId),
                "the correction is a second version under the same durable id");
        assertEquals("Redacción corregida", queryOne(
                "SELECT value FROM survey.translations WHERE translation_id = ?1 AND effective_to = '9999-12-31 23:59:59+00'",
                durableId));
        assertEquals(1, queryLong(
                "SELECT version FROM survey.translations WHERE translation_id = ?1 AND effective_to = '9999-12-31 23:59:59+00'",
                durableId));
    }

    @Test
    @TestTransaction
    void aStaleHashTravelsVerbatimAndIsNotRecomputed() {
        // The hash belongs to the base text the translator worked from, which lives at the
        // authoring instance. Recomputing it here would mark a stale translation fresh and serve
        // a respondent the wrong question in their language.
        UUID questionKey = UUID.randomUUID();
        UUID translationKey = UUID.randomUUID();
        Survey survey = surveyWithQuestion("stale", questionKey);
        seedTranslation(survey, translationKey, questionKey, "Redacción original", HASH_A);

        var result = updateService.updateFromFile(
                toStream(header(survey) + translationRecord(translationKey, questionKey, "Redacción original", HASH_B, "")),
                "stale.elicit", survey.id);

        assertTrue(result.isSuccess(), () -> "update errors: " + result.getErrors());
        assertEquals(new SurveyDefinitionUpdateService.TableUpdateCounts(0, 1, 0, 0),
                result.getCounts().get("translations"), "a changed hash alone is a new version");
        assertEquals(HASH_B, queryOne(
                "SELECT source_hash FROM survey.translations WHERE translation_key = ?1 AND effective_to = '9999-12-31 23:59:59+00'",
                translationKey));
    }

    @Test
    @TestTransaction
    void versioningTheQuestionLeavesItsTranslationsUntouched() {
        UUID questionKey = UUID.randomUUID();
        UUID translationKey = UUID.randomUUID();
        Survey survey = surveyWithQuestion("reword", questionKey);
        seedTranslation(survey, translationKey, questionKey, "Redacción original", HASH_A);
        long before = queryLong("SELECT id FROM survey.translations WHERE translation_key = ?1", translationKey);

        String content = header(survey)
                + "questions: 1|" + questionKey + "|1|Reworded question|Q||false|||||||||0||||\n";
        var result = updateService.updateFromFile(toStream(content), "reword.elicit", survey.id);

        assertTrue(result.isSuccess(), () -> "update errors: " + result.getErrors());
        assertEquals(new SurveyDefinitionUpdateService.TableUpdateCounts(0, 1, 0, 0),
                result.getCounts().get("questions"));
        assertEquals(before, queryLong(
                "SELECT id FROM survey.translations WHERE translation_key = ?1 AND effective_to = '9999-12-31 23:59:59+00'",
                translationKey),
                "a wording change is the translator's problem, not a structural one: same row, still current");
        assertEquals(1, queryLong("SELECT COUNT(*) FROM survey.translations WHERE translation_key = ?1", translationKey));
    }

    @Test
    @TestTransaction
    void retiringTheQuestionClosesItsTranslationsEvenWhenTheFileOmitsThem() {
        UUID questionKey = UUID.randomUUID();
        UUID translationKey = UUID.randomUUID();
        Survey survey = surveyWithQuestion("retire", questionKey);
        seedTranslation(survey, translationKey, questionKey, "Redacción original", HASH_A);

        // The question is marked retired; the file says nothing at all about its translation.
        String content = header(survey)
                + "questions: 1|" + questionKey + "|1|Original wording|Q||false|||||||||0||2026-01-01 00:00:00+00||\n";
        var result = updateService.updateFromFile(toStream(content), "retire.elicit", survey.id);

        assertTrue(result.isSuccess(), () -> "update errors: " + result.getErrors());
        assertEquals(0, queryLong(
                "SELECT COUNT(*) FROM survey.translations WHERE translation_key = ?1 AND effective_to = '9999-12-31 23:59:59+00'",
                translationKey),
                "a translation must never be current under a retired element");
        assertEquals(1, queryLong("SELECT COUNT(*) FROM survey.translations WHERE translation_key = ?1", translationKey),
                "closed, not deleted");
    }

    @Test
    @TestTransaction
    void aRetiredTranslationRecordClosesTheDeployedRow() {
        UUID questionKey = UUID.randomUUID();
        UUID translationKey = UUID.randomUUID();
        Survey survey = surveyWithQuestion("drop", questionKey);
        seedTranslation(survey, translationKey, questionKey, "Redacción original", HASH_A);

        var result = updateService.updateFromFile(
                toStream(header(survey) + translationRecord(translationKey, questionKey, "Redacción original", HASH_A,
                        "2026-01-01 00:00:00+00")),
                "drop.elicit", survey.id);

        assertTrue(result.isSuccess(), () -> "update errors: " + result.getErrors());
        assertEquals(new SurveyDefinitionUpdateService.TableUpdateCounts(0, 0, 0, 1),
                result.getCounts().get("translations"));
        assertEquals(0, queryLong(
                "SELECT COUNT(*) FROM survey.translations WHERE translation_key = ?1 AND effective_to = '9999-12-31 23:59:59+00'",
                translationKey));
    }

    @Test
    @TestTransaction
    void theSurveysPublishedLanguagesAreUpdatedInPlace() {
        UUID questionKey = UUID.randomUUID();
        Survey survey = surveyWithQuestion("langs", questionKey);

        String content = "# ELICIT_SURVEY_EXPORT_V1\n\nsurveys: " + survey.id + "|" + survey.surveyKey + "|"
                + survey.name + "|1|" + survey.title + "||||||en|es-419,ar\n";
        var result = updateService.updateFromFile(toStream(content), "langs.elicit", survey.id);

        assertTrue(result.isSuccess(), () -> "update errors: " + result.getErrors());
        assertEquals("es-419,ar", queryOne("SELECT content_languages FROM survey.surveys WHERE id = ?1", survey.id));
        assertEquals("en", queryOne("SELECT base_language FROM survey.surveys WHERE id = ?1", survey.id));
        assertEquals(new SurveyDefinitionUpdateService.TableUpdateCounts(0, 1, 0, 0),
                result.getCounts().get("surveys"), "a changed published set is a change to the survey");
    }

    @Test
    @TestTransaction
    void aTranslationOfAFieldThatCarriesNoRespondentTextIsRejected() {
        UUID questionKey = UUID.randomUUID();
        UUID translationKey = UUID.randomUUID();
        Survey survey = surveyWithQuestion("badfield", questionKey);

        String content = header(survey)
                + "translations: 7|" + translationKey + "|questions|" + questionKey
                + "|default_value|es-419|Sí|" + HASH_A + "|0||||\n";

        // A malformed record aborts the whole update by exception (A5), as it does for the
        // structural tables; nothing of the file is applied.
        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> updateService.updateFromFile(toStream(content), "badfield.elicit", survey.id));
        assertTrue(thrown.getMessage().contains("default_value"), thrown.getMessage());
        assertEquals(0, queryLong("SELECT COUNT(*) FROM survey.translations WHERE translation_key = ?1", translationKey));
    }
}
