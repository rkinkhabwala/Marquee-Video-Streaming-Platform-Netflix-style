package com.marquee.api.progress;

import com.marquee.api.catalog.VideoAsset;
import com.marquee.api.profile.Profile;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "watch_progress")
public class WatchProgress {
    @EmbeddedId
    private WatchProgressId id;

    @MapsId("profileId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    private Profile profile;

    @MapsId("videoAssetId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_asset_id", nullable = false)
    private VideoAsset videoAsset;

    @Column(name = "position_seconds", nullable = false)
    private Integer positionSeconds = 0;

    @Column(name = "duration_seconds", nullable = false)
    private Integer durationSeconds = 0;

    @Column(nullable = false)
    private boolean completed = false;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected WatchProgress() {
    }

    public WatchProgress(Profile profile, VideoAsset videoAsset) {
        this.profile = profile;
        this.videoAsset = videoAsset;
        this.id = new WatchProgressId(profile.getId(), videoAsset.getId());
    }

    public WatchProgressId getId() {
        return id;
    }

    public Profile getProfile() {
        return profile;
    }

    public void setProfile(Profile profile) {
        this.profile = profile;
    }

    public VideoAsset getVideoAsset() {
        return videoAsset;
    }

    public void setVideoAsset(VideoAsset videoAsset) {
        this.videoAsset = videoAsset;
    }

    public Integer getPositionSeconds() {
        return positionSeconds;
    }

    public void setPositionSeconds(Integer positionSeconds) {
        this.positionSeconds = positionSeconds;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
