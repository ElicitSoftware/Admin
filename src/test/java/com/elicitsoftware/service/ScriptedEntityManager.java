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

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A stand-in {@link EntityManager} for the plain tests of the survey-definition services
 * (UC-013 export, UC-014 import, UC-017 update), which talk to the database only through
 * native queries.
 * <p>
 * Only {@code createNativeQuery(String)} is implemented. Every query it hands out records its
 * parameters and, when executed, records one {@link Call} and asks {@link #answer} what to
 * return: the single result, the result list, or the update count. An answer that is a
 * {@link RuntimeException} is thrown instead. Anything else the code under test calls fails
 * loudly, so a test can never pass by silently doing nothing.
 */
final class ScriptedEntityManager {

    /** How a query was executed. */
    enum Kind { SINGLE, LIST, UPDATE }

    /** One executed query: its SQL, its bound parameters (by position or name) and how it ran. */
    record Call(String sql, Map<Object, Object> params, Kind kind) {

        Object param(Object position) {
            return params.get(position);
        }

        boolean sqlContains(String fragment) {
            return sql.contains(fragment);
        }
    }

    final List<Call> calls = new ArrayList<>();

    /**
     * Answers each executed query. The default answers no single result ({@code null}), an
     * empty result list and an update count of 1.
     */
    Function<Call, Object> answer = call -> switch (call.kind()) {
        case SINGLE -> null;
        case LIST -> List.of();
        case UPDATE -> 1;
    };

    final EntityManager em = (EntityManager) Proxy.newProxyInstance(
            EntityManager.class.getClassLoader(), new Class<?>[]{EntityManager.class},
            (proxy, method, args) -> {
                if (method.getName().equals("createNativeQuery") && args != null && args.length == 1) {
                    return query((String) args[0]);
                }
                if (method.getDeclaringClass() == Object.class) {
                    return switch (method.getName()) {
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> "ScriptedEntityManager";
                    };
                }
                throw new UnsupportedOperationException("EntityManager." + method.getName());
            });

    private Query query(String sql) {
        Map<Object, Object> params = new LinkedHashMap<>();
        Query[] self = new Query[1];
        self[0] = (Query) Proxy.newProxyInstance(Query.class.getClassLoader(), new Class<?>[]{Query.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "setParameter" -> {
                        if (args.length != 2) {
                            throw new UnsupportedOperationException("Query.setParameter/" + args.length);
                        }
                        params.put(args[0], args[1]);
                        yield self[0];
                    }
                    case "getSingleResult" -> run(sql, params, Kind.SINGLE);
                    case "getResultList" -> run(sql, params, Kind.LIST);
                    case "executeUpdate" -> run(sql, params, Kind.UPDATE);
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "Query[" + sql + "]";
                    default -> throw new UnsupportedOperationException("Query." + method.getName());
                });
        return self[0];
    }

    private Object run(String sql, Map<Object, Object> params, Kind kind) {
        Call call = new Call(sql, new LinkedHashMap<>(params), kind);
        calls.add(call);
        Object result = answer.apply(call);
        if (result instanceof RuntimeException e) {
            throw e;
        }
        return result;
    }

    /** The executed calls whose SQL contains {@code fragment}, in execution order. */
    List<Call> callsContaining(String fragment) {
        return calls.stream().filter(c -> c.sqlContains(fragment)).collect(Collectors.toList());
    }

    /** The one executed call whose SQL contains {@code fragment}; fails unless there is exactly one. */
    Call only(String fragment) {
        List<Call> matches = callsContaining(fragment);
        if (matches.size() != 1) {
            throw new AssertionError("Expected one call containing <" + fragment + "> but found "
                    + matches.size() + ": " + calls.stream().map(Call::sql).toList());
        }
        return matches.get(0);
    }
}
