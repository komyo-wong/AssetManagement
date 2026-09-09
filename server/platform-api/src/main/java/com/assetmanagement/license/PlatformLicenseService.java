package com.assetmanagement.license;

import com.assetmanagement.iam.PermissionCodes;
import com.assetmanagement.license.domain.PlatformInstall;
import com.assetmanagement.license.domain.PlatformLicenseActive;
import com.assetmanagement.license.domain.PlatformLicenseUsed;
import com.assetmanagement.license.repository.PlatformInstallRepository;
import com.assetmanagement.license.repository.PlatformLicenseActiveRepository;
import com.assetmanagement.license.repository.PlatformLicenseUsedRepository;
import com.assetmanagement.platform.application.PlatformAuditRecorder;
import com.assetmanagement.security.CurrentUserPrincipal;
import com.assetmanagement.security.CurrentUserProvider;
import com.assetmanagement.shared.exception.BusinessException;
import com.assetmanagement.shared.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PlatformLicenseService {

    private static final Logger log = LoggerFactory.getLogger(PlatformLicenseService.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final PlatformInstallRepository installRepository;
    private final PlatformLicenseUsedRepository usedRepository;
    private final PlatformLicenseActiveRepository activeRepository;
    private final CurrentUserProvider currentUserProvider;
    private final PlatformAuditRecorder platformAuditRecorder;
    private final Path licenseDir;

    public PlatformLicenseService(
            PlatformInstallRepository installRepository,
            PlatformLicenseUsedRepository usedRepository,
            PlatformLicenseActiveRepository activeRepository,
            CurrentUserProvider currentUserProvider,
            PlatformAuditRecorder platformAuditRecorder,
            PlatformLicenseProperties properties
    ) {
        this.installRepository = installRepository;
        this.usedRepository = usedRepository;
        this.activeRepository = activeRepository;
        this.currentUserProvider = currentUserProvider;
        this.platformAuditRecorder = platformAuditRecorder;
        this.licenseDir = Path.of(properties.dir()).toAbsolutePath().normalize();
    }

    @Transactional
    public UUID installId() {
        return ensureInstall().getInstallId();
    }

    @Transactional(readOnly = true)
    public boolean has(String feature) {
        LicenseClaims claims = currentClaimsOrNull(false);
        return claims != null && claims.has(feature);
    }

    public void requireFeature(String feature, String message) {
        if (!has(feature)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, message);
        }
    }

    public Integer maxBeaconsOrNull() {
        LicenseClaims claims = currentClaimsOrNull(false);
        return claims == null ? null : claims.maxBeacons();
    }

    public Integer maxGatewaysOrNull() {
        LicenseClaims claims = currentClaimsOrNull(false);
        return claims == null ? null : claims.maxGateways();
    }

    public void requireUnderLimit(Integer max, long current, String message) {
        if (max != null && current >= max) {
            throw new BusinessException(ErrorCode.FORBIDDEN, message);
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> publicFlags() {
        LicenseClaims claims = currentClaimsOrNull(false);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("notify", claims != null && claims.has(LicenseFeature.NOTIFY));
        map.put("loginCopyright", claims != null && claims.has(LicenseFeature.LOGIN_COPYRIGHT));
        map.put("eink", claims != null && claims.has(LicenseFeature.EINK));
        map.put("buzz", claims != null && claims.has(LicenseFeature.BUZZ));
        map.put("maxBeacons", claims == null ? null : claims.maxBeacons());
        map.put("maxGateways", claims == null ? null : claims.maxGateways());
        return map;
    }

    @Transactional
    public Map<String, Object> status() {
        require(PermissionCodes.PLATFORM_OPS_READ);
        return statusMap();
    }

    @Transactional
    public Map<String, Object> activate(String token) {
        require(PermissionCodes.PLATFORM_OPS_MANAGE);
        String raw = token == null ? "" : token.trim();
        if (raw.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请粘贴授权码");
        }
        LicenseClaims claims;
        try {
            claims = LicenseCodec.verify(raw);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "授权码无效");
        }
        if (claims.expiredOn(LocalDate.now(ZONE))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "授权码已过期");
        }
        UUID installId = ensureInstall().getInstallId();
        importUsedFromFile();
        if (!installId.equals(claims.installId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "授权码与当前安装 ID 不匹配，每个码只能绑定一台服务器");
        }
        PlatformLicenseActive active = activeRepository.findFirstByOrderByCreatedAtAsc().orElse(null);
        // 同一安装 ID 允许再贴一次（同机重装库空了但卷还在）。换机器会先被上面的 installId 拦住。
        if (!usedRepository.existsByJti(claims.id())) {
            usedRepository.save(new PlatformLicenseUsed(claims.id()));
            appendUsedFile(claims.id());
        }
        if (active == null) {
            active = new PlatformLicenseActive();
        }
        active.apply(
                claims.id(),
                raw,
                claims.who(),
                String.join(",", claims.featureSet()),
                claims.until(),
                Instant.now()
        );
        activeRepository.save(active);
        platformAuditRecorder.record(
                "platform.license.activate",
                "platform_license",
                claims.id().toString(),
                Map.of(
                        "who", claims.who() == null ? "" : claims.who(),
                        "features", String.join(",", claims.featureSet())
                )
        );
        return statusMap();
    }

    private Map<String, Object> statusMap() {
        LicenseClaims claims = currentClaimsOrNull(true);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("installId", ensureInstall().getInstallId().toString());
        map.put("activated", claims != null);
        map.put("who", claims == null ? null : claims.who());
        map.put("until", claims == null || claims.until() == null ? null : claims.until().toString());
        map.put("features", claims == null ? Set.of() : claims.featureSet());
        map.put("notify", claims != null && claims.has(LicenseFeature.NOTIFY));
        map.put("loginCopyright", claims != null && claims.has(LicenseFeature.LOGIN_COPYRIGHT));
        map.put("eink", claims != null && claims.has(LicenseFeature.EINK));
        map.put("buzz", claims != null && claims.has(LicenseFeature.BUZZ));
        map.put("maxBeacons", claims == null ? null : claims.maxBeacons());
        map.put("maxGateways", claims == null ? null : claims.maxGateways());
        return map;
    }

    private LicenseClaims currentClaimsOrNull(boolean createInstall) {
        PlatformLicenseActive active = activeRepository.findFirstByOrderByCreatedAtAsc().orElse(null);
        if (active == null || active.getToken() == null) {
            return null;
        }
        UUID installId = createInstall
                ? ensureInstall().getInstallId()
                : installRepository.findFirstByOrderByCreatedAtAsc().map(PlatformInstall::getInstallId).orElse(null);
        if (installId == null) {
            return null;
        }
        try {
            LicenseClaims claims = LicenseCodec.verify(active.getToken());
            if (claims.expiredOn(LocalDate.now(ZONE))) {
                return null;
            }
            if (!installId.equals(claims.installId())) {
                return null;
            }
            return claims;
        } catch (RuntimeException ex) {
            log.warn("stored license is invalid: {}", ex.toString());
            return null;
        }
    }

    private PlatformInstall ensureInstall() {
        PlatformInstall row = installRepository.findFirstByOrderByCreatedAtAsc().orElse(null);
        UUID fromFile = readInstallFile();
        if (row != null) {
            if (fromFile == null) {
                writeInstallFile(row.getInstallId());
            }
            return row;
        }
        UUID installId = fromFile == null ? UUID.randomUUID() : fromFile;
        PlatformInstall created = installRepository.save(new PlatformInstall(installId));
        writeInstallFile(created.getInstallId());
        return created;
    }

    private Path installFile() {
        return licenseDir.resolve("install-id");
    }

    private Path usedFile() {
        return licenseDir.resolve("used-jtis.txt");
    }

    private UUID readInstallFile() {
        Path file = installFile();
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8).trim();
            return text.isEmpty() ? null : UUID.fromString(text);
        } catch (Exception ex) {
            return null;
        }
    }

    private void writeInstallFile(UUID installId) {
        try {
            Files.createDirectories(licenseDir);
            Files.writeString(installFile(), installId.toString() + "\n", StandardCharsets.UTF_8);
        } catch (IOException ex) {
            log.warn("could not persist install id file: {}", ex.toString());
        }
    }

    private void importUsedFromFile() {
        Path file = usedFile();
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            for (String line : Files.readString(file, StandardCharsets.UTF_8).split("\\R")) {
                String text = line.trim();
                if (text.isEmpty()) {
                    continue;
                }
                try {
                    UUID jti = UUID.fromString(text);
                    if (!usedRepository.existsByJti(jti)) {
                        usedRepository.save(new PlatformLicenseUsed(jti));
                    }
                } catch (IllegalArgumentException ignored) {
                    /* skip */
                }
            }
        } catch (IOException ex) {
            log.warn("could not import used license file: {}", ex.toString());
        }
    }

    private void appendUsedFile(UUID jti) {
        try {
            Files.createDirectories(licenseDir);
            Path file = usedFile();
            Set<String> existing = new LinkedHashSet<>();
            if (Files.isRegularFile(file)) {
                existing.addAll(Arrays.stream(Files.readString(file, StandardCharsets.UTF_8).split("\\R"))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toSet()));
            }
            existing.add(jti.toString());
            Files.writeString(file, String.join("\n", existing) + "\n", StandardCharsets.UTF_8);
        } catch (IOException ex) {
            log.warn("could not persist used license file: {}", ex.toString());
        }
    }

    private void require(String permission) {
        CurrentUserPrincipal principal = currentUserProvider.requireCurrentUser();
        if (!principal.hasPlatformPermission(permission)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Missing permission: " + permission);
        }
    }
}
