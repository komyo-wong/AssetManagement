package com.assetmanagement.geotag.application;

import com.assetmanagement.geotag.GeotagCrypto;
import com.assetmanagement.shared.exception.BusinessException;
import com.assetmanagement.shared.exception.ErrorCode;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Signed calls to GeoTag cloud {@code /device/getList} and {@code /device/getHistory}. */
public class GeotagApiClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiBaseUrl;
    private final String businessNo;
    private final String ourPrivateKey;
    private final String platformPublicKey;

    public GeotagApiClient(
            ObjectMapper objectMapper,
            String apiBaseUrl,
            String businessNo,
            String ourPrivateKey,
            String platformPublicKey
    ) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        this.apiBaseUrl = trimSlash(apiBaseUrl);
        this.businessNo = businessNo;
        this.ourPrivateKey = ourPrivateKey;
        this.platformPublicKey = platformPublicKey;
    }

    public List<CloudDevice> listAllDevices() {
        List<CloudDevice> devices = new ArrayList<>();
        int page = 1;
        int pageSize = 50;
        while (page <= 40) {
            JsonNode data = post("/device/getList", listRequest(page, pageSize));
            JsonNode list = data.path("list");
            if (!list.isArray() || list.isEmpty()) {
                break;
            }
            for (JsonNode item : list) {
                String sn = text(item, "sn");
                if (sn == null || sn.isBlank()) {
                    continue;
                }
                devices.add(new CloudDevice(
                        sn.trim(),
                        text(item, "mac"),
                        text(item, "uuid"),
                        item.path("status").isNumber() ? item.path("status").intValue() : null
                ));
            }
            if (list.size() < pageSize) {
                break;
            }
            page++;
        }
        return devices;
    }

    public List<JsonNode> historyPoints(String sn, Instant from, Instant to) {
        return historyPoints(sn, from, to, 40, null).points();
    }

    public HistoryBatch historyPoints(String sn, Instant from, Instant to, int maxPages, Runnable beforeEach) {
        List<JsonNode> points = new ArrayList<>();
        if (sn == null || sn.isBlank() || from == null || to == null || maxPages <= 0) {
            return new HistoryBatch(points, false);
        }
        long start = from.toEpochMilli();
        long end = to.toEpochMilli();
        if (end < start) {
            long swap = start;
            start = end;
            end = swap;
        }
        int page = 1;
        int pageSize = 100;
        int limit = Math.min(maxPages, 40);
        boolean more = false;
        while (page <= limit) {
            if (beforeEach != null) {
                beforeEach.run();
            }
            ObjectNode request = objectMapper.createObjectNode();
            request.put("sn", sn.trim());
            request.put("startTime", start);
            request.put("endTime", end);
            request.put("beginTime", start);
            request.put("page", page);
            request.put("pageSize", pageSize);
            JsonNode data = post("/device/getHistory", request);
            JsonNode list = historyList(data);
            if (!list.isArray() || list.isEmpty()) {
                break;
            }
            for (JsonNode item : list) {
                if (item == null || item.isNull()) {
                    continue;
                }
                if (item.isObject() && !item.has("sn") && !item.has("deviceSn")) {
                    ((ObjectNode) item).put("sn", sn.trim());
                }
                points.add(item);
            }
            if (list.size() < pageSize) {
                break;
            }
            if (page == limit) {
                more = true;
                break;
            }
            page++;
        }
        return new HistoryBatch(points, more);
    }

    private JsonNode historyList(JsonNode data) {
        if (data == null || data.isNull()) {
            return objectMapper.createArrayNode();
        }
        if (data.isArray()) {
            return data;
        }
        for (String field : List.of("list", "records", "items", "history", "data")) {
            JsonNode node = data.path(field);
            if (node.isArray()) {
                return node;
            }
        }
        return objectMapper.createArrayNode();
    }

    private ObjectNode listRequest(int page, int pageSize) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("page", page);
        node.put("pageSize", pageSize);
        return node;
    }

    private JsonNode post(String path, JsonNode payload) {
        try {
            String plaintext = objectMapper.writeValueAsString(payload);
            String data = GeotagCrypto.encryptEnvelope(plaintext, platformPublicKey);
            long timestamp = System.currentTimeMillis();
            String sign = GeotagCrypto.signPayload(data, timestamp, ourPrivateKey);
            ObjectNode body = objectMapper.createObjectNode();
            body.put("businessNo", businessNo);
            body.put("data", data);
            body.put("sign", sign);
            body.put("timestamp", timestamp);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiBaseUrl + path))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "GeoTag 接口返回 HTTP " + response.statusCode()
                );
            }
            JsonNode root = objectMapper.readTree(response.body() == null ? "{}" : response.body());
            int code = root.path("code").asInt(0);
            if (code != 200 && code != 0) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "GeoTag 接口失败：" + root.path("msg").asText("unknown")
                );
            }
            String responseData = root.path("data").asText("");
            long responseTimestamp = root.path("timestamp").asLong(0);
            String responseSign = root.path("sign").asText("");
            if (!responseData.isBlank()
                    && !GeotagCrypto.verifyPayload(responseData, responseTimestamp, responseSign, platformPublicKey)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "GeoTag 响应验签失败");
            }
            if (responseData.isBlank()) {
                return objectMapper.createObjectNode();
            }
            String decrypted = GeotagCrypto.decryptEnvelope(responseData, ourPrivateKey);
            return objectMapper.readTree(decrypted);
        } catch (BusinessException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "GeoTag 请求被中断");
        } catch (Exception exception) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "无法连接 GeoTag：" + exception.getMessage()
            );
        }
    }

    private static String trimSlash(String url) {
        if (url == null) {
            return "";
        }
        String value = url.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text.trim();
    }

    public record CloudDevice(String sn, String mac, String uuid, Integer status) {
    }

    public record HistoryBatch(List<JsonNode> points, boolean more) {
    }
}
