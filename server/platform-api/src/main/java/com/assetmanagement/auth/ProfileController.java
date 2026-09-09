package com.assetmanagement.auth;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/me")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @PutMapping("/profile")
    public Map<String, Object> updateProfile(@RequestBody Map<String, Object> body) {
        return profileService.updateProfile(body);
    }

    @PutMapping("/avatar")
    public Map<String, Object> updateAvatar(@RequestBody Map<String, Object> body) {
        return profileService.updateAvatar(body);
    }

    @PutMapping("/password")
    public void changePassword(@RequestBody Map<String, Object> body) {
        profileService.changePassword(body);
    }

    @PutMapping("/ui-preferences")
    public Map<String, Object> updateUiPreferences(@RequestBody Map<String, Object> body) {
        return profileService.updateUiPreferences(body);
    }

    @GetMapping("/alert-sound")
    public Map<String, Object> getAlertSound() {
        return profileService.getAlertSound();
    }

    @PutMapping("/alert-sound")
    public Map<String, Object> updateAlertSound(@RequestBody Map<String, Object> body) {
        return profileService.updateAlertSound(body);
    }
}
