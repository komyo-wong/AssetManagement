package com.assetmanagement.mqtt.worker.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.mqtt-worker", name = "enabled", havingValue = "true")
public class WorkerRestartWatcher {

    public static final String RESTART_KEY = "am:ops:restart:worker";

    private static final Logger log = LoggerFactory.getLogger(WorkerRestartWatcher.class);

    private final StringRedisTemplate redisTemplate;

    public WorkerRestartWatcher(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Scheduled(fixedDelay = 2000)
    public void poll() {
        try {
            String signal = redisTemplate.opsForValue().getAndDelete(RESTART_KEY);
            if (signal == null || signal.isBlank()) {
                return;
            }
            log.warn("Platform ops requested MQTT worker restart ({})", signal);
            Thread thread = new Thread(() -> {
                try {
                    Thread.sleep(400);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                System.exit(0);
            }, "ops-worker-restart");
            thread.setDaemon(false);
            thread.start();
        } catch (RuntimeException ex) {
            log.debug("Worker restart poll skipped: {}", ex.getMessage());
        }
    }
}
