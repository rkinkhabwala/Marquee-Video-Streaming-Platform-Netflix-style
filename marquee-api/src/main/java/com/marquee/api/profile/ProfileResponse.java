package com.marquee.api.profile;

import java.time.Instant;

public record ProfileResponse(
        Long id,
        Long userId,
        String name,
        String avatarKey,
        boolean isKids,
        Instant createdAt) {
    public static ProfileResponse from(Profile profile) {
        return new ProfileResponse(
                profile.getId(),
                profile.getUser().getId(),
                profile.getName(),
                profile.getAvatarKey(),
                profile.isKids(),
                profile.getCreatedAt());
    }
}
