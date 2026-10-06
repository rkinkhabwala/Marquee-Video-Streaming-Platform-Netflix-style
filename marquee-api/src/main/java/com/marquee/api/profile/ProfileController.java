package com.marquee.api.profile;

import com.marquee.api.security.UserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ProfileController {
    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/profiles")
    public List<ProfileResponse> getProfiles(@AuthenticationPrincipal UserPrincipal principal) {
        return profileService.getProfilesForUser(principal.getId());
    }

    @PostMapping("/profiles")
    public ResponseEntity<ProfileResponse> createProfile(@AuthenticationPrincipal UserPrincipal principal,
                                                       @Valid @RequestBody CreateProfileRequest request) {
        ProfileResponse response = profileService.createProfile(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/profiles/{id}")
    public ProfileResponse getProfile(@AuthenticationPrincipal UserPrincipal principal,
                                     @PathVariable Long id) {
        return profileService.getProfile(principal.getId(), id);
    }

    @PutMapping("/profiles/{id}")
    public ProfileResponse updateProfile(@AuthenticationPrincipal UserPrincipal principal,
                                        @Valid @RequestBody UpdateProfileRequest request,
                                        @PathVariable Long id) {
        return profileService.updateProfile(principal.getId(), id, request);
    }

    @DeleteMapping("/profiles/{id}")
    public ResponseEntity<Void> deleteProfile(@AuthenticationPrincipal UserPrincipal principal,
                                             @PathVariable Long id) {
        profileService.deleteProfile(principal.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
