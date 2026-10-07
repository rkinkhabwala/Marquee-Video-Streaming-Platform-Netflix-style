package com.marquee.api.profile;

import com.marquee.api.recsys.EngagementRecorder;
import com.marquee.api.user.User;
import com.marquee.api.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ProfileService {
    private static final int MAX_PROFILES_PER_USER = 5;

    private final ProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final EngagementRecorder engagement;

    public ProfileService(ProfileRepository profileRepository, UserRepository userRepository, EngagementRecorder engagement) {
        this.profileRepository = profileRepository;
        this.userRepository = userRepository;
        this.engagement = engagement;
    }

    @Transactional(readOnly = true)
    public List<ProfileResponse> getProfilesForUser(Long userId) {
        return profileRepository.findByUserIdOrderByCreatedAtAsc(userId)
                .stream()
                .map(ProfileResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(Long userId, Long profileId) {
        Profile profile = profileRepository.findByIdAndUserId(profileId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found"));
        return ProfileResponse.from(profile);
    }

    @Transactional
    public ProfileResponse createProfile(Long userId, CreateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (profileRepository.countByUserId(userId) >= MAX_PROFILES_PER_USER) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Profile limit reached");
        }

        String trimmedName = request.name() == null ? "" : request.name().trim();
        if (trimmedName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Profile name is required");
        }

        Profile profile = new Profile(user, trimmedName, request.avatarKey(), request.isKids());
        Profile savedProfile = profileRepository.save(profile);
        return ProfileResponse.from(savedProfile);
    }

    @Transactional
    public ProfileResponse updateProfile(Long userId, Long profileId, UpdateProfileRequest request) {
        Profile profile = profileRepository.findByIdAndUserId(profileId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found"));

        String trimmedName = request.name() == null ? profile.getName() : request.name().trim();
        if (trimmedName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Profile name is required");
        }

        profile.setName(trimmedName);
        if (request.avatarKey() != null) {
            profile.setAvatarKey(request.avatarKey());
        }
        if (request.isKids() != null) {
            profile.setKids(request.isKids());
        }

        return ProfileResponse.from(profileRepository.save(profile));
    }

    @Transactional
    public void deleteProfile(Long userId, Long profileId) {
        Profile profile = profileRepository.findByIdAndUserId(profileId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found"));
        profileRepository.delete(profile);
        engagement.profileDeleted(profileId);
    }
}
