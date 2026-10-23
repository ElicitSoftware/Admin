package com.elicitsoftware.participation;

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
 * A hand-written stand-in for the {@link EntityManager} {@link ParticipationService} reads
 * through (UC-031 BR-127): every native query is answered by the first handler whose SQL
 * fragment it contains, given the parameters bound to it, and every executed query is recorded
 * so a test can assert on the SQL and the bindings. Only {@code createNativeQuery(String)},
 * {@code setParameter(String, Object)} and {@code getResultList()} are supported.
 */
final class NativeQueryStub {

    /** One executed query: its SQL and its named parameters. */
    record Executed(String sql, Map<String, Object> parameters) {
    }

    private record Handler(String fragment, Function<Map<String, Object>, List<?>> rows) {
    }

    private final List<Handler> handlers = new ArrayList<>();
    final List<Executed> executed = new ArrayList<>();

    /** Answers queries containing {@code fragment} with {@code rows(parameters)}. */
    NativeQueryStub on(String fragment, Function<Map<String, Object>, List<?>> rows) {
        handlers.add(new Handler(fragment, rows));
        return this;
    }

    /** Answers queries containing {@code fragment} with the same rows whatever the parameters. */
    NativeQueryStub on(String fragment, List<?> rows) {
        return on(fragment, p -> rows);
    }

    /** The executed queries whose SQL contains {@code fragment}. */
    List<Executed> executed(String fragment) {
        return executed.stream().filter(e -> e.sql().contains(fragment)).toList();
    }

    EntityManager entityManager() {
        return (EntityManager) Proxy.newProxyInstance(EntityManager.class.getClassLoader(),
                new Class<?>[]{EntityManager.class}, (proxy, method, args) -> {
                    if (method.getName().equals("createNativeQuery") && args.length == 1) {
                        return query((String) args[0]);
                    }
                    throw new UnsupportedOperationException(method.toString());
                });
    }

    private Query query(String sql) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        return (Query) Proxy.newProxyInstance(Query.class.getClassLoader(), new Class<?>[]{Query.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "setParameter" -> {
                        if (args.length != 2 || !(args[0] instanceof String name)) {
                            throw new UnsupportedOperationException(method.toString());
                        }
                        parameters.put(name, args[1]);
                        yield proxy;
                    }
                    case "getResultList" -> {
                        executed.add(new Executed(sql, Map.copyOf(parameters)));
                        yield handlers.stream().filter(h -> sql.contains(h.fragment())).findFirst()
                                .map(h -> new ArrayList<Object>(h.rows().apply(parameters)))
                                .orElseThrow(() -> new AssertionError("unexpected query: " + sql));
                    }
                    default -> throw new UnsupportedOperationException(method.toString());
                });
    }
}
