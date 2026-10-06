package com.marquee.api.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "video_assets")
public class VideoAsset {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "title_id")
    private Title title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VideoAssetStatus status = VideoAssetStatus.UPLOADED;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "source_key")
    private String sourceKey;

    @Column(name = "master_playlist_key")
    private String masterPlaylistKey;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected VideoAsset() {
    }

    public VideoAsset(Title title, VideoAssetStatus status) {
        this.title = title;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public Title getTitle() {
        return title;
    }

    public void setTitle(Title title) {
        this.title = title;
    }

    public VideoAssetStatus getStatus() {
        return status;
    }

    public void setStatus(VideoAssetStatus status) {
        this.status = status;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public String getSourceKey() {
        return sourceKey;
    }

    public void setSourceKey(String sourceKey) {
        this.sourceKey = sourceKey;
    }

    public String getMasterPlaylistKey() {
        return masterPlaylistKey;
    }

    public void setMasterPlaylistKey(String masterPlaylistKey) {
        this.masterPlaylistKey = masterPlaylistKey;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
