package com.assetmanagement.business.api;

import com.assetmanagement.business.application.ProjectBusinessService;
import com.assetmanagement.shared.api.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/projects/{projectId}")
public class ProjectBusinessController {

    private final ProjectBusinessService businessService;

    public ProjectBusinessController(ProjectBusinessService businessService) {
        this.businessService = businessService;
    }

    @GetMapping("/asset-types")
    public List<ResourceDtos.NamedResource> listAssetTypes(@PathVariable UUID tenantId, @PathVariable UUID projectId) {
        return businessService.listAssetTypes(tenantId, projectId);
    }

    @PostMapping("/asset-types")
    public ResourceDtos.NamedResource createAssetType(
            @PathVariable UUID tenantId, @PathVariable UUID projectId, @RequestBody ResourceDtos.UpsertRequest request) {
        return businessService.createAssetType(tenantId, projectId, request);
    }

    @PutMapping("/asset-types/{id}")
    public ResourceDtos.NamedResource updateAssetType(
            @PathVariable UUID tenantId, @PathVariable UUID projectId, @PathVariable UUID id,
            @RequestBody ResourceDtos.UpsertRequest request) {
        return businessService.updateAssetType(tenantId, projectId, id, request);
    }

    @DeleteMapping("/asset-types/{id}")
    public void deleteAssetType(@PathVariable UUID tenantId, @PathVariable UUID projectId, @PathVariable UUID id) {
        businessService.deleteAssetType(tenantId, projectId, id);
    }

    @GetMapping("/assets")
    public List<ResourceDtos.NamedResource> listAssets(@PathVariable UUID tenantId, @PathVariable UUID projectId) {
        return businessService.listAssets(tenantId, projectId);
    }

    @PostMapping("/assets")
    public ResourceDtos.NamedResource createAsset(
            @PathVariable UUID tenantId, @PathVariable UUID projectId, @RequestBody ResourceDtos.UpsertRequest request) {
        return businessService.createAsset(tenantId, projectId, request);
    }

    @PostMapping(value = "/assets/images", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadAssetImage(
            @PathVariable UUID tenantId,
            @PathVariable UUID projectId,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file
    ) {
        return businessService.uploadAssetImage(tenantId, projectId, file);
    }

    @PutMapping("/assets/{id}")
    public ResourceDtos.NamedResource updateAsset(
            @PathVariable UUID tenantId, @PathVariable UUID projectId, @PathVariable UUID id,
            @RequestBody ResourceDtos.UpsertRequest request) {
        return businessService.updateAsset(tenantId, projectId, id, request);
    }

    @DeleteMapping("/assets/{id}")
    public void deleteAsset(@PathVariable UUID tenantId, @PathVariable UUID projectId, @PathVariable UUID id) {
        businessService.deleteAsset(tenantId, projectId, id);
    }

    @PostMapping("/assets/{assetId}/bind-beacon/{beaconId}")
    public ResourceDtos.NamedResource bindBeacon(
            @PathVariable UUID tenantId, @PathVariable UUID projectId,
            @PathVariable UUID assetId, @PathVariable UUID beaconId,
            @RequestParam(required = false) String protocolType,
            @RequestBody(required = false) Map<String, Object> body) {
        String protocol = protocolType;
        if ((protocol == null || protocol.isBlank()) && body != null && body.get("protocolType") != null) {
            protocol = String.valueOf(body.get("protocolType"));
        }
        if (protocol == null || protocol.isBlank()) {
            protocol = "AUTO";
        }
        return businessService.bindBeacon(tenantId, projectId, assetId, beaconId, protocol);
    }

    @PostMapping("/assets/{assetId}/unbind-beacon")
    public ResourceDtos.NamedResource unbindBeacon(
            @PathVariable UUID tenantId, @PathVariable UUID projectId, @PathVariable UUID assetId) {
        return businessService.unbindBeacon(tenantId, projectId, assetId);
    }

    @PostMapping("/assets/{assetId}/buzzer")
    public Map<String, Object> buzzAsset(
            @PathVariable UUID tenantId, @PathVariable UUID projectId, @PathVariable UUID assetId,
            @RequestBody Map<String, Object> body) {
        Object raw = body == null ? null : body.get("mode");
        String mode = raw == null ? null : String.valueOf(raw);
        return businessService.buzzAsset(tenantId, projectId, assetId, mode);
    }

    @PostMapping("/assets/{assetId}/eink-jobs")
    public Map<String, Object> pushAssetEink(
            @PathVariable UUID tenantId, @PathVariable UUID projectId, @PathVariable UUID assetId,
            @RequestBody Map<String, Object> body) {
        return businessService.pushAssetEink(tenantId, projectId, assetId, body);
    }

    @GetMapping("/assets/{assetId}/eink-last")
    public Map<String, Object> getAssetEinkLast(
            @PathVariable UUID tenantId, @PathVariable UUID projectId, @PathVariable UUID assetId) {
        return businessService.getAssetEinkLast(tenantId, projectId, assetId);
    }

    @GetMapping("/assets/{assetId}/eink-jobs")
    public PageResponse<Map<String, Object>> listAssetEinkJobs(
            @PathVariable UUID tenantId, @PathVariable UUID projectId, @PathVariable UUID assetId,
            @RequestParam(defaultValue = "1") @Min(1) long current,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size) {
        return businessService.listEinkJobs(tenantId, projectId, assetId, current, size);
    }

    @GetMapping("/eink-jobs/{jobId}")
    public Map<String, Object> getEinkJob(
            @PathVariable UUID tenantId, @PathVariable UUID projectId, @PathVariable UUID jobId) {
        return businessService.getEinkJob(tenantId, projectId, jobId);
    }

    @PutMapping("/beacons/{beaconId}/eink-settings")
    public Map<String, Object> updateBeaconEinkSettings(
            @PathVariable UUID tenantId, @PathVariable UUID projectId, @PathVariable UUID beaconId,
            @RequestBody Map<String, Object> body) {
        return businessService.updateBeaconEinkSettings(tenantId, projectId, beaconId, body);
    }

    @GetMapping("/beacons/{beaconId}/scans")
    public PageResponse<ResourceDtos.NamedResource> beaconScans(
            @PathVariable UUID tenantId, @PathVariable UUID projectId, @PathVariable UUID beaconId,
            @RequestParam(defaultValue = "1") @Min(1) long current,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) long size) {
        return businessService.trackingHistory(tenantId, projectId, beaconId, current, size);
    }

    @GetMapping("/beacons/{beaconId}/presence-events")
    public PageResponse<ResourceDtos.NamedResource> beaconPresenceEvents(
            @PathVariable UUID tenantId, @PathVariable UUID projectId, @PathVariable UUID beaconId,
            @RequestParam(defaultValue = "1") @Min(1) long current,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) long size,
            @RequestParam(defaultValue = "30") @Min(1) @Max(90) int days) {
        return businessService.beaconPresenceHistory(tenantId, projectId, beaconId, current, size, days);
    }

    @GetMapping("/gateways")
    public List<ResourceDtos.NamedResource> listGateways(@PathVariable UUID tenantId, @PathVariable UUID projectId) {
        return businessService.listGateways(tenantId, projectId);
    }

    @GetMapping("/gateways/next-code")
    public Map<String, String> nextGatewayCode(@PathVariable UUID tenantId, @PathVariable UUID projectId) {
        return businessService.nextGatewayCode(tenantId, projectId);
    }

    @PostMapping("/gateways")
    public ResourceDtos.NamedResource createGateway(
            @PathVariable UUID tenantId, @PathVariable UUID projectId, @RequestBody ResourceDtos.UpsertRequest request) {
        return businessService.createGateway(tenantId, projectId, request);
    }

    @PutMapping("/gateways/{id}")
    public ResourceDtos.NamedResource updateGateway(
            @PathVariable UUID tenantId, @PathVariable UUID projectId, @PathVariable UUID id,
            @RequestBody ResourceDtos.UpsertRequest request) {
        return businessService.updateGateway(tenantId, projectId, id, request);
    }

    @DeleteMapping("/gateways/{id}")
    public void deleteGateway(@PathVariable UUID tenantId, @PathVariable UUID projectId, @PathVariable UUID id) {
        businessService.deleteGateway(tenantId, projectId, id);
    }

    @GetMapping("/gateways/{id}/provision")
    public Map<String, Object> gatewayProvision(
            @PathVariable UUID tenantId, @PathVariable UUID projectId, @PathVariable UUID id) {
        return businessService.getGatewayProvision(tenantId, projectId, id);
    }

    @GetMapping("/beacons")
    public List<ResourceDtos.NamedResource> listBeacons(@PathVariable UUID tenantId, @PathVariable UUID projectId) {
        return businessService.listBeacons(tenantId, projectId);
    }

    @PostMapping("/beacons")
    public ResourceDtos.NamedResource createBeacon(
            @PathVariable UUID tenantId, @PathVariable UUID projectId, @RequestBody ResourceDtos.UpsertRequest request) {
        return businessService.createBeacon(tenantId, projectId, request);
    }

    @PutMapping("/beacons/{id}")
    public ResourceDtos.NamedResource updateBeacon(
            @PathVariable UUID tenantId, @PathVariable UUID projectId, @PathVariable UUID id,
            @RequestBody ResourceDtos.UpsertRequest request) {
        return businessService.updateBeacon(tenantId, projectId, id, request);
    }

    @DeleteMapping("/beacons/{id}")
    public void deleteBeacon(@PathVariable UUID tenantId, @PathVariable UUID projectId, @PathVariable UUID id) {
        businessService.deleteBeacon(tenantId, projectId, id);
    }

    @PostMapping("/beacons/batch-delete")
    public Map<String, Object> deleteBeacons(
            @PathVariable UUID tenantId, @PathVariable UUID projectId,
            @RequestBody ResourceDtos.BatchIdsRequest request) {
        return businessService.deleteBeacons(tenantId, projectId, request == null ? List.of() : request.ids());
    }

    @GetMapping("/beacons/import-template")
    public org.springframework.http.ResponseEntity<byte[]> beaconImportTemplate(
            @PathVariable UUID tenantId, @PathVariable UUID projectId) {
        byte[] body = businessService.beaconImportTemplateCsv(tenantId, projectId);
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"beacon-import-template.csv\"")
                .contentType(new org.springframework.http.MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .body(body);
    }

    @PostMapping(value = "/beacons/import", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> importBeacons(
            @PathVariable UUID tenantId,
            @PathVariable UUID projectId,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        return businessService.importBeacons(tenantId, projectId, file);
    }

    @GetMapping("/maps")
    public List<ResourceDtos.NamedResource> listMaps(@PathVariable UUID tenantId, @PathVariable UUID projectId) {
        return businessService.listMaps(tenantId, projectId);
    }

    @PostMapping("/maps")
    public ResourceDtos.NamedResource createMap(
            @PathVariable UUID tenantId, @PathVariable UUID projectId, @RequestBody ResourceDtos.UpsertRequest request) {
        return businessService.createMap(tenantId, projectId, request);
    }

    @PostMapping(value = "/maps/images", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadMapImage(
            @PathVariable UUID tenantId,
            @PathVariable UUID projectId,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file
    ) {
        return businessService.uploadMapImage(tenantId, projectId, file);
    }

    @PutMapping("/maps/{id}")
    public ResourceDtos.NamedResource updateMap(
            @PathVariable UUID tenantId, @PathVariable UUID projectId, @PathVariable UUID id,
            @RequestBody ResourceDtos.UpsertRequest request) {
        return businessService.updateMap(tenantId, projectId, id, request);
    }

    @DeleteMapping("/maps/{id}")
    public void deleteMap(@PathVariable UUID tenantId, @PathVariable UUID projectId, @PathVariable UUID id) {
        businessService.deleteMap(tenantId, projectId, id);
    }

    @GetMapping("/zones")
    public List<ResourceDtos.NamedResource> listZones(@PathVariable UUID tenantId, @PathVariable UUID projectId) {
        return businessService.listZones(tenantId, projectId);
    }

    @PostMapping("/zones")
    public ResourceDtos.NamedResource createZone(
            @PathVariable UUID tenantId, @PathVariable UUID projectId, @RequestBody ResourceDtos.UpsertRequest request) {
        return businessService.createZone(tenantId, projectId, request);
    }

    @PutMapping("/zones/{id}")
    public ResourceDtos.NamedResource updateZone(
            @PathVariable UUID tenantId, @PathVariable UUID projectId, @PathVariable UUID id,
            @RequestBody ResourceDtos.UpsertRequest request) {
        return businessService.updateZone(tenantId, projectId, id, request);
    }

    @DeleteMapping("/zones/{id}")
    public void deleteZone(@PathVariable UUID tenantId, @PathVariable UUID projectId, @PathVariable UUID id) {
        businessService.deleteZone(tenantId, projectId, id);
    }

    @GetMapping("/tracking/live")
    public List<ResourceDtos.NamedResource> liveTracking(@PathVariable UUID tenantId, @PathVariable UUID projectId) {
        return businessService.liveTracking(tenantId, projectId);
    }

    @GetMapping("/tracking/history")
    public PageResponse<ResourceDtos.NamedResource> trackingHistory(
            @PathVariable UUID tenantId, @PathVariable UUID projectId,
            @RequestParam(required = false) UUID beaconId,
            @RequestParam(defaultValue = "1") @Min(1) long current,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) long size) {
        return businessService.trackingHistory(tenantId, projectId, beaconId, current, size);
    }

    @GetMapping("/roll-calls")
    public List<ResourceDtos.NamedResource> listRollCalls(@PathVariable UUID tenantId, @PathVariable UUID projectId) {
        return businessService.listRollCalls(tenantId, projectId);
    }

    @PostMapping("/roll-calls")
    public ResourceDtos.NamedResource startRollCall(
            @PathVariable UUID tenantId, @PathVariable UUID projectId, @RequestBody ResourceDtos.UpsertRequest request) {
        return businessService.startRollCall(tenantId, projectId, request);
    }

    @GetMapping("/alert-rules")
    public List<ResourceDtos.NamedResource> listAlertRules(@PathVariable UUID tenantId, @PathVariable UUID projectId) {
        return businessService.listAlertRules(tenantId, projectId);
    }

    @PostMapping("/alert-rules")
    public ResourceDtos.NamedResource createAlertRule(
            @PathVariable UUID tenantId, @PathVariable UUID projectId, @RequestBody ResourceDtos.UpsertRequest request) {
        return businessService.createAlertRule(tenantId, projectId, request);
    }

    @PutMapping("/alert-rules/{id}")
    public ResourceDtos.NamedResource updateAlertRule(
            @PathVariable UUID tenantId, @PathVariable UUID projectId, @PathVariable UUID id,
            @RequestBody ResourceDtos.UpsertRequest request) {
        return businessService.updateAlertRule(tenantId, projectId, id, request);
    }

    @GetMapping("/alerts")
    public PageResponse<ResourceDtos.NamedResource> listAlerts(
            @PathVariable UUID tenantId, @PathVariable UUID projectId,
            @RequestParam(defaultValue = "1") @Min(1) long current,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) long size) {
        return businessService.listAlertEvents(tenantId, projectId, current, size);
    }

    @PostMapping("/alerts/{id}/acknowledge")
    public ResourceDtos.NamedResource acknowledgeAlert(
            @PathVariable UUID tenantId, @PathVariable UUID projectId, @PathVariable UUID id) {
        return businessService.acknowledgeAlert(tenantId, projectId, id);
    }

    @PostMapping("/alerts/{id}/resolve")
    public ResourceDtos.NamedResource resolveAlert(
            @PathVariable UUID tenantId, @PathVariable UUID projectId, @PathVariable UUID id) {
        return businessService.resolveAlert(tenantId, projectId, id);
    }

    @GetMapping("/inventory-sessions")
    public List<ResourceDtos.NamedResource> listInventory(@PathVariable UUID tenantId, @PathVariable UUID projectId) {
        return businessService.listInventory(tenantId, projectId);
    }

    @GetMapping("/inventory-sessions/{id}")
    public ResourceDtos.NamedResource getInventory(
            @PathVariable UUID tenantId, @PathVariable UUID projectId, @PathVariable UUID id) {
        return businessService.getInventory(tenantId, projectId, id);
    }

    @PostMapping("/inventory-sessions")
    public ResourceDtos.NamedResource startInventory(
            @PathVariable UUID tenantId, @PathVariable UUID projectId, @RequestBody ResourceDtos.UpsertRequest request) {
        return businessService.startInventory(tenantId, projectId, request);
    }

    @PostMapping("/inventory-sessions/{id}/close")
    public ResourceDtos.NamedResource closeInventory(
            @PathVariable UUID tenantId, @PathVariable UUID projectId, @PathVariable UUID id) {
        return businessService.closeInventory(tenantId, projectId, id);
    }

    @DeleteMapping("/inventory-sessions/{id}")
    public void deleteInventory(
            @PathVariable UUID tenantId, @PathVariable UUID projectId, @PathVariable UUID id) {
        businessService.deleteInventory(tenantId, projectId, id);
    }

    @GetMapping("/tasks")
    public List<ResourceDtos.NamedResource> listTasks(@PathVariable UUID tenantId, @PathVariable UUID projectId) {
        return businessService.listTasks(tenantId, projectId);
    }

    @PostMapping("/tasks")
    public ResourceDtos.NamedResource createTask(
            @PathVariable UUID tenantId, @PathVariable UUID projectId, @RequestBody ResourceDtos.UpsertRequest request) {
        return businessService.createTask(tenantId, projectId, request);
    }

    @PutMapping("/tasks/{id}")
    public ResourceDtos.NamedResource updateTask(
            @PathVariable UUID tenantId, @PathVariable UUID projectId, @PathVariable UUID id,
            @RequestBody ResourceDtos.UpsertRequest request) {
        return businessService.updateTask(tenantId, projectId, id, request);
    }

    @GetMapping("/documents")
    public List<ResourceDtos.NamedResource> listDocuments(@PathVariable UUID tenantId, @PathVariable UUID projectId) {
        return businessService.listDocuments(tenantId, projectId);
    }

    @PostMapping("/documents")
    public ResourceDtos.NamedResource createDocument(
            @PathVariable UUID tenantId, @PathVariable UUID projectId, @RequestBody ResourceDtos.UpsertRequest request) {
        return businessService.createDocument(tenantId, projectId, request);
    }

    @PostMapping(value = "/documents/upload", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResourceDtos.NamedResource uploadDocument(
            @PathVariable UUID tenantId,
            @PathVariable UUID projectId,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file
    ) {
        return businessService.uploadDocument(tenantId, projectId, title, file);
    }

    @PutMapping("/documents/{id}")
    public ResourceDtos.NamedResource updateDocument(
            @PathVariable UUID tenantId,
            @PathVariable UUID projectId,
            @PathVariable UUID id,
            @RequestBody ResourceDtos.UpsertRequest request
    ) {
        return businessService.updateDocument(tenantId, projectId, id, request);
    }

    @GetMapping("/documents/{id}/content")
    public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> downloadDocument(
            @PathVariable UUID tenantId,
            @PathVariable UUID projectId,
            @PathVariable UUID id
    ) {
        var doc = businessService.requireDocument(tenantId, projectId, id);
        var resource = businessService.loadDocumentContent(tenantId, projectId, id);
        String contentType = doc.getContentType() == null ? "application/octet-stream" : doc.getContentType();
        boolean inline = contentType.startsWith("image/");
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        (inline ? "inline" : "attachment") + "; filename=\"" + doc.getFileName().replace("\"", "") + "\"")
                .contentType(org.springframework.http.MediaType.parseMediaType(contentType))
                .body(resource);
    }

    @DeleteMapping("/documents/{id}")
    public void deleteDocument(
            @PathVariable UUID tenantId,
            @PathVariable UUID projectId,
            @PathVariable UUID id
    ) {
        businessService.deleteDocument(tenantId, projectId, id);
    }

    @GetMapping("/analytics/summary")
    public Map<String, Object> analyticsSummary(@PathVariable UUID tenantId, @PathVariable UUID projectId) {
        return businessService.analyticsSummary(tenantId, projectId);
    }
}
