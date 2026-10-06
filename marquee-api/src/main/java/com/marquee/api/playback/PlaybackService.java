package com.marquee.api.playback;

import com.marquee.api.catalog.Title;
import com.marquee.api.catalog.VideoAsset;
import com.marquee.api.catalog.VideoAssetStatus;
import com.marquee.api.catalog.VideoAssetRepository;
import com.marquee.api.profile.Profile;
import com.marquee.api.profile.ProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlaybackService {
    private final ProfileRepository profileRepository;
    private final VideoAssetRepository videoAssetRepository;
    private final PlaybackTokenService playbackTokenService;

    public PlaybackService(ProfileRepository profileRepository,
                          VideoAssetRepository videoAssetRepository,
                          PlaybackTokenService playbackTokenService) {
        this.profileRepository = profileRepository;
        this.videoAssetRepository = videoAssetRepository;
        this.playbackTokenService = playbackTokenService;
    }

    public PlaybackResponse getPlayback(Long userId, Long profileId, Long assetId) {
        Profile profile = profileRepository.findByIdAndUserId(profileId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found"));

        VideoAsset asset = videoAssetRepository.findById(assetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset not found"));

        if (asset.getStatus() != VideoAssetStatus.READY) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Asset is not ready for playback");
        }

        validateProfileAccess(profile, asset);

        String token = playbackTokenService.generate(assetId, profileId);
        return new PlaybackResponse("/stream/" + assetId + "/master.m3u8?token=" + token, 0, asset.getDurationSeconds() == null ? 0 : asset.getDurationSeconds());
    }

    public PlaybackTokenClaims authorizeStream(Long assetId, String token) {
        PlaybackTokenClaims claims = playbackTokenService.validate(token);
        if (!assetId.equals(claims.assetId())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Playback token does not match asset");
        }

        Profile profile = profileRepository.findById(claims.profileId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Profile not found"));

        VideoAsset asset = videoAssetRepository.findById(assetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset not found"));

        validateProfileAccess(profile, asset);
        return claims;
    }

    private void validateProfileAccess(Profile profile, VideoAsset asset) {
        Title title = asset.getTitle();
        if (title == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset is not associated with a title");
        }

        if (profile.isKids() && (title.getMaturityRating() == null || !title.getMaturityRating().isKidsSafe())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Kids profile cannot play this title");
        }
    }
}
