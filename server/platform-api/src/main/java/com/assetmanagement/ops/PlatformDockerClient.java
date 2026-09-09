package com.assetmanagement.ops;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
class PlatformDockerClient {

    private static final Logger log = LoggerFactory.getLogger(PlatformDockerClient.class);

    static final List<String> SERVICE_ORDER = List.of(
            "api", "worker", "web", "postgres", "redis", "mosquitto"
    );

    private final PlatformOpsProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    PlatformDockerClient(PlatformOpsProperties properties) {
        this.properties = properties;
    }

    private volatile Boolean reachable;

    boolean available() {
        if (!properties.dockerEnabled()) {
            return false;
        }
        if (!Files.exists(Path.of(properties.dockerSocket()))) {
            return false;
        }
        if (reachable != null) {
            return reachable;
        }
        try {
            docker("GET", "/version", Duration.ofSeconds(3));
            reachable = true;
        } catch (RuntimeException ex) {
            log.warn("Docker socket present but not usable: {}", ex.getMessage());
            reachable = false;
        }
        return reachable;
    }

    Map<String, Object> inspect() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("enabled", properties.dockerEnabled());
        map.put("socketPresent", Files.exists(Path.of(properties.dockerSocket())));
        map.put("available", available());
        if (!available()) {
            map.put("containers", List.of());
            return map;
        }
        try {
            map.put("containers", listContainers());
        } catch (Exception ex) {
            log.warn("Docker inspect failed: {}", ex.getMessage());
            map.put("available", false);
            map.put("error", ex.getMessage());
            map.put("containers", List.of());
        }
        return map;
    }

    List<Map<String, Object>> listContainers() {
        JsonNode root = dockerJson("GET", "/containers/json", Duration.ofSeconds(8));
        List<Map<String, Object>> out = new ArrayList<>();
        if (root == null || !root.isArray()) {
            return out;
        }
        for (JsonNode node : root) {
            String service = composeService(node);
            if (service == null || !SERVICE_ORDER.contains(service)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", text(node, "Id"));
            row.put("name", firstName(node));
            row.put("service", service);
            row.put("state", text(node, "State"));
            row.put("status", text(node, "Status"));
            out.add(row);
        }
        out.sort((a, b) -> Integer.compare(
                SERVICE_ORDER.indexOf(String.valueOf(a.get("service"))),
                SERVICE_ORDER.indexOf(String.valueOf(b.get("service")))
        ));
        return out;
    }

    String firstIpv4(String serviceOrName) {
        if (!available() || serviceOrName == null || serviceOrName.isBlank()) {
            return null;
        }
        String wanted = serviceOrName.trim().toLowerCase(Locale.ROOT);
        JsonNode root = dockerJson("GET", "/containers/json", Duration.ofSeconds(8));
        if (root == null || !root.isArray()) {
            return null;
        }
        for (JsonNode node : root) {
            String service = composeService(node);
            String name = firstName(node).toLowerCase(Locale.ROOT);
            boolean match = wanted.equals(service) || name.equals(wanted) || name.contains(wanted);
            if (!match) {
                continue;
            }
            JsonNode networks = node.path("NetworkSettings").path("Networks");
            if (!networks.isObject()) {
                continue;
            }
            var fields = networks.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                String ip = entry.getValue().path("IPAddress").asText("");
                if (!ip.isBlank()) {
                    return ip;
                }
            }
        }
        return null;
    }

    void restartService(String service) {
        if (!available()) {
            throw new IllegalStateException("Docker 不可用：未挂载 docker.sock 或未开启 OPS_DOCKER_ENABLED");
        }
        String id = requireContainerId(service);
        docker("POST", "/containers/" + id + "/restart?t=20", Duration.ofSeconds(60));
    }

    void upsertMosquittoUser(String username, String password) {
        if (!available()) {
            throw new IllegalStateException("Docker 不可用：未挂载 docker.sock 或未开启 OPS_DOCKER_ENABLED");
        }
        String id = requireContainerId("mosquitto");
        var create = objectMapper.createObjectNode();
        create.put("AttachStdout", true);
        create.put("AttachStderr", true);
        create.put("User", "0");
        var cmd = create.putArray("Cmd");
        cmd.add("mosquitto_passwd");
        cmd.add("-b");
        cmd.add("/mosquitto/config/password_file");
        cmd.add(username);
        cmd.add(password);
        JsonNode created = dockerJson(
                "POST",
                "/containers/" + id + "/exec",
                Duration.ofSeconds(15),
                create.toString()
        );
        String execId = created.path("Id").asText("");
        if (execId.isBlank()) {
            throw new IllegalStateException("无法在 Mosquitto 容器中创建 exec");
        }
        docker("POST", "/exec/" + execId + "/start", Duration.ofSeconds(15), "{\"Detach\":false,\"Tty\":false}");
        JsonNode inspect = dockerJson("GET", "/exec/" + execId + "/json", Duration.ofSeconds(8));
        int exit = inspect.path("ExitCode").asInt(-1);
        if (exit != 0) {
            throw new IllegalStateException("mosquitto_passwd 退出码 " + exit);
        }
    }

    void reloadMosquitto() {
        if (!available()) {
            return;
        }
        String id = findContainerId("mosquitto");
        if (id == null || id.isBlank()) {
            return;
        }
        docker("POST", "/containers/" + id + "/kill?signal=HUP", Duration.ofSeconds(10));
    }

    private String requireContainerId(String service) {
        String id = findContainerId(service);
        if (id == null || id.isBlank()) {
            throw new IllegalStateException("未找到 Docker 服务：" + service);
        }
        return id;
    }

    private String findContainerId(String service) {
        String wanted = service == null ? "" : service.trim().toLowerCase(Locale.ROOT);
        for (Map<String, Object> row : listContainers()) {
            if (wanted.equals(String.valueOf(row.get("service")))) {
                return String.valueOf(row.get("id"));
            }
        }
        return null;
    }

    private JsonNode dockerJson(String method, String path, Duration timeout) {
        return dockerJson(method, path, timeout, null);
    }

    private JsonNode dockerJson(String method, String path, Duration timeout, String jsonBody) {
        String body = docker(method, path, timeout, jsonBody);
        if (body == null || body.isBlank()) {
            return objectMapper.createArrayNode();
        }
        try {
            return objectMapper.readTree(body);
        } catch (IOException ex) {
            throw new IllegalStateException("无法解析 Docker 响应", ex);
        }
    }

    private String docker(String method, String path, Duration timeout) {
        return docker(method, path, timeout, null);
    }

    private String docker(String method, String path, Duration timeout, String jsonBody) {
        String url = "http://127.0.0.1/" + properties.dockerApiVersion() + path;
        List<String> command = new ArrayList<>();
        command.add("curl");
        command.add("-sS");
        command.add("--unix-socket");
        command.add(properties.dockerSocket());
        command.add("--max-time");
        command.add(String.valueOf(Math.max(2, timeout.toSeconds())));
        command.add("-X");
        command.add(method);
        command.add("-o");
        command.add("-");
        command.add("-w");
        command.add("\n%{http_code}");
        if (jsonBody != null) {
            command.add("-H");
            command.add("Content-Type: application/json");
            command.add("--data-binary");
            command.add(jsonBody);
        }
        command.add(url);
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(timeout.toMillis() + 1000, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("Docker 请求超时");
            }
            int split = output.lastIndexOf('\n');
            String body = split < 0 ? "" : output.substring(0, split);
            String codeText = (split < 0 ? output : output.substring(split + 1)).trim();
            int status;
            try {
                status = Integer.parseInt(codeText);
            } catch (NumberFormatException ex) {
                throw new IllegalStateException("Docker 调用失败：" + output.trim());
            }
            if (status >= 200 && status < 300) {
                return body;
            }
            throw new IllegalStateException("Docker HTTP " + status + (body.isBlank() ? "" : ": " + body.trim()));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Docker 调用被中断", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("无法调用 Docker：" + ex.getMessage(), ex);
        }
    }

    private static String composeService(JsonNode node) {
        JsonNode labels = node.path("Labels");
        String service = labels.path("com.docker.compose.service").asText("");
        if (!service.isBlank()) {
            return service.trim().toLowerCase(Locale.ROOT);
        }
        String name = firstName(node).toLowerCase(Locale.ROOT);
        if (name.contains("mosquitto") || name.equals("mqtt") || name.contains("mqtt-broker")) {
            return "mosquitto";
        }
        return null;
    }

    private static String firstName(JsonNode node) {
        JsonNode names = node.path("Names");
        if (names.isArray() && names.size() > 0) {
            String raw = names.get(0).asText("");
            return raw.startsWith("/") ? raw.substring(1) : raw;
        }
        return text(node, "Id");
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("");
    }
}
