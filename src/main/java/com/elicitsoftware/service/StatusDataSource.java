package com.elicitsoftware.service;

import com.elicitsoftware.model.Status;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

import java.util.stream.Stream;

public class StatusDataSource {
    public Stream<Status> fetch(String sql, int offset, long limit) {
        return stream(sql, offset, limit).map(entity -> (Status) entity);
    }

    private Stream<PanacheEntityBase> stream(String sql, int offset, long limit) {
        // emulate accessing the backend datasource - in a real application this would
        // call, for example, an SQL query, passing an offset and a limit to the query
        return Status.stream(sql).skip(offset).limit(limit);
    }

    public int count(String sql) {
        return (int) Status.stream(sql).count();
    }
}
