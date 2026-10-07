package com.marquee.api.ingest;

import com.marquee.api.catalog.VideoAsset;
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

/** One row in {@code transcode_jobs} per transcoder attempt. */
@Entity
@Table(name = "transcode_jobs")
public class TranscodeAttempt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_asset_id", nullable = false)
    private VideoAsset videoAsset;

    @Column(nullable = false)
    private int attempt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TranscodeStatus status;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "log_tail", columnDefinition = "TEXT")
    private String logTail;

    protected TranscodeAttempt() {
    }

    public TranscodeAttempt(VideoAsset videoAsset, int attempt) {
        this.videoAsset = videoAsset;
        this.attempt = attempt;
        this.status = TranscodeStatus.RUNNING;
        this.startedAt = Instant.now();
    }

    public void finish(TranscodeStatus status, String logTail) {
        this.status = status;
        this.logTail = logTail;
        this.finishedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public int getAttempt() {
        return attempt;
    }

    public TranscodeStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public String getLogTail() {
        return logTail;
    }
}
