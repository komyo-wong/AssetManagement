package com.assetmanagement.ops;

import com.assetmanagement.ops.domain.PlatformOpsSettings;
import com.assetmanagement.ops.repository.PlatformOpsSettingsRepository;
import com.assetmanagement.iam.PermissionCodes;
import com.assetmanagement.platform.application.PlatformAuditRecorder;
import com.assetmanagement.security.CurrentUserPrincipal;
import com.assetmanagement.security.CurrentUserProvider;
import com.assetmanagement.security.RlsContextExecutor;
import com.assetmanagement.shared.exception.BusinessException;
import com.assetmanagement.shared.exception.ErrorCode;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

@Service
public class PlatformOpsService {

    static final String WORKER_RESTART_KEY = "am:ops:restart:worker";
    static final String CONFIRM_RESTORE = "RESTORE";
    static final String CONFIRM_RESTART = "RESTART";
    /** Raw MQTT payloads dominate disk; keep table schema, skip row data in backups. */
    private static final List<String> BACKUP_EXCLUDE_TABLE_DATA = List.of("mqtt_inbox");
    private static final Duration DUMP_TIMEOUT = Duration.ofMinutes(30);
    private static final Duration RESTORE_TIMEOUT = Duration.ofMinutes(30);
    private static final int CLEANUP_BATCH_SIZE = 5000;
    private static final int CLEANUP_PREVIEW_CAP = 10_001;
    private static final int CLEANUP_SOCKET_TIMEOUT_SECONDS = 120;

    private static final Logger log = LoggerFactory.getLogger(PlatformOpsService.class);
    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneId.of("Asia/Shanghai"));
    private static final Pattern SAFE_BACKUP_NAME = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,180}$");
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private final CurrentUserProvider currentUserProvider;
    private final RlsContextExecutor rlsContextExecutor;
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    private final StringRedisTemplate redisTemplate;
    private final PlatformAuditRecorder platformAuditRecorder;
    private final PlatformOpsProperties properties;
    private final PlatformDockerClient dockerClient;
    private final PlatformOpsSettingsRepository settingsRepository;
    private final ObjectMapper objectMapper;
    private final PlatformJdbcEndpoint jdbcEndpoint;
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private final PlatformCleanupJobState cleanupJob = new PlatformCleanupJobState();
    private final ExecutorService cleanupExecutor = Executors.newSingleThreadExecutor(thread -> {
        Thread worker = new Thread(thread, "platform-ops-cleanup");
        worker.setDaemon(true);
        return worker;
    });
    private final Path backupDir;

    public PlatformOpsService(
            CurrentUserProvider currentUserProvider,
            RlsContextExecutor rlsContextExecutor,
            JdbcTemplate jdbcTemplate,
            DataSource dataSource,
            StringRedisTemplate redisTemplate,
            PlatformAuditRecorder platformAuditRecorder,
            PlatformOpsProperties properties,
            PlatformDockerClient dockerClient,
            PlatformOpsSettingsRepository settingsRepository,
            ObjectMapper objectMapper,
            @Value("${spring.datasource.url:}") String datasourceUrl,
            @Value("${spring.datasource.username:}") String datasourceUsername,
            @Value("${spring.datasource.password:}") String datasourcePassword
    ) throws IOException {
        this.currentUserProvider = currentUserProvider;
        this.rlsContextExecutor = rlsContextExecutor;
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
        this.redisTemplate = redisTemplate;
        this.platformAuditRecorder = platformAuditRecorder;
        this.properties = properties;
        this.dockerClient = dockerClient;
        this.settingsRepository = settingsRepository;
        this.objectMapper = objectMapper;
        this.jdbcEndpoint = datasourceUrl != null && datasourceUrl.startsWith("jdbc:postgresql:")
                ? PlatformJdbcEndpoint.parse(datasourceUrl, datasourceUsername, datasourcePassword)
                : null;
        this.backupDir = Path.of(properties.backupDir()).toAbsolutePath().normalize();
        Files.createDirectories(this.backupDir);
    }

    public Map<String, Object> overview() {
        require(PermissionCodes.PLATFORM_OPS_READ);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("postgresql", jdbcEndpoint != null);
        map.put("backupDir", backupDir.toString());
        map.put("maxBackups", properties.maxBackups());
        map.put("docker", dockerClient.inspect());
        map.put("services", health());
        map.put("cleanupTargets", PlatformCleanupTarget.all().stream().map(PlatformCleanupTarget::describe).toList());
        map.put("cleanupJob", cleanupJob.toMap());
        map.put("backups", listBackups());
        map.put("tools", toolStatus());
        map.put("autoCleanup", autoCleanupMap(loadSettings()));
        return map;
    }

    public List<Map<String, Object>> health() {
        require(PermissionCodes.PLATFORM_OPS_READ);
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(probe("api", "API", true, "本进程"));
        rows.add(probeWorker());
        rows.add(probePostgres());
        rows.add(probeRedis());
        rows.add(probeMqtt());
        if (dockerClient.available()) {
            for (Map<String, Object> container : dockerClient.listContainers()) {
                String service = String.valueOf(container.get("service"));
                if ("web".equals(service)) {
                    boolean up = "running".equalsIgnoreCase(String.valueOf(container.get("state")));
                    rows.add(probe(service, displayName(service), up, String.valueOf(container.get("status"))));
                }
            }
        }
        return rows;
    }

    public Map<String, Object> previewCleanup(Map<String, Object> body) {
        require(PermissionCodes.PLATFORM_OPS_READ);
        CleanupPlan plan = parsePlan(body);
        List<Map<String, Object>> items = new ArrayList<>();
        long total = 0;
        boolean approximate = false;
        for (CleanupPlan.Item item : plan.items()) {
            CountEstimate estimate = estimateCount(item);
            total += estimate.count();
            approximate = approximate || estimate.approximate();
            Map<String, Object> row = item.target().describe();
            row.put("days", item.days());
            row.put("cutoff", item.cutoff().toString());
            row.put("count", estimate.count());
            row.put("approximate", estimate.approximate());
            items.add(row);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", total);
        result.put("approximate", approximate);
        return result;
    }

    public Map<String, Object> executeCleanup(Map<String, Object> body) {
        require(PermissionCodes.PLATFORM_OPS_MANAGE);
        CleanupPlan plan = parsePlan(body);
        if (plan.items().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请至少选择一类要清理的数据");
        }
        acquireBusy("清理");
        List<String> targets = plan.items().stream().map(item -> item.target().id()).toList();
        cleanupJob.start(targets);
        SecurityContext securityContext = SecurityContextHolder.getContext();
        cleanupExecutor.execute(() -> {
            SecurityContextHolder.setContext(securityContext);
            try {
                runCleanupJob(plan, true);
            } finally {
                SecurityContextHolder.clearContext();
            }
        });
        Map<String, Object> accepted = new LinkedHashMap<>(cleanupJob.toMap());
        accepted.put("accepted", true);
        accepted.put("message", "流水过多时会在后台分批清理，请稍候刷新进度");
        return accepted;
    }

    public Map<String, Object> cleanupStatus() {
        require(PermissionCodes.PLATFORM_OPS_READ);
        return cleanupJob.toMap();
    }

    public Map<String, Object> updateAutoCleanup(Map<String, Object> body) {
        require(PermissionCodes.PLATFORM_OPS_MANAGE);
        PlatformOpsSettings settings = loadSettings();
        boolean enabled = body != null && body.get("enabled") instanceof Boolean b
                ? b
                : settings.isAutoCleanupEnabled();
        int intervalDays = settings.getAutoCleanupDays();
        if (body != null) {
            if (body.get("intervalDays") != null) {
                intervalDays = toDays(body.get("intervalDays"));
            } else if (body.get("days") != null) {
                intervalDays = toDays(body.get("days"));
            }
        }
        if (intervalDays < 1 || intervalDays > 3650) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "自动清理间隔天数需在 1–3650 之间");
        }
        List<Map<String, Object>> rules = body != null && body.containsKey("rules")
                ? normalizeRules(body.get("rules"))
                : readRules(settings.getCleanupRulesJson());
        if (enabled && rules.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "启用自动清理前请至少勾选一条规则并保存");
        }
        settings.apply(enabled, intervalDays, writeRules(rules));
        settingsRepository.save(settings);
        platformAuditRecorder.record("platform.ops.auto_cleanup", "platform_ops", "auto-cleanup", Map.of(
                "enabled", enabled,
                "intervalDays", intervalDays,
                "rules", rules.size()
        ));
        return autoCleanupMap(settings);
    }

    @Scheduled(cron = "${app.ops.auto-cleanup-cron:0 20 3 * * *}", zone = "Asia/Shanghai")
    public void runScheduledCleanup() {
        PlatformOpsSettings settings = loadSettings();
        if (!settings.isAutoCleanupEnabled()) {
            return;
        }
        List<Map<String, Object>> rules = readRules(settings.getCleanupRulesJson());
        if (rules.isEmpty()) {
            log.info("Scheduled ops cleanup skipped: no saved rules");
            return;
        }
        Instant last = settings.getLastAutoCleanupAt();
        int intervalDays = Math.max(1, settings.getAutoCleanupDays());
        if (last != null && Instant.now().isBefore(last.plus(Duration.ofDays(intervalDays)))) {
            return;
        }
        if (busy.get()) {
            log.info("Scheduled ops cleanup skipped: another ops job is running");
            return;
        }
        try {
            acquireBusy("清理");
        } catch (BusinessException ex) {
            log.info("Scheduled ops cleanup skipped: {}", ex.getMessage());
            return;
        }
        CleanupPlan plan = planFromRules(rules);
        if (plan.items().isEmpty()) {
            busy.set(false);
            return;
        }
        cleanupJob.start(plan.items().stream().map(item -> item.target().id()).toList());
        Map<String, Object> result = runCleanupJob(plan, false);
        if ("succeeded".equals(result.get("status"))) {
            long total = result.get("total") instanceof Number n ? n.longValue() : 0L;
            settings.markRun(total, Instant.now());
            settingsRepository.save(settings);
            log.info("Scheduled ops cleanup deleted {} rows using {} saved rules (every {} days)",
                    total, rules.size(), intervalDays);
        }
    }

    private Map<String, Object> runCleanupJob(CleanupPlan plan, boolean recordManualAudit) {
        List<Map<String, Object>> items = new ArrayList<>();
        List<String> vacuumTables = new ArrayList<>();
        long total = 0;
        try {
            for (CleanupPlan.Item item : ordered(plan.items())) {
                cleanupJob.progress(item.target().table(), total, items);
                long deleted = deleteInBatches(item);
                total += deleted;
                Map<String, Object> row = item.target().describe();
                row.put("days", item.days());
                row.put("deleted", deleted);
                items.add(row);
                if (deleted > 0) {
                    vacuumTables.add(item.target().table());
                }
                cleanupJob.progress(item.target().table(), total, items);
            }
            vacuumTables(vacuumTables);
            cleanupJob.succeed(total, items);
            if (recordManualAudit) {
                platformAuditRecorder.record("platform.ops.cleanup", "platform_ops", "cleanup", Map.of(
                        "total", total,
                        "targets", plan.items().stream().map(item -> item.target().id()).toList()
                ));
            }
        } catch (RuntimeException ex) {
            String message = cleanupFailureMessage(ex);
            log.warn("Ops cleanup failed after {} rows: {}", total, message);
            cleanupJob.fail(message, total, items);
        } finally {
            busy.set(false);
        }
        return cleanupJob.toMap();
    }

    public Map<String, Object> createBackup() {
        require(PermissionCodes.PLATFORM_OPS_MANAGE);
        requirePostgres();
        acquireBusy("备份");
        try {
            String name = "asset-management-" + FILE_TS.format(Instant.now()) + ".sql.gz";
            Path target = backupDir.resolve(name);
            dumpDatabase(target);
            pruneOldBackups();
            Map<String, Object> row = backupRow(target);
            platformAuditRecorder.record("platform.ops.backup", "platform_ops", name, Map.of(
                    "fileName", name,
                    "sizeBytes", row.get("sizeBytes")
            ));
            return row;
        } finally {
            busy.set(false);
        }
    }

    public List<Map<String, Object>> listBackups() {
        require(PermissionCodes.PLATFORM_OPS_READ);
        List<Map<String, Object>> rows = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(backupDir)) {
            for (Path path : stream) {
                if (Files.isRegularFile(path) && SAFE_BACKUP_NAME.matcher(path.getFileName().toString()).matches()) {
                    rows.add(backupRow(path));
                }
            }
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "无法读取备份目录");
        }
        rows.sort(Comparator.comparing((Map<String, Object> row) -> String.valueOf(row.get("fileName"))).reversed());
        return rows;
    }

    public ResponseEntity<Resource> downloadBackup(String fileName) {
        require(PermissionCodes.PLATFORM_OPS_READ);
        Path path = resolveBackup(fileName);
        Resource resource = new FileSystemResource(path);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + path.getFileName() + "\"")
                .body(resource);
    }

    public Map<String, Object> deleteBackup(String fileName) {
        require(PermissionCodes.PLATFORM_OPS_MANAGE);
        Path path = resolveBackup(fileName);
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "删除备份失败");
        }
        platformAuditRecorder.record("platform.ops.backup_delete", "platform_ops", fileName, Map.of(
                "fileName", fileName
        ));
        return Map.of("deleted", true, "fileName", fileName);
    }

    public Map<String, Object> restoreBackup(String fileName, String confirm) {
        require(PermissionCodes.PLATFORM_OPS_MANAGE);
        requireConfirm(confirm, CONFIRM_RESTORE, "请输入 RESTORE 以确认恢复数据库");
        Path path = resolveBackup(fileName);
        return restoreFromFile(path, fileName);
    }

    public Map<String, Object> restoreUpload(MultipartFile file, String confirm) {
        require(PermissionCodes.PLATFORM_OPS_MANAGE);
        requireConfirm(confirm, CONFIRM_RESTORE, "请输入 RESTORE 以确认恢复数据库");
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请上传备份文件");
        }
        String original = file.getOriginalFilename() == null ? "upload.sql" : file.getOriginalFilename();
        String safe = sanitizeUploadName(original);
        Path stored = backupDir.resolve(safe);
        try {
            Files.copy(file.getInputStream(), stored, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "保存上传备份失败");
        }
        return restoreFromFile(stored, safe);
    }

    public Map<String, Object> restart(String service, String confirm) {
        require(PermissionCodes.PLATFORM_OPS_MANAGE);
        requireConfirm(confirm, CONFIRM_RESTART, "请输入 RESTART 以确认重启服务");
        String target = service == null ? "" : service.trim().toLowerCase(Locale.ROOT);
        if (!PlatformDockerClient.SERVICE_ORDER.contains(target) && !"api".equals(target) && !"worker".equals(target)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的服务：" + service);
        }
        platformAuditRecorder.record("platform.ops.restart", "platform_ops", target, Map.of("service", target));
        if ("api".equals(target)) {
            restartApi();
            return Map.of("accepted", true, "service", target, "mode", restartMode("api"));
        }
        if ("worker".equals(target)) {
            restartWorker();
            return Map.of("accepted", true, "service", target, "mode", restartMode("worker"));
        }
        if (!dockerClient.available()) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "重启 " + target + " 需要 API 容器挂载 docker.sock（当前不可用）"
            );
        }
        try {
            dockerClient.restartService(target);
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, ex.getMessage());
        }
        return Map.of("accepted", true, "service", target, "mode", "docker");
    }

    private Map<String, Object> restoreFromFile(Path dumpPath, String displayName) {
        requirePostgres();
        acquireBusy("恢复");
        Path sqlFile = dumpPath;
        Path extracted = null;
        boolean poolClosed = false;
        try {
            String name = dumpPath.getFileName().toString().toLowerCase(Locale.ROOT);
            if (name.endsWith(".tar.gz") || name.endsWith(".tgz")) {
                try {
                    extracted = Files.createTempDirectory(backupDir, "restore-");
                } catch (IOException ex) {
                    throw new BusinessException(ErrorCode.INTERNAL_ERROR, "无法创建恢复临时目录");
                }
                extractTarGz(dumpPath, extracted);
                Path inner = extracted.resolve("database.sql");
                if (!Files.isRegularFile(inner)) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR, "压缩包中缺少 database.sql");
                }
                sqlFile = inner;
            }
            try {
                Path safety = backupDir.resolve("asset-management-pre-restore-" + FILE_TS.format(Instant.now()) + ".sql.gz");
                dumpDatabase(safety);
            } catch (RuntimeException ex) {
                log.warn("Pre-restore safety dump failed: {}", ex.getMessage());
            }
            restartWorker();
            sleepQuietly(1500);
            closePool();
            poolClosed = true;
            recreateDatabase();
            restorePlainSql(sqlFile);
            log.warn("Database restored from {}; API will exit so the process can reload", displayName);
            scheduleExit(2000);
            return Map.of(
                    "accepted", true,
                    "fileName", displayName,
                    "message", "数据库已恢复，API 即将重启，请稍后刷新重新登录"
            );
        } catch (RuntimeException ex) {
            log.error("Database restore failed from {}", displayName, ex);
            if (poolClosed) {
                scheduleExit(1500);
            }
            throw ex;
        } finally {
            if (extracted != null) {
                deleteRecursively(extracted);
            }
            busy.set(false);
        }
    }

    private void recreateDatabase() {
        String db = jdbcEndpoint.database();
        runPgTool(
                "psql",
                List.of(
                        "-h", jdbcEndpoint.host(),
                        "-p", String.valueOf(jdbcEndpoint.port()),
                        "-U", jdbcEndpoint.username(),
                        "-d", "postgres",
                        "-v", "ON_ERROR_STOP=1",
                        "-c",
                        "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='"
                                + db.replace("'", "''")
                                + "' AND pid <> pg_backend_pid();"
                ),
                Duration.ofSeconds(30)
        );
        runPgTool(
                "psql",
                List.of(
                        "-h", jdbcEndpoint.host(),
                        "-p", String.valueOf(jdbcEndpoint.port()),
                        "-U", jdbcEndpoint.username(),
                        "-d", "postgres",
                        "-v", "ON_ERROR_STOP=1",
                        "-c", "DROP DATABASE IF EXISTS \"" + db.replace("\"", "") + "\";"
                ),
                Duration.ofSeconds(30)
        );
        runPgTool(
                "psql",
                List.of(
                        "-h", jdbcEndpoint.host(),
                        "-p", String.valueOf(jdbcEndpoint.port()),
                        "-U", jdbcEndpoint.username(),
                        "-d", "postgres",
                        "-v", "ON_ERROR_STOP=1",
                        "-c", "CREATE DATABASE \"" + db.replace("\"", "") + "\";"
                ),
                Duration.ofSeconds(30)
        );
    }

    private void closePool() {
        if (dataSource instanceof HikariDataSource hikari && !hikari.isClosed()) {
            hikari.close();
        }
    }

    private void restartApi() {
        if (dockerClient.available()) {
            try {
                dockerClient.restartService("api");
                return;
            } catch (RuntimeException ex) {
                log.warn("Docker restart api failed, falling back to process exit: {}", ex.getMessage());
            }
        }
        scheduleExit(1200);
    }

    private void restartWorker() {
        if (dockerClient.available()) {
            try {
                dockerClient.restartService("worker");
                return;
            } catch (RuntimeException ex) {
                log.warn("Docker restart worker failed, falling back to Redis signal: {}", ex.getMessage());
            }
        }
        try {
            redisTemplate.opsForValue().set(WORKER_RESTART_KEY, Instant.now().toString(), Duration.ofSeconds(120));
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "无法通知 Worker 重启：" + ex.getMessage());
        }
    }

    private String restartMode(String service) {
        return dockerClient.available() ? "docker" : ("api".equals(service) ? "process-exit" : "redis-signal");
    }

    private void scheduleExit(long delayMs) {
        Thread thread = new Thread(() -> {
            sleepQuietly(delayMs);
            System.exit(0);
        }, "platform-ops-restart");
        thread.setDaemon(false);
        thread.start();
    }

    private Map<String, Object> toolStatus() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("pgDump", commandExists("pg_dump"));
        map.put("psql", commandExists("psql"));
        map.put("curl", commandExists("curl"));
        return map;
    }

    private Map<String, Object> probeWorker() {
        String url = properties.workerHealthUrl();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            boolean up = response.statusCode() >= 200 && response.statusCode() < 300
                    && response.body() != null
                    && response.body().toUpperCase(Locale.ROOT).contains("UP");
            return probe("worker", "MQTT Worker", up, "HTTP " + response.statusCode());
        } catch (Exception ex) {
            return probe("worker", "MQTT Worker", false, ex.getMessage());
        }
    }

    private Map<String, Object> probePostgres() {
        if (jdbcEndpoint == null) {
            return probe("postgres", "PostgreSQL", false, "当前不是 PostgreSQL");
        }
        try {
            Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return probe("postgres", "PostgreSQL", Objects.equals(one, 1), jdbcEndpoint.host() + ":" + jdbcEndpoint.port());
        } catch (Exception ex) {
            return probe("postgres", "PostgreSQL", false, ex.getMessage());
        }
    }

    private Map<String, Object> probeRedis() {
        try {
            var factory = redisTemplate.getConnectionFactory();
            if (factory == null) {
                return probe("redis", "Redis", false, "no connection factory");
            }
            try (var connection = factory.getConnection()) {
                String pong = connection.ping();
                boolean up = pong != null && !pong.isBlank();
                return probe("redis", "Redis", up, pong);
            }
        } catch (Exception ex) {
            return probe("redis", "Redis", false, ex.getMessage());
        }
    }

    private Map<String, Object> probeMqtt() {
        int port = properties.mqttPort();
        String lastError = "unreachable";
        for (String host : mqttProbeHosts()) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 2000);
                return probe("mosquitto", "MQTT Broker", true, host + ":" + port);
            } catch (Exception ex) {
                lastError = host + ": " + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
            }
        }
        return probe("mosquitto", "MQTT Broker", false, lastError);
    }

    private List<String> mqttProbeHosts() {
        LinkedHashSet<String> hosts = new LinkedHashSet<>();
        for (String part : properties.mqttHost().split(",")) {
            String host = part.trim();
            if (!host.isEmpty()) {
                hosts.add(host);
            }
        }
        hosts.add("mosquitto");
        hosts.add("host.docker.internal");
        hosts.add("172.17.0.1");
        if (dockerClient.available()) {
            String ip = dockerClient.firstIpv4("mosquitto");
            if (ip != null && !ip.isBlank()) {
                hosts.add(ip);
            }
        }
        return new ArrayList<>(hosts);
    }

    private static Map<String, Object> probe(String id, String name, boolean up, String detail) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("name", name);
        map.put("up", up);
        map.put("detail", detail == null ? "" : detail);
        return map;
    }

    private static String displayName(String service) {
        return switch (service) {
            case "api" -> "API";
            case "worker" -> "MQTT Worker";
            case "web" -> "Web";
            case "postgres" -> "PostgreSQL";
            case "redis" -> "Redis";
            case "mosquitto" -> "MQTT Broker";
            default -> service;
        };
    }

    private CleanupPlan parsePlan(Map<String, Object> body) {
        List<String> requested = new ArrayList<>();
        Object rawTargets = body == null ? null : body.get("targets");
        if (rawTargets instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    requested.add(String.valueOf(item));
                }
            }
        }
        if (requested.isEmpty()) {
            for (PlatformCleanupTarget target : PlatformCleanupTarget.all()) {
                requested.add(target.id());
            }
        }
        Map<String, Integer> daysByTarget = new LinkedHashMap<>();
        Object rawDays = body == null ? null : body.get("olderThanDays");
        if (rawDays instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                daysByTarget.put(String.valueOf(entry.getKey()), toDays(entry.getValue()));
            }
        }
        Integer globalDays = body != null && body.get("days") != null ? toDays(body.get("days")) : null;
        List<CleanupPlan.Item> items = new ArrayList<>();
        for (String id : requested) {
            PlatformCleanupTarget target = PlatformCleanupTarget.fromId(id);
            if (target == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "未知清理项：" + id);
            }
            int days = daysByTarget.getOrDefault(target.id(), globalDays != null ? globalDays : target.defaultDays());
            if (days < 0) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "保留天数不能为负数");
            }
            Instant cutoff = Instant.now().minus(Duration.ofDays(days));
            items.add(new CleanupPlan.Item(target, days, cutoff));
        }
        return new CleanupPlan(items);
    }

    private static int toDays(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "保留天数无效");
        }
    }

    private static List<CleanupPlan.Item> ordered(List<CleanupPlan.Item> items) {
        List<PlatformCleanupTarget> order = List.of(
                PlatformCleanupTarget.GATT_OPS,
                PlatformCleanupTarget.EINK_OPS,
                PlatformCleanupTarget.EINK_JOBS,
                PlatformCleanupTarget.OUTBOUND_COMMANDS,
                PlatformCleanupTarget.SCAN_EVENTS,
                PlatformCleanupTarget.MQTT_INBOX,
                PlatformCleanupTarget.PRESENCE_EVENTS,
                PlatformCleanupTarget.NOTIFICATION_DELIVERIES,
                PlatformCleanupTarget.CLOSED_ALERTS,
                PlatformCleanupTarget.AUDIT_LOGS
        );
        List<CleanupPlan.Item> out = new ArrayList<>();
        for (PlatformCleanupTarget target : order) {
            for (CleanupPlan.Item item : items) {
                if (item.target() == target) {
                    out.add(item);
                }
            }
        }
        return out;
    }

    private CountEstimate estimateCount(CleanupPlan.Item item) {
        String sql = "SELECT COUNT(*) FROM (SELECT 1 FROM " + item.target().table()
                + " WHERE " + item.target().whereSql()
                + " LIMIT " + CLEANUP_PREVIEW_CAP + ") capped";
        try {
            if (jdbcEndpoint != null) {
                try (Connection connection = openCleanupConnection()) {
                    try (PreparedStatement statement = connection.prepareStatement(sql)) {
                        statement.setTimestamp(1, Timestamp.from(item.cutoff()));
                        try (ResultSet resultSet = statement.executeQuery()) {
                            long count = resultSet.next() ? resultSet.getLong(1) : 0L;
                            return new CountEstimate(Math.min(count, CLEANUP_PREVIEW_CAP - 1), count >= CLEANUP_PREVIEW_CAP);
                        }
                    }
                }
            }
            Long count = jdbcTemplate.queryForObject(sql, Long.class, Timestamp.from(item.cutoff()));
            long n = count == null ? 0 : count;
            return new CountEstimate(Math.min(n, CLEANUP_PREVIEW_CAP - 1), n >= CLEANUP_PREVIEW_CAP);
        } catch (SQLException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, cleanupFailureMessage(ex));
        }
    }

    private long deleteInBatches(CleanupPlan.Item item) {
        String sql = "WITH doomed AS (SELECT id FROM " + item.target().table()
                + " WHERE " + item.target().whereSql()
                + " LIMIT " + CLEANUP_BATCH_SIZE + ") DELETE FROM " + item.target().table()
                + " WHERE id IN (SELECT id FROM doomed)";
        try {
            if (jdbcEndpoint != null) {
                return deleteInBatchesOnCleanupConnection(item, sql);
            }
            long total = 0;
            while (true) {
                int deleted = jdbcTemplate.update(sql, Timestamp.from(item.cutoff()));
                total += deleted;
                if (deleted < CLEANUP_BATCH_SIZE) {
                    break;
                }
            }
            return total;
        } catch (SQLException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, cleanupFailureMessage(ex));
        }
    }

    private long deleteInBatchesOnCleanupConnection(CleanupPlan.Item item, String sql) throws SQLException {
        long total = 0;
        try (Connection connection = openCleanupConnection()) {
            while (true) {
                connection.setAutoCommit(false);
                int deleted;
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setTimestamp(1, Timestamp.from(item.cutoff()));
                    deleted = statement.executeUpdate();
                }
                connection.commit();
                total += deleted;
                if (deleted < CLEANUP_BATCH_SIZE) {
                    break;
                }
            }
        }
        return total;
    }

    private Connection openCleanupConnection() throws SQLException {
        String url = "jdbc:postgresql://" + jdbcEndpoint.host() + ":" + jdbcEndpoint.port()
                + "/" + jdbcEndpoint.database()
                + "?connectTimeout=10&socketTimeout=" + CLEANUP_SOCKET_TIMEOUT_SECONDS;
        Connection connection = DriverManager.getConnection(url, jdbcEndpoint.username(), jdbcEndpoint.password());
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    SELECT
                        set_config('app.current_user_id', '', false),
                        set_config('app.current_tenant_id', '', false),
                        set_config('app.current_project_id', '', false),
                        set_config('app.is_platform_admin', 'true', false)
                    """);
            statement.execute("SET statement_timeout = '120s'");
        }
        return connection;
    }

    private void vacuumTables(List<String> tables) {
        if (tables.isEmpty() || jdbcEndpoint == null) {
            return;
        }
        try (Connection connection = openCleanupConnection()) {
            connection.setAutoCommit(true);
            try (Statement statement = connection.createStatement()) {
                for (String table : tables) {
                    statement.execute("VACUUM " + table);
                }
            }
        } catch (SQLException ex) {
            log.warn("VACUUM after cleanup failed: {}", ex.getMessage());
        }
    }

    private static String cleanupFailureMessage(Throwable error) {
        String raw = error.getMessage() == null ? "" : error.getMessage().toLowerCase(Locale.ROOT);
        if (raw.contains("timeout") || raw.contains("timed out") || raw.contains("i/o error")) {
            return "清理超时。已改为分批后台执行，请稍后刷新进度；若仍失败请减少勾选或缩短保留天数后再试";
        }
        return "清理失败：" + (error.getMessage() == null || error.getMessage().isBlank()
                ? "请稍后重试"
                : error.getMessage());
    }

    @PreDestroy
    void shutdownCleanupExecutor() {
        cleanupExecutor.shutdownNow();
    }

    private Map<String, Object> backupRow(Path path) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("fileName", path.getFileName().toString());
        try {
            map.put("sizeBytes", Files.size(path));
            map.put("modifiedAt", Files.getLastModifiedTime(path).toInstant().toString());
        } catch (IOException ex) {
            map.put("sizeBytes", 0);
            map.put("modifiedAt", null);
        }
        return map;
    }

    private Path resolveBackup(String fileName) {
        if (fileName == null || !SAFE_BACKUP_NAME.matcher(fileName).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "备份文件名无效");
        }
        Path path = backupDir.resolve(fileName).normalize();
        if (!path.startsWith(backupDir) || !Files.isRegularFile(path)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "备份文件不存在");
        }
        return path;
    }

    private void pruneOldBackups() {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(backupDir, "asset-management-*")) {
            stream.forEach(files::add);
        } catch (IOException ignored) {
            return;
        }
        files.removeIf(path -> !Files.isRegularFile(path));
        files.sort(Comparator.comparing(Path::getFileName).reversed());
        for (int i = properties.maxBackups(); i < files.size(); i++) {
            try {
                Files.deleteIfExists(files.get(i));
            } catch (IOException ignored) {
                // best-effort
            }
        }
    }

    private void dumpDatabase(Path target) {
        List<String> args = new ArrayList<>();
        args.add("-h");
        args.add(jdbcEndpoint.host());
        args.add("-p");
        args.add(String.valueOf(jdbcEndpoint.port()));
        args.add("-U");
        args.add(jdbcEndpoint.username());
        args.add("-d");
        args.add(jdbcEndpoint.database());
        args.add("--no-owner");
        args.add("--no-acl");
        args.add("--compress=gzip:6");
        for (String table : BACKUP_EXCLUDE_TABLE_DATA) {
            args.add("--exclude-table-data=" + table);
        }
        args.add("-f");
        args.add(target.toString());
        runPgTool("pg_dump", args, DUMP_TIMEOUT);
    }

    private void restorePlainSql(Path sqlFile) {
        List<String> args = new ArrayList<>();
        args.add("-h");
        args.add(jdbcEndpoint.host());
        args.add("-p");
        args.add(String.valueOf(jdbcEndpoint.port()));
        args.add("-U");
        args.add(jdbcEndpoint.username());
        args.add("-d");
        args.add(jdbcEndpoint.database());
        args.add("-v");
        args.add("ON_ERROR_STOP=1");
        if (!isGzipSql(sqlFile)) {
            args.add("-f");
            args.add(sqlFile.toString());
            runPgTool("psql", args, RESTORE_TIMEOUT);
            return;
        }
        args.add("-f");
        args.add("-");
        runPsqlWithGzipStdin(args, sqlFile, RESTORE_TIMEOUT);
    }

    private static boolean isGzipSql(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".sql.gz") || (name.endsWith(".gz") && !name.endsWith(".tar.gz") && !name.endsWith(".tgz"));
    }

    private void runPsqlWithGzipStdin(List<String> args, Path gzipFile, Duration timeout) {
        if (!commandExists("psql")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "未找到 psql，请在 API 镜像中安装 postgresql-client");
        }
        List<String> command = new ArrayList<>();
        command.add("psql");
        command.addAll(args);
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.environment().put("PGPASSWORD", jdbcEndpoint.password());
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            try (InputStream fileIn = Files.newInputStream(gzipFile);
                 InputStream decoded = new GZIPInputStream(fileIn, 65536);
                 OutputStream stdin = process.getOutputStream()) {
                decoded.transferTo(stdin);
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "psql 执行超时");
            }
            if (process.exitValue() != 0) {
                String snippet = output.strip();
                if (snippet.length() > 800) {
                    snippet = snippet.substring(0, 800);
                }
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "psql 失败：" + snippet);
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "psql 被中断");
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "无法执行 psql：" + ex.getMessage());
        }
    }

    private void runPgTool(String executable, List<String> args, Duration timeout) {
        if (!commandExists(executable)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "未找到 " + executable + "，请在 API 镜像中安装 postgresql-client");
        }
        List<String> command = new ArrayList<>();
        command.add(executable);
        command.addAll(args);
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.environment().put("PGPASSWORD", jdbcEndpoint.password());
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, executable + " 执行超时");
            }
            if (process.exitValue() != 0) {
                String snippet = output.strip();
                if (snippet.length() > 800) {
                    snippet = snippet.substring(0, 800);
                }
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, executable + " 失败：" + snippet);
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, executable + " 被中断");
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "无法执行 " + executable + "：" + ex.getMessage());
        }
    }

    private static boolean commandExists(String executable) {
        ProcessBuilder builder = new ProcessBuilder("sh", "-c", "command -v " + executable);
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            boolean finished = process.waitFor(3, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void extractTarGz(Path archive, Path dest) {
        ProcessBuilder builder = new ProcessBuilder("tar", "-xzf", archive.toString(), "-C", dest.toString());
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(2, TimeUnit.MINUTES) || process.exitValue() != 0) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "解压备份失败：" + output.strip());
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "解压备份失败");
        }
    }

    private static void deleteRecursively(Path root) {
        try {
            if (!Files.exists(root)) {
                return;
            }
            Files.walk(root)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // best-effort
                        }
                    });
        } catch (IOException ignored) {
            // best-effort
        }
    }

    private static String sanitizeUploadName(String original) {
        String base = Path.of(original).getFileName().toString().replace(' ', '_');
        if (!SAFE_BACKUP_NAME.matcher(base).matches()) {
            String ext = base.endsWith(".sql.gz") ? ".sql.gz"
                    : (base.endsWith(".tar.gz") ? ".tar.gz"
                    : (base.contains(".") ? base.substring(base.lastIndexOf('.')) : ".sql"));
            base = "asset-management-upload-" + FILE_TS.format(Instant.now()) + ext;
        }
        return base;
    }

    private void acquireBusy(String action) {
        if (!busy.compareAndSet(false, true)) {
            throw new BusinessException(ErrorCode.CONFLICT, "正在执行其他运维操作，请稍后再" + action);
        }
    }

    private void requirePostgres() {
        if (jdbcEndpoint == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前数据源不是 PostgreSQL，无法备份/恢复");
        }
    }

    private static void requireConfirm(String actual, String expected, String message) {
        if (actual == null || !expected.equals(actual.trim())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, message);
        }
    }

    private void require(String permission) {
        CurrentUserPrincipal principal = currentUserProvider.requireCurrentUser();
        if (!principal.hasPlatformPermission(permission)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Platform permission is required");
        }
    }

    private PlatformOpsSettings loadSettings() {
        return settingsRepository.findFirstByOrderByCreatedAtAsc()
                .orElseGet(() -> settingsRepository.save(PlatformOpsSettings.defaults()));
    }

    private Map<String, Object> autoCleanupMap(PlatformOpsSettings settings) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("enabled", settings.isAutoCleanupEnabled());
        map.put("intervalDays", settings.getAutoCleanupDays());
        map.put("days", settings.getAutoCleanupDays());
        map.put("rules", readRules(settings.getCleanupRulesJson()));
        map.put("lastRunAt", settings.getLastAutoCleanupAt() == null ? null : settings.getLastAutoCleanupAt().toString());
        map.put("lastDeleted", settings.getLastAutoCleanupTotal());
        return map;
    }

    private CleanupPlan planFromRules(List<Map<String, Object>> rules) {
        List<String> targets = new ArrayList<>();
        Map<String, Integer> olderThanDays = new LinkedHashMap<>();
        for (Map<String, Object> rule : rules) {
            String id = String.valueOf(rule.get("id"));
            targets.add(id);
            olderThanDays.put(id, toDays(rule.get("days")));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("targets", targets);
        body.put("olderThanDays", olderThanDays);
        return parsePlan(body);
    }

    private List<Map<String, Object>> normalizeRules(Object raw) {
        if (!(raw instanceof List<?> list)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "清理规则格式无效");
        }
        List<Map<String, Object>> rules = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map) || map.get("id") == null) {
                continue;
            }
            String id = String.valueOf(map.get("id")).trim();
            if (id.isEmpty() || PlatformCleanupTarget.fromId(id) == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "未知清理项：" + id);
            }
            int days = toDays(map.get("days") == null ? 7 : map.get("days"));
            if (days < 0 || days > 3650) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "保留天数需在 0–3650 之间");
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", id);
            row.put("days", days);
            rules.add(row);
        }
        return rules;
    }

    private List<Map<String, Object>> readRules(String json) {
        if (json == null || json.isBlank() || "[]".equals(json.trim())) {
            return List.of();
        }
        try {
            List<?> list = objectMapper.readValue(json, List.class);
            if (list == null) {
                return List.of();
            }
            List<Map<String, Object>> rules = new ArrayList<>();
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map) || map.get("id") == null) {
                    continue;
                }
                String id = String.valueOf(map.get("id")).trim();
                if (id.isEmpty() || PlatformCleanupTarget.fromId(id) == null) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", id);
                row.put("days", toDays(map.get("days") == null ? 7 : map.get("days")));
                rules.add(row);
            }
            return rules;
        } catch (RuntimeException ex) {
            log.warn("Unable to parse saved cleanup rules: {}", ex.getMessage());
            return List.of();
        }
    }

    private String writeRules(List<Map<String, Object>> rules) {
        try {
            return objectMapper.writeValueAsString(rules == null ? List.of() : rules);
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "清理规则无法保存");
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private record CleanupPlan(List<Item> items) {
        private record Item(PlatformCleanupTarget target, int days, Instant cutoff) {
        }
    }

    private record CountEstimate(long count, boolean approximate) {
    }
}
