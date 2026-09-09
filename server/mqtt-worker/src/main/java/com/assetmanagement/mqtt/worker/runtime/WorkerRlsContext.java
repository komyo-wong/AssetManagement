package com.assetmanagement.mqtt.worker.runtime;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

@Component
public class WorkerRlsContext {

    private static final String APPLY = """
            SELECT set_config('app.is_platform_admin', 'true', true)
            """;

    private final JdbcTemplate jdbcTemplate;

    public WorkerRlsContext(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public <T> T asPlatform(Supplier<T> work) {
        jdbcTemplate.queryForList(APPLY);
        return work.get();
    }

    @Transactional
    public void asPlatform(Runnable work) {
        asPlatform(() -> {
            work.run();
            return null;
        });
    }
}
