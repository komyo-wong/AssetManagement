package com.assetmanagement.notification.api;

import com.assetmanagement.notification.application.NotificationPreferenceService;
import com.assetmanagement.shared.api.PageResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/me/notifications")
public class NotificationPreferenceController {

    private final NotificationPreferenceService service;

    public NotificationPreferenceController(NotificationPreferenceService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, Object> overview() {
        return service.overview();
    }

    @GetMapping("/channels")
    public List<Map<String, Object>> channels() {
        return service.listChannels();
    }

    @PostMapping("/channels")
    public Map<String, Object> createChannel(@RequestBody Map<String, Object> body) {
        return service.createChannel(body);
    }

    @PutMapping("/channels/{id}")
    public Map<String, Object> updateChannel(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return service.updateChannel(id, body);
    }

    @DeleteMapping("/channels/{id}")
    public void deleteChannel(@PathVariable UUID id) {
        service.deleteChannel(id);
    }

    @GetMapping("/subscriptions")
    public List<Map<String, Object>> subscriptions() {
        return service.listSubscriptions();
    }

    @PostMapping("/subscriptions")
    public Map<String, Object> createSubscription(@RequestBody Map<String, Object> body) {
        return service.createSubscription(body);
    }

    @PutMapping("/subscriptions/{id}")
    public Map<String, Object> updateSubscription(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return service.updateSubscription(id, body);
    }

    @DeleteMapping("/subscriptions/{id}")
    public void deleteSubscription(@PathVariable UUID id) {
        service.deleteSubscription(id);
    }

    @GetMapping("/deliveries")
    public PageResponse<Map<String, Object>> deliveries(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size
    ) {
        return service.listDeliveries(current, size);
    }
}
