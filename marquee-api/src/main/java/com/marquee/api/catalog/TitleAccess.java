package com.marquee.api.catalog;

import com.marquee.api.profile.Profile;
import com.marquee.api.profile.ProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Profile ownership and title visibility checks shared by the viewer-facing services. */
@Component
public class TitleAccess {
    private final ProfileRepository profileRepository;
    private final TitleRepository titleRepository;

    public TitleAccess(ProfileRepository profileRepository, TitleRepository titleRepository) {
        this.profileRepository = profileRepository;
        this.titleRepository = titleRepository;
    }

    public Profile requireProfile(Long userId, Long profileId) {
        if (profileId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Profile-Id header is required");
        }
        return profileRepository.findByIdAndUserId(profileId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found"));
    }

    /** 404 for missing or unpublished titles; 403 when a kids profile may not see the title. */
    public Title requireVisibleTitle(Profile profile, Long titleId) {
        Title title = titleRepository.findById(titleId)
                .filter(Title::isPublished)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Title not found"));
        if (profile.isKids() && (title.getMaturityRating() == null || !title.getMaturityRating().isKidsSafe())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Title is not available for kids profiles");
        }
        return title;
    }
}
