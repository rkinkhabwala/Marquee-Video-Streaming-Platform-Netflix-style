package com.marquee.api.progress;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class WatchProgressId implements Serializable {
    private Long profileId;
    private Long videoAssetId;

    public WatchProgressId() {
    }

    public WatchProgressId(Long profileId, Long videoAssetId) {
        this.profileId = profileId;
        this.videoAssetId = videoAssetId;
    }

    public Long getProfileId() {
        return profileId;
    }

    public void setProfileId(Long profileId) {
        this.profileId = profileId;
    }

    public Long getVideoAssetId() {
        return videoAssetId;
    }

    public void setVideoAssetId(Long videoAssetId) {
        this.videoAssetId = videoAssetId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        WatchProgressId that = (WatchProgressId) o;
        return Objects.equals(profileId, that.profileId) && Objects.equals(videoAssetId, that.videoAssetId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(profileId, videoAssetId);
    }
}
