package com.assetmanagement.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

@Component
public class RlsContextExecutor {

    private static final String APPLY_CONTEXT_SQL = """
            SELECT
                set_config('app.current_user_id', ?, true),
                set_config('app.current_tenant_id', ?, true),
                set_config('app.current_project_id', ?, true),
                set_config('app.is_platform_admin', ?, true)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final CurrentUserProvider currentUserProvider;
    private final boolean postgresql;
    private final boolean testDatabase;

    public RlsContextExecutor(
            JdbcTemplate jdbcTemplate,
            CurrentUserProvider currentUserProvider,
            @Value("${spring.datasource.url}") String datasourceUrl,
            Environment environment
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.currentUserProvider = currentUserProvider;
        this.postgresql = datasourceUrl.startsWith("jdbc:postgresql:");
        this.testDatabase = datasourceUrl.startsWith("jdbc:h2:")
                && Arrays.asList(environment.getActiveProfiles()).contains("test");
        if (!postgresql && !testDatabase) {
            throw new IllegalStateException("RLS context requires PostgreSQL outside the test profile");
        }
    }

    @Transactional
    public <T> T forCurrentUser(Supplier<T> work) {
        return forUser(currentUserProvider.requireCurrentUser().userId(), work);
    }

    @Transactional
    public <T> T forUser(UUID userId, Supplier<T> work) {
        return execute(userId, null, null, false, work);
    }

    @Transactional
    public <T> T inTenant(UUID tenantId, boolean platformAdmin, Supplier<T> work) {
        return execute(currentUserProvider.currentUserId().orElse(null), tenantId, null,
                platformAdmin, work);
    }

    @Transactional
    public <T> T inProject(
            UUID tenantId,
            UUID projectId,
            boolean platformAdmin,
            Supplier<T> work
    ) {
        return execute(currentUserProvider.currentUserId().orElse(null), tenantId, projectId,
                platformAdmin, work);
    }

    @Transactional
    public <T> T asPlatform(Supplier<T> work) {
        return execute(currentUserProvider.currentUserId().orElse(null), null, null, true, work);
    }

    private <T> T execute(
            UUID userId,
            UUID tenantId,
            UUID projectId,
            boolean platformAdmin,
            Supplier<T> work
    ) {
        Objects.requireNonNull(work, "work");
        if (postgresql) {
            jdbcTemplate.queryForList(
                    APPLY_CONTEXT_SQL,
                    stringValue(userId),
                    stringValue(tenantId),
                    stringValue(projectId),
                    Boolean.toString(platformAdmin)
            );
        }
        return work.get();
    }

    private static String stringValue(UUID value) {
        return value == null ? "" : value.toString();
    }
}
