package com.assetmanagement.ops;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class PlatformCleanupIndexBootstrap {

    private static final Logger log = LoggerFactory.getLogger(PlatformCleanupIndexBootstrap.class);
    private static final List<String> INDEXES = List.of(
            "CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_mqtt_inbox_received_at ON mqtt_inbox (received_at)",
            "CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_scan_events_received_at ON scan_events (received_at)",
            "CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_beacon_presence_events_created_at ON beacon_presence_events (created_at)",
            "CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_mqtt_outbound_commands_created_at ON mqtt_outbound_commands (created_at)",
            "CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_hcbg_gatt_ops_created_at ON hcbg_gatt_ops (created_at)",
            "CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_hcbg_eink_ops_created_at ON hcbg_eink_ops (created_at)",
            "CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_eink_push_jobs_created_at ON eink_push_jobs (created_at)",
            "CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_alert_events_resolved_at ON alert_events (resolved_at)",
            "CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_notification_deliveries_created_at ON notification_deliveries (created_at)",
            "CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_audit_logs_created_at ON audit_logs (created_at)"
    );

    private final PlatformJdbcEndpoint jdbcEndpoint;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(thread -> {
        Thread worker = new Thread(thread, "platform-ops-cleanup-indexes");
        worker.setDaemon(true);
        return worker;
    });

    public PlatformCleanupIndexBootstrap(
            @Value("${spring.datasource.url:}") String datasourceUrl,
            @Value("${spring.datasource.username:}") String datasourceUsername,
            @Value("${spring.datasource.password:}") String datasourcePassword
    ) {
        this.jdbcEndpoint = datasourceUrl != null && datasourceUrl.startsWith("jdbc:postgresql:")
                ? PlatformJdbcEndpoint.parse(datasourceUrl, datasourceUsername, datasourcePassword)
                : null;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (jdbcEndpoint == null) {
            return;
        }
        executor.execute(this::ensureIndexes);
    }

    private void ensureIndexes() {
        String url = "jdbc:postgresql://" + jdbcEndpoint.host() + ":" + jdbcEndpoint.port()
                + "/" + jdbcEndpoint.database() + "?connectTimeout=10&socketTimeout=0";
        try (Connection connection = DriverManager.getConnection(
                url, jdbcEndpoint.username(), jdbcEndpoint.password()
        )) {
            connection.setAutoCommit(true);
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET statement_timeout = 0");
                for (String sql : INDEXES) {
                    try {
                        statement.execute(sql);
                    } catch (SQLException ex) {
                        log.warn("Cleanup index skipped: {} ({})", sql, ex.getMessage());
                    }
                }
            }
            log.info("Cleanup time indexes are ready");
        } catch (SQLException ex) {
            log.warn("Unable to ensure cleanup time indexes: {}", ex.getMessage());
        }
    }
}
