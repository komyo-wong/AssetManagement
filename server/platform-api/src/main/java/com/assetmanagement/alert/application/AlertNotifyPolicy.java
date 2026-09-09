package com.assetmanagement.alert.application;

import com.assetmanagement.alert.domain.AlertEvent;
import com.assetmanagement.alert.repository.AlertEventRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Shared gates so flapping presence does not create alert/email storms.
 */
@Component
public class AlertNotifyPolicy {

    /** Same rule+resource must stay resolved at least this long before a new OPEN. */
    public static final Duration REOPEN_COOLDOWN = Duration.ofMinutes(10);

    /** Recovery mail is skipped when the ticket lived shorter than this (still recorded as SKIPPED). */
    public static final Duration MIN_OPEN_FOR_RECOVERY_NOTIFY = Duration.ofMinutes(2);

    private final AlertEventRepository alertEventRepository;

    public AlertNotifyPolicy(AlertEventRepository alertEventRepository) {
        this.alertEventRepository = alertEventRepository;
    }

    public boolean allowOpenNew(
            UUID projectId,
            UUID ruleId,
            String resourceType,
            UUID resourceId,
            Instant now
    ) {
        if (projectId == null || ruleId == null || resourceType == null || resourceId == null) {
            return true;
        }
        return alertEventRepository
                .findFirstByProjectIdAndRuleIdAndResourceTypeAndResourceIdAndStatusOrderByResolvedAtDesc(
                        projectId, ruleId, resourceType, resourceId, "RESOLVED")
                .map(AlertEvent::getResolvedAt)
                .filter(resolvedAt -> resolvedAt != null && resolvedAt.isAfter(now.minus(REOPEN_COOLDOWN)))
                .isEmpty();
    }

    public static boolean isShortLivedRecovery(AlertEvent event) {
        if (event == null || event.getOpenedAt() == null || event.getResolvedAt() == null) {
            return false;
        }
        return Duration.between(event.getOpenedAt(), event.getResolvedAt())
                .compareTo(MIN_OPEN_FOR_RECOVERY_NOTIFY) < 0;
    }
}
