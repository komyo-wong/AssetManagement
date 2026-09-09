package com.assetmanagement.auth;

import com.assetmanagement.iam.domain.User;
import com.assetmanagement.iam.repository.UserRepository;
import com.assetmanagement.iam.UserIdentityService;
import com.assetmanagement.security.CurrentUserPrincipal;
import com.assetmanagement.security.CurrentUserProvider;
import com.assetmanagement.shared.exception.BusinessException;
import com.assetmanagement.shared.exception.ErrorCode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class ProfileService {

    private static final int MAX_AVATAR_CHARS = 350_000;
    /** ~300KB binary as data URL (base64 expansion + header). */
    private static final int MAX_ALERT_SOUND_CHARS = 450_000;
    private static final int MAX_DISPLAY_NAME = 120;
    private static final int MAX_SOUND_NAME = 160;

    private final CurrentUserProvider currentUserProvider;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final UserIdentityService userIdentityService;

    public ProfileService(
            CurrentUserProvider currentUserProvider,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            ObjectMapper objectMapper,
            UserIdentityService userIdentityService
    ) {
        this.currentUserProvider = currentUserProvider;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
        this.userIdentityService = userIdentityService;
    }

    @Transactional
    public Map<String, Object> updateProfile(Map<String, Object> body) {
        User user = requireSelf();
        if (body.containsKey("displayName")) {
            String name = stringOrNull(body.get("displayName"));
            if (name == null || name.isBlank()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "displayName is required");
            }
            name = name.trim();
            if (name.length() > MAX_DISPLAY_NAME) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "displayName is too long");
            }
            user.updateDisplayName(name);
        }
        if (body.containsKey("email")) {
            String email = UserIdentityService.normalizeEmail(stringOrNull(body.get("email")));
            userIdentityService.assertEmailAvailable(email, user.getId());
            user.updateEmail(email);
        }
        return profileView(userRepository.save(user));
    }

    @Transactional
    public Map<String, Object> updateAvatar(Map<String, Object> body) {
        User user = requireSelf();
        if (!body.containsKey("avatar")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "avatar is required");
        }
        String avatar = stringOrNull(body.get("avatar"));
        if (avatar == null || avatar.isBlank()) {
            user.updateAvatarUrl(null);
        } else {
            avatar = avatar.trim();
            if (avatar.length() > MAX_AVATAR_CHARS) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "avatar is too large");
            }
            String lower = avatar.toLowerCase(Locale.ROOT);
            if (!(lower.startsWith("data:image/") || lower.startsWith("https://") || lower.startsWith("http://"))) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "avatar must be an image data URL or http(s) URL");
            }
            user.updateAvatarUrl(avatar);
        }
        return profileView(userRepository.save(user));
    }

    @Transactional
    public void changePassword(Map<String, Object> body) {
        User user = requireSelf();
        String currentPassword = requiredString(body, "currentPassword");
        String newPassword = requiredString(body, "newPassword");
        if (newPassword.length() < 8 || newPassword.length() > 128) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "newPassword must be 8-128 characters");
        }
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前密码不正确");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "新密码不能与当前密码相同");
        }
        user.replacePasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Transactional
    public Map<String, Object> updateUiPreferences(Map<String, Object> body) {
        User user = requireSelf();
        boolean popup = body.get("alertPopupEnabled") instanceof Boolean b ? b : true;
        boolean sound = body.get("alertSoundEnabled") instanceof Boolean b ? b : true;
        Map<String, Object> prefs = new LinkedHashMap<>();
        prefs.put("alertPopupEnabled", popup);
        prefs.put("alertSoundEnabled", sound);
        try {
            user.updateUiPreferencesJson(objectMapper.writeValueAsString(prefs));
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to save preferences");
        }
        return profileView(userRepository.save(user));
    }

    @Transactional
    public Map<String, Object> updateAlertSound(Map<String, Object> body) {
        User user = requireSelf();
        if (!body.containsKey("sound")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "sound is required");
        }
        String sound = stringOrNull(body.get("sound"));
        String fileName = stringOrNull(body.get("fileName"));
        if (sound == null || sound.isBlank()) {
            user.updateAlertSound(null, null);
        } else {
            sound = sound.trim();
            if (sound.length() > MAX_ALERT_SOUND_CHARS) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "sound must be <= 300KB");
            }
            if (!isAllowedAlertSoundDataUrl(sound, fileName)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "sound must be mp3 or wma");
            }
            if (fileName != null && fileName.length() > MAX_SOUND_NAME) {
                fileName = fileName.substring(0, MAX_SOUND_NAME);
            }
            if (fileName == null || fileName.isBlank()) {
                fileName = guessSoundFileName(sound);
            }
            user.updateAlertSound(sound, fileName);
        }
        User saved = userRepository.save(user);
        return alertSoundView(saved);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAlertSound() {
        return alertSoundView(requireSelf());
    }

    private Map<String, Object> alertSoundView(User user) {
        Map<String, Object> map = new LinkedHashMap<>();
        boolean has = user.getAlertSoundData() != null && !user.getAlertSoundData().isBlank();
        map.put("hasCustomAlertSound", has);
        map.put("fileName", user.getAlertSoundName());
        map.put("sound", has ? user.getAlertSoundData() : null);
        Map<String, Object> prefs = parsePrefs(user.getUiPreferencesJson());
        map.put("alertPopupEnabled", prefs.get("alertPopupEnabled"));
        map.put("alertSoundEnabled", prefs.get("alertSoundEnabled"));
        return map;
    }

    private static boolean isAllowedAlertSoundDataUrl(String dataUrl, String fileName) {
        String lower = dataUrl.toLowerCase(Locale.ROOT);
        if (lower.startsWith("data:audio/mpeg")
                || lower.startsWith("data:audio/mp3")
                || lower.startsWith("data:audio/x-mpeg")
                || lower.startsWith("data:audio/x-ms-wma")
                || lower.startsWith("data:audio/wma")
                || lower.startsWith("data:audio/x-wma")) {
            return true;
        }
        // Some browsers label wma/mp3 as octet-stream
        if (lower.startsWith("data:application/octet-stream")) {
            String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
            return name.endsWith(".mp3") || name.endsWith(".wma");
        }
        return false;
    }

    private static String guessSoundFileName(String dataUrl) {
        String lower = dataUrl.toLowerCase(Locale.ROOT);
        if (lower.contains("wma")) {
            return "alert.wma";
        }
        return "alert.mp3";
    }

    private User requireSelf() {
        CurrentUserPrincipal principal = currentUserProvider.requireCurrentUser();
        return userRepository.findById(principal.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "User was not found"));
    }

    private Map<String, Object> profileView(User user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("userId", user.getId().toString());
        map.put("userName", user.getUsername());
        map.put("email", user.getEmail());
        map.put("displayName", user.getDisplayName());
        map.put("avatar", user.getAvatarUrl());
        Map<String, Object> prefs = parsePrefs(user.getUiPreferencesJson());
        map.put("alertPopupEnabled", prefs.get("alertPopupEnabled"));
        map.put("alertSoundEnabled", prefs.get("alertSoundEnabled"));
        boolean hasSound = user.getAlertSoundData() != null && !user.getAlertSoundData().isBlank();
        map.put("hasCustomAlertSound", hasSound);
        map.put("alertSoundFileName", user.getAlertSoundName());
        return map;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> parsePrefs(String json) {
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("alertPopupEnabled", true);
        defaults.put("alertSoundEnabled", true);
        if (json == null || json.isBlank()) {
            return defaults;
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> parsed = mapper.readValue(json, Map.class);
            if (parsed.get("alertPopupEnabled") instanceof Boolean b) {
                defaults.put("alertPopupEnabled", b);
            }
            if (parsed.get("alertSoundEnabled") instanceof Boolean b) {
                defaults.put("alertSoundEnabled", b);
            }
        } catch (Exception ignored) {
            // keep defaults
        }
        return defaults;
    }

    private static String requiredString(Map<String, Object> body, String key) {
        String value = stringOrNull(body.get(key));
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, key + " is required");
        }
        return value;
    }

    private static String stringOrNull(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }
}
