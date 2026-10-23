package com.elicitsoftware.test;

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

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A plain-test stand-in for an {@link EntityManager} that only answers native queries.
 * <p>
 * Every {@code createNativeQuery} returns a recording {@link Query}; when the query is run
 * ({@code getSingleResult}, {@code getResultList} or {@code executeUpdate}) the call is recorded
 * and the script decides the result. A script that returns a {@link RuntimeException} makes the
 * query throw it. Any other {@code EntityManager} or {@code Query} method fails the test.
 */
public final class ScriptedEntityManager {

    /** One executed native query. */
    public record Call(String sql, Map<Object, Object> params, String terminal) {

        /** The parameter bound at the given position or name. */
        public Object param(Object key) {
            return params.get(key);
        }

        /** Whether the SQL contains the fragment. */
        public boolean is(String fragment) {
            return sql.contains(fragment);
        }
    }

    private final List<Call> calls = new ArrayList<>();
    private final Function<Call, Object> script;

    /**
     * @param script maps each executed query to its result; for {@code executeUpdate} the
     *               result is ignored unless it is an exception
     */
    public ScriptedEntityManager(Function<Call, Object> script) {
        this.script = script;
    }

    /** The queries executed so far, in order. */
    public List<Call> calls() {
        return calls;
    }

    /** The executed queries whose SQL contains the fragment. */
    public List<Call> calls(String fragment) {
        return calls.stream().filter(c -> c.is(fragment)).toList();
    }

    /** The proxy to hand to the class under test. */
    public EntityManager entityManager() {
        return (EntityManager) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{EntityManager.class}, (proxy, method, args) -> {
                    if (method.getName().equals("createNativeQuery")) {
                        return query((String) args[0]);
                    }
                    throw new UnsupportedOperationException("EntityManager." + method.getName());
                });
    }

    private Query query(String sql) {
        Map<Object, Object> params = new LinkedHashMap<>();
        return (Query) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Query.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "setParameter" -> {
                            params.put(args[0], args[1]);
                            return proxy;
                        }
                        case "getSingleResult", "getResultList", "executeUpdate" -> {
                            Call call = new Call(sql, new LinkedHashMap<>(params), method.getName());
                            calls.add(call);
                            Object result = script.apply(call);
                            if (result instanceof RuntimeException e) {
                                throw e;
                            }
                            return method.getName().equals("executeUpdate") ? 1 : result;
                        }
                        default -> throw new UnsupportedOperationException("Query." + method.getName());
                    }
                });
    }
}
