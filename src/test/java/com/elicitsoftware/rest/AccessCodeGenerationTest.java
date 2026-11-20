package com.elicitsoftware.rest;

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

import com.elicitsoftware.exception.AccessCodeGenerationError;
import com.elicitsoftware.model.Respondent;
import com.elicitsoftware.model.Survey;
import com.elicitsoftware.test.PostgresTestResource;
import com.elicitsoftware.util.RandomString;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AccessCodeService#generateAccessCode(int)} against a generator that can only produce
 * two codes, so collisions can be forced.
 *
 * <p>Traceability: UC-015 (Generate Survey Access Code), A1 (collision) and A2 (unable to
 * generate a unique access code). The retry counter used to count up from 4 while the loop
 * waited for it to reach 0, so a survey whose codes all collided looped forever.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class AccessCodeGenerationTest {

    @Inject
    AccessCodeService service;

    private RandomString originalGenerator;

    private Field generatorField() throws NoSuchFieldException {
        Field field = AccessCodeService.class.getDeclaredField("generator");
        field.setAccessible(true);
        return field;
    }

    /** Counts the draws made by the most recent {@link #scripted} generator. */
    private final AtomicInteger draws = new AtomicInteger();

    /** A Random that returns the given symbol indexes in order, repeating the last one. */
    private Random scripted(int... indexes) {
        draws.set(0);
        return new Random() {
            @Override
            public int nextInt(int bound) {
                int next = draws.getAndIncrement();
                return indexes[Math.min(next, indexes.length - 1)];
            }
        };
    }

    /** Length 1 over "BC" can only ever produce "B" (index 0) or "C" (index 1). */
    private void useGenerator(Random random) throws Exception {
        generatorField().set(ClientProxy.unwrap(service), new RandomString(1, random, "BC"));
    }

    @BeforeEach
    void rememberGenerator() throws Exception {
        originalGenerator = (RandomString) generatorField().get(ClientProxy.unwrap(service));
    }

    @AfterEach
    void restoreGenerator() throws Exception {
        generatorField().set(ClientProxy.unwrap(service), originalGenerator);
    }

    private void persistRespondent(Survey survey, String accessCode) {
        Respondent respondent = new Respondent();
        respondent.survey = survey;
        respondent.accessCode = accessCode;
        respondent.active = true;
        respondent.persist();
    }

    private Survey survey() {
        Survey survey = Survey.findById(1L);
        assertNotNull(survey, "test bootstrap should have seeded survey id=1");
        return survey;
    }

    /** UC-015 A2: when every candidate collides, generation gives up instead of looping forever. */
    @Test
    @TestTransaction
    void givesUpWhenEveryCandidateCollides() throws Exception {
        useGenerator(scripted(0, 1));
        Survey survey = survey();
        persistRespondent(survey, "B");
        persistRespondent(survey, "C");

        AccessCodeGenerationError error = assertTimeout(Duration.ofSeconds(10),
                () -> assertThrows(AccessCodeGenerationError.class, () -> service.generateAccessCode(survey.id)));
        assertEquals("Unable to generate a unique access code", error.getMessage());
        assertEquals(AccessCodeService.MAX_ACCESS_CODE_ATTEMPTS, draws.get());
    }

    /** UC-015 A1: a colliding candidate is discarded and a free one is used. */
    @Test
    @TestTransaction
    void skipsCollidingCandidate() throws Exception {
        useGenerator(scripted(0, 0, 1));
        Survey survey = survey();
        persistRespondent(survey, "B");

        // Draws B, B, C: the first two collide, the third is free.
        Respondent respondent = assertTimeout(Duration.ofSeconds(10), () -> service.generateAccessCode(survey.id));

        assertEquals("C", respondent.accessCode);
        assertEquals(3, draws.get());
        assertEquals(survey.id, respondent.survey.id);
        assertTrue(respondent.active);
    }
}
