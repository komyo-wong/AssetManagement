package com.assetmanagement.ops;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/platform/ops")
public class PlatformOpsController {

    private final PlatformOpsService platformOpsService;

    public PlatformOpsController(PlatformOpsService platformOpsService) {
        this.platformOpsService = platformOpsService;
    }

    @GetMapping
    public Map<String, Object> overview() {
        return platformOpsService.overview();
    }

    @GetMapping("/health")
    public List<Map<String, Object>> health() {
        return platformOpsService.health();
    }

    @PostMapping("/cleanup/preview")
    public Map<String, Object> previewCleanup(@RequestBody(required = false) Map<String, Object> body) {
        return platformOpsService.previewCleanup(body == null ? Map.of() : body);
    }

    @PostMapping("/cleanup")
    public Map<String, Object> cleanup(@RequestBody(required = false) Map<String, Object> body) {
        return platformOpsService.executeCleanup(body == null ? Map.of() : body);
    }

    @GetMapping("/cleanup/status")
    public Map<String, Object> cleanupStatus() {
        return platformOpsService.cleanupStatus();
    }

    @PutMapping("/cleanup/schedule")
    public Map<String, Object> updateCleanupSchedule(@RequestBody Map<String, Object> body) {
        return platformOpsService.updateAutoCleanup(body == null ? Map.of() : body);
    }

    @GetMapping("/backups")
    public List<Map<String, Object>> backups() {
        return platformOpsService.listBackups();
    }

    @PostMapping("/backups")
    public Map<String, Object> createBackup() {
        return platformOpsService.createBackup();
    }

    @GetMapping("/backups/{fileName:.+}")
    public ResponseEntity<Resource> downloadBackup(@PathVariable String fileName) {
        return platformOpsService.downloadBackup(fileName);
    }

    @DeleteMapping("/backups/{fileName:.+}")
    public Map<String, Object> deleteBackup(@PathVariable String fileName) {
        return platformOpsService.deleteBackup(fileName);
    }

    @PostMapping("/restore")
    public Map<String, Object> restore(@RequestBody Map<String, Object> body) {
        String fileName = body == null ? null : String.valueOf(body.getOrDefault("fileName", ""));
        String confirm = body == null || body.get("confirm") == null ? "" : String.valueOf(body.get("confirm"));
        return platformOpsService.restoreBackup(fileName, confirm);
    }

    @PostMapping(value = "/restore/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> restoreUpload(
            @RequestPart("file") MultipartFile file,
            @RequestParam("confirm") String confirm
    ) {
        return platformOpsService.restoreUpload(file, confirm);
    }

    @PostMapping("/restart")
    public Map<String, Object> restart(@RequestBody Map<String, Object> body) {
        String service = body == null ? null : String.valueOf(body.getOrDefault("service", ""));
        String confirm = body == null || body.get("confirm") == null ? "" : String.valueOf(body.get("confirm"));
        return platformOpsService.restart(service, confirm);
    }
}
