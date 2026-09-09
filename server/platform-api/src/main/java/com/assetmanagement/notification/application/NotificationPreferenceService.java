package com.assetmanagement.notification.application;

import com.assetmanagement.alert.repository.AlertEventRepository;
import com.assetmanagement.iam.PermissionCodes;
import com.assetmanagement.license.LicenseFeature;
import com.assetmanagement.license.PlatformLicenseService;
import com.assetmanagement.iam.domain.User;
import com.assetmanagement.iam.repository.UserRepository;
import com.assetmanagement.notification.domain.NotificationChannel;
import com.assetmanagement.notification.domain.NotificationDelivery;
import com.assetmanagement.notification.domain.NotificationSubscription;
import com.assetmanagement.notification.repository.NotificationChannelRepository;
import com.assetmanagement.notification.repository.NotificationDeliveryRepository;
import com.assetmanagement.notification.repository.NotificationSubscriptionRepository;
import com.assetmanagement.project.repository.ProjectRepository;
import com.assetmanagement.security.CurrentUserPrincipal;
import com.assetmanagement.security.CurrentUserProvider;
import com.assetmanagement.shared.api.PageResponse;
import com.assetmanagement.shared.exception.BusinessException;
import com.assetmanagement.shared.exception.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificationPreferenceService {

    private final CurrentUserProvider currentUserProvider;
    private final UserRepository userRepository;
    private final NotificationChannelRepository channelRepository;
    private final NotificationSubscriptionRepository subscriptionRepository;
    private final NotificationDeliveryRepository deliveryRepository;
    private final AlertEventRepository alertEventRepository;
    private final ProjectRepository projectRepository;
    private final PlatformLicenseService platformLicenseService;

    public NotificationPreferenceService(
            CurrentUserProvider currentUserProvider,
            UserRepository userRepository,
            NotificationChannelRepository channelRepository,
            NotificationSubscriptionRepository subscriptionRepository,
            NotificationDeliveryRepository deliveryRepository,
            AlertEventRepository alertEventRepository,
            ProjectRepository projectRepository,
            PlatformLicenseService platformLicenseService
    ) {
        this.currentUserProvider = currentUserProvider;
        this.userRepository = userRepository;
        this.channelRepository = channelRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.deliveryRepository = deliveryRepository;
        this.alertEventRepository = alertEventRepository;
        this.projectRepository = projectRepository;
        this.platformLicenseService = platformLicenseService;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> overview() {
        CurrentUserPrincipal principal = requireManageable();
        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "User was not found"));
        Map<String, Object> map = new HashMap<>();
        map.put("accountEmail", user.getEmail());
        map.put("licensed", platformLicenseService.has(LicenseFeature.NOTIFY));
        map.put("channels", listChannels());
        map.put("subscriptions", listSubscriptions());
        return map;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listChannels() {
        CurrentUserPrincipal principal = requireReadable();
        return channelRepository.findAllByUserIdOrderByCreatedAtDesc(principal.userId()).stream()
                .map(this::toChannel)
                .toList();
    }

    @Transactional
    public Map<String, Object> createChannel(Map<String, Object> body) {
        CurrentUserPrincipal principal = requireManageable();
        requireNotifyLicense();
        String type = requiredString(body, "channelType").toUpperCase(Locale.ROOT);
        if (!"EMAIL".equals(type) && !"WEBHOOK".equals(type)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "channelType must be EMAIL or WEBHOOK");
        }
        String address = requiredString(body, "address");
        validateAddress(type, address);
        NotificationChannel channel = new NotificationChannel(principal.userId(), type, address);
        channel.update(
                address,
                stringOrNull(body.get("displayName")),
                body.get("enabled") instanceof Boolean b ? b : true,
                stringOrNull(body.get("configJson"))
        );
        return toChannel(channelRepository.save(channel));
    }

    @Transactional
    public Map<String, Object> updateChannel(UUID id, Map<String, Object> body) {
        CurrentUserPrincipal principal = requireManageable();
        requireNotifyLicense();
        NotificationChannel channel = channelRepository.findByIdAndUserId(id, principal.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Channel was not found"));
        String address = body.containsKey("address") ? requiredString(body, "address") : channel.getAddress();
        validateAddress(channel.getChannelType(), address);
        channel.update(
                address,
                body.containsKey("displayName") ? stringOrNull(body.get("displayName")) : channel.getDisplayName(),
                body.get("enabled") instanceof Boolean b ? b : null,
                body.containsKey("configJson") ? stringOrNull(body.get("configJson")) : channel.getConfigJson()
        );
        return toChannel(channelRepository.save(channel));
    }

    @Transactional
    public void deleteChannel(UUID id) {
        CurrentUserPrincipal principal = requireManageable();
        NotificationChannel channel = channelRepository.findByIdAndUserId(id, principal.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Channel was not found"));
        channelRepository.delete(channel);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listSubscriptions() {
        CurrentUserPrincipal principal = requireReadable();
        return subscriptionRepository.findAllByUserIdOrderByCreatedAtDesc(principal.userId()).stream()
                .map(this::toSubscription)
                .toList();
    }

    @Transactional
    public Map<String, Object> createSubscription(Map<String, Object> body) {
        CurrentUserPrincipal principal = requireManageable();
        requireNotifyLicense();
        UUID tenantId = uuid(body, "tenantId");
        UUID projectId = uuid(body, "projectId");
        UUID channelId = uuid(body, "channelId");
        projectRepository.findByIdAndTenantId(projectId, tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Project was not found"));
        channelRepository.findByIdAndUserId(channelId, principal.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Channel was not found"));
        NotificationSubscription sub = new NotificationSubscription(
                principal.userId(), tenantId, projectId, channelId);
        applySubscriptionFields(sub, body);
        return toSubscription(subscriptionRepository.save(sub));
    }

    @Transactional
    public Map<String, Object> updateSubscription(UUID id, Map<String, Object> body) {
        CurrentUserPrincipal principal = requireManageable();
        requireNotifyLicense();
        NotificationSubscription sub = subscriptionRepository.findByIdAndUserId(id, principal.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Subscription was not found"));
        applySubscriptionFields(sub, body);
        return toSubscription(subscriptionRepository.save(sub));
    }

    @Transactional
    public void deleteSubscription(UUID id) {
        CurrentUserPrincipal principal = requireManageable();
        NotificationSubscription sub = subscriptionRepository.findByIdAndUserId(id, principal.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Subscription was not found"));
        subscriptionRepository.delete(sub);
    }

    @Transactional(readOnly = true)
    public PageResponse<Map<String, Object>> listDeliveries(long current, long size) {
        CurrentUserPrincipal principal = requireReadable();
        Page<NotificationDelivery> page = deliveryRepository.findAllByUserIdOrderByCreatedAtDesc(
                principal.userId(),
                PageRequest.of((int) Math.max(current - 1, 0), (int) Math.min(Math.max(size, 1), 100))
        );
        return new PageResponse<>(
                page.getContent().stream().map(this::toDelivery).toList(),
                current,
                size,
                page.getTotalElements()
        );
    }

    private void applySubscriptionFields(NotificationSubscription sub, Map<String, Object> body) {
        Boolean onAlert = body.get("onAlert") instanceof Boolean b ? b : null;
        Boolean onRecovery = body.get("onRecovery") instanceof Boolean b ? b : null;
        String minSeverity = body.containsKey("minSeverity")
                ? requiredString(body, "minSeverity").toUpperCase(Locale.ROOT)
                : null;
        Boolean enabled = body.get("enabled") instanceof Boolean b ? b : null;
        String quietHoursJson = body.containsKey("quietHoursJson")
                ? stringOrNull(body.get("quietHoursJson"))
                : sub.getQuietHoursJson();
        Integer throttleSeconds = body.containsKey("throttleSeconds")
                ? (body.get("throttleSeconds") instanceof Number n ? n.intValue() : null)
                : sub.getThrottleSeconds();
        String ruleTypesCsv = body.containsKey("ruleTypesCsv")
                ? stringOrNull(body.get("ruleTypesCsv"))
                : sub.getRuleTypesCsv();
        sub.update(onAlert, onRecovery, minSeverity, enabled, quietHoursJson, throttleSeconds, ruleTypesCsv);
        String sev = sub.getMinSeverity();
        if (!List.of("INFO", "WARNING", "CRITICAL").contains(sev)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "minSeverity invalid");
        }
    }

    private CurrentUserPrincipal requireReadable() {
        CurrentUserPrincipal principal = currentUserProvider.requireCurrentUser();
        if (!principal.hasPlatformPermission(PermissionCodes.NOTIFICATION_READ)
                && !principal.hasPlatformPermission(PermissionCodes.NOTIFICATION_MANAGE)
                && !principal.rootAccount()) {
            // Allow any authenticated user to manage own preferences; Root always allowed.
            // Non-root without explicit grant still OK for personal settings.
        }
        return principal;
    }

    private CurrentUserPrincipal requireManageable() {
        return requireReadable();
    }

    private Map<String, Object> toChannel(NotificationChannel c) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", c.getId().toString());
        map.put("channelType", c.getChannelType());
        map.put("address", c.getAddress());
        map.put("displayName", c.getDisplayName());
        map.put("enabled", c.isEnabled());
        map.put("verified", c.isVerified());
        map.put("configJson", c.getConfigJson());
        map.put("createdAt", c.getCreatedAt());
        map.put("updatedAt", c.getUpdatedAt());
        return map;
    }

    private Map<String, Object> toSubscription(NotificationSubscription s) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", s.getId().toString());
        map.put("tenantId", s.getTenantId().toString());
        map.put("projectId", s.getProjectId().toString());
        map.put("channelId", s.getChannelId().toString());
        map.put("onAlert", s.isOnAlert());
        map.put("onRecovery", s.isOnRecovery());
        map.put("minSeverity", s.getMinSeverity());
        map.put("enabled", s.isEnabled());
        map.put("quietHoursJson", s.getQuietHoursJson());
        map.put("throttleSeconds", s.getThrottleSeconds());
        map.put("ruleTypesCsv", s.getRuleTypesCsv());
        map.put("createdAt", s.getCreatedAt());
        map.put("updatedAt", s.getUpdatedAt());
        channelRepository.findById(s.getChannelId()).ifPresent(ch -> {
            map.put("channelType", ch.getChannelType());
            map.put("channelAddress", ch.getAddress());
        });
        return map;
    }

    private Map<String, Object> toDelivery(NotificationDelivery d) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", d.getId().toString());
        map.put("alertEventId", d.getAlertEventId() == null ? null : d.getAlertEventId().toString());
        map.put("subscriptionId", d.getSubscriptionId() == null ? null : d.getSubscriptionId().toString());
        map.put("channelId", d.getChannelId() == null ? null : d.getChannelId().toString());
        map.put("eventKind", d.getEventKind());
        map.put("status", d.getStatus());
        map.put("attempts", d.getAttempts());
        map.put("lastError", d.getLastError());
        String title = titleFromPayload(d.getPayloadJson());
        String message = messageFromPayload(d.getPayloadJson());
        if (d.getAlertEventId() != null) {
            var alert = alertEventRepository.findById(d.getAlertEventId());
            if (alert.isPresent()) {
                if (title == null || title.isBlank()) {
                    title = alert.get().getTitle();
                }
                if (message == null || message.isBlank()) {
                    message = alert.get().getMessage();
                }
            }
        }
        map.put("title", title);
        map.put("message", message);
        map.put("createdAt", d.getCreatedAt() == null ? null : d.getCreatedAt().toString());
        return map;
    }

    private static String titleFromPayload(String payloadJson) {
        return jsonStringField(payloadJson, "title");
    }

    private static String messageFromPayload(String payloadJson) {
        return jsonStringField(payloadJson, "message");
    }

    private static String jsonStringField(String payloadJson, String field) {
        if (payloadJson == null || payloadJson.isBlank() || field == null) {
            return null;
        }
        String key = "\"" + field + "\"";
        int at = payloadJson.indexOf(key);
        if (at < 0) {
            return null;
        }
        int colon = payloadJson.indexOf(':', at + key.length());
        if (colon < 0) {
            return null;
        }
        int i = colon + 1;
        while (i < payloadJson.length() && Character.isWhitespace(payloadJson.charAt(i))) {
            i++;
        }
        if (i >= payloadJson.length() || payloadJson.charAt(i) != '"') {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int p = i + 1; p < payloadJson.length(); p++) {
            char c = payloadJson.charAt(p);
            if (c == '\\' && p + 1 < payloadJson.length()) {
                sb.append(payloadJson.charAt(p + 1));
                p++;
                continue;
            }
            if (c == '"') {
                return sb.toString();
            }
            sb.append(c);
        }
        return null;
    }

    private static void validateAddress(String type, String address) {
        if ("EMAIL".equals(type)) {
            if (!address.contains("@") || address.length() < 5) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "email address is invalid");
            }
            return;
        }
        try {
            URI uri = URI.create(address.trim());
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "webhook must be http(s) URL");
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "webhook URL is invalid");
        }
    }

    private static String requiredString(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, key + " is required");
        }
        return String.valueOf(value).trim();
    }

    private static String stringOrNull(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private void requireNotifyLicense() {
        platformLicenseService.requireFeature(LicenseFeature.NOTIFY, "使用通知功能需要授权");
    }

    private static UUID uuid(Map<String, Object> body, String key) {
        try {
            return UUID.fromString(requiredString(body, key));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, key + " is invalid");
        }
    }
}
