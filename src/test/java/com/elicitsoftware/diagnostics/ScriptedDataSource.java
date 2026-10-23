package com.elicitsoftware.diagnostics;

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

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * A hand-written JDBC stand-in for plain tests of the diagnostics: every query, plain or
 * prepared, is answered by {@link Script} from its SQL and its bound parameters, and the test can
 * read back what was executed and how many connections were closed. Only the handful of JDBC
 * methods the diagnostics call are supported.
 */
final class ScriptedDataSource {

    /** Answers one query with its rows (each an array of column values), or throws. */
    interface Script {
        List<Object[]> answer(String sql, List<Object> parameters) throws SQLException;
    }

    final List<String> executed = new ArrayList<>();
    final List<List<Object>> bound = new ArrayList<>();
    int connectionsClosed;
    private final Script script;
    private final SQLException connectFailure;

    private ScriptedDataSource(Script script, SQLException connectFailure) {
        this.script = script;
        this.connectFailure = connectFailure;
    }

    static ScriptedDataSource answering(Script script) {
        return new ScriptedDataSource(script, null);
    }

    /** A source whose every query answers the one row {@code row}. */
    static ScriptedDataSource answeringRow(Object... row) {
        List<Object[]> rows = new ArrayList<>();
        rows.add(row);
        return answering((sql, parameters) -> rows);
    }

    static ScriptedDataSource refusing(String message) {
        return new ScriptedDataSource(null, new SQLException(message));
    }

    DataSource dataSource() {
        return proxy(DataSource.class, (method, args) -> {
            if (method.equals("getConnection")) {
                if (connectFailure != null) {
                    throw connectFailure;
                }
                return connection();
            }
            throw new UnsupportedOperationException(method);
        });
    }

    private Connection connection() {
        return proxy(Connection.class, (method, args) -> switch (method) {
            case "createStatement" -> statement(Statement.class, null);
            case "prepareStatement" -> statement(PreparedStatement.class, (String) args[0]);
            case "close" -> {
                connectionsClosed++;
                yield null;
            }
            default -> throw new UnsupportedOperationException(method);
        });
    }

    private <S extends Statement> S statement(Class<S> type, String preparedSql) {
        List<Object> parameters = new ArrayList<>();
        return proxy(type, (method, args) -> switch (method) {
            case "setString" -> {
                parameters.add(args[1]);
                yield null;
            }
            case "executeQuery", "execute" -> {
                String sql = preparedSql != null ? preparedSql : (String) args[0];
                executed.add(sql);
                bound.add(List.copyOf(parameters));
                List<Object[]> rows = script.answer(sql, parameters);
                yield method.equals("execute") ? Boolean.TRUE : resultSet(rows);
            }
            case "close" -> null;
            default -> throw new UnsupportedOperationException(method);
        });
    }

    private static ResultSet resultSet(List<Object[]> rows) {
        int[] index = {-1};
        return proxy(ResultSet.class, (method, args) -> switch (method) {
            case "next" -> ++index[0] < rows.size();
            case "getString", "getBoolean" -> rows.get(index[0])[(Integer) args[0] - 1];
            case "close" -> null;
            default -> throw new UnsupportedOperationException(method);
        });
    }

    private interface Handler {
        Object handle(String method, Object[] args) throws Throwable;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Handler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, method, args) -> handler.handle(method.getName(), args));
    }
}
