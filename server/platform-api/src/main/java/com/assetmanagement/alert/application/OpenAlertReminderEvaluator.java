package com.assetmanagement.alert.application;

import com.assetmanagement.alert.domain.AlertEvent;
import com.assetmanagement.alert.repository.AlertEventRepository;
import com.assetmanagement.notification.application.NotificationDispatchService;
import com.assetmanagement.notification.repository.NotificationSubscriptionRepository;
import com.assetmanagement.project.domain.Project;
import com.assetmanagement.project.domain.ProjectStatus;
import com.assetmanagement.project.repository.ProjectRepository;
import com.assetmanagement.security.RlsContextExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Re-invokes ALERT dispatch for OPEN events so subscriptions with throttleSeconds &gt; 0
 * can send repeat reminders. Interval gating lives in NotificationDispatchService.
 */
@Component
public class OpenAlertReminderEvaluator {

    private static final Logger log = LoggerFactory.getLogger(OpenAlertReminderEvaluator.class);

    private final RlsContextExecutor rlsContextExecutor;
    private final ProjectRepository projectRepository;
    private final AlertEventRepository alertEventRepository;
    private final NotificationSubscriptionRepository subscriptionRepository;
    private final NotificationDispatchService notificationDispatchService;

    public OpenAlertReminderEvaluator(
            RlsContextExecutor rlsContextExecutor,
            ProjectRepository projectRepository,
            AlertEventRepository alertEventRepository,
            NotificationSubscriptionRepository subscriptionRepository,
            NotificationDispatchService notificationDispatchService
    ) {
        this.rlsContextExecutor = rlsContextExecutor;
        this.projectRepository = projectRepository;
        this.alertEventRepository = alertEventRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.notificationDispatchService = notificationDispatchService;
    }

    @Scheduled(fixedDelayString = "${app.alert.reminder-eval-interval:60s}")
    public void remind() {
        try {
            rlsContextExecutor.asPlatform(this::remindAll);
        } catch (Exception ex) {
            log.warn("open alert reminder failed: {}", ex.toString());
        }
    }

    @Transactional
    protected Void remindAll() {
        for (Project project : projectRepository.findAllByStatus(ProjectStatus.ACTIVE)) {
            boolean needsReminder = subscriptionRepository.findAllByProjectIdAndEnabledTrue(project.getId()).stream()
                    .anyMatch(s -> s.isOnAlert()
                            && s.getThrottleSeconds() != null
                            && s.getThrottleSeconds() > 0);
            if (!needsReminder) {
                continue;
            }
            for (AlertEvent event : alertEventRepository.findAllByProjectIdAndStatus(project.getId(), "OPEN")) {
                notificationDispatchService.dispatchAlertOpened(event);
            }
        }
        return null;
    }
}
