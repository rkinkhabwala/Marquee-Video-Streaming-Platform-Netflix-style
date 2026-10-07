package com.marquee.api.ingest;

import com.marquee.api.catalog.Title;
import com.marquee.api.catalog.TitleRepository;
import com.marquee.api.catalog.VideoAsset;
import com.marquee.api.catalog.VideoAssetRepository;
import com.marquee.api.catalog.VideoAssetStatus;
import com.marquee.api.storage.ObjectStorageService;
import com.marquee.api.storage.PresignedUploadResponse;
import com.marquee.common.jobs.TranscodeEvent;
import com.marquee.common.jobs.TranscodeJob;
import com.marquee.common.storage.StorageKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IngestService {
    private static final Logger log = LoggerFactory.getLogger(IngestService.class);

    private final VideoAssetRepository videoAssetRepository;
    private final TitleRepository titleRepository;
    private final TranscodeAttemptRepository attemptRepository;
    private final ObjectStorageService objectStorageService;
    private final TranscodeJobPublisher transcodeJobPublisher;

    public IngestService(VideoAssetRepository videoAssetRepository,
                        TitleRepository titleRepository,
                        TranscodeAttemptRepository attemptRepository,
                        ObjectStorageService objectStorageService,
                        TranscodeJobPublisher transcodeJobPublisher) {
        this.videoAssetRepository = videoAssetRepository;
        this.titleRepository = titleRepository;
        this.attemptRepository = attemptRepository;
        this.objectStorageService = objectStorageService;
        this.transcodeJobPublisher = transcodeJobPublisher;
    }

    @Transactional
    public AssetUploadResponse createAsset(CreateAssetRequest request) {
        if (request == null || request.titleId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Title id is required");
        }

        Title title = titleRepository.findById(request.titleId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Title not found"));

        VideoAsset saved = videoAssetRepository.save(new VideoAsset(title, VideoAssetStatus.UPLOADED));
        String sourceKey = StorageKeys.source(saved.getId());
        saved.setSourceKey(sourceKey);
        saved = videoAssetRepository.save(saved);

        PresignedUploadResponse upload = objectStorageService.presignUpload(sourceKey, "video/mp4");
        return new AssetUploadResponse(saved.getId(), title.getId(), sourceKey, upload.uploadUrl(), upload.contentType(), saved.getStatus().name());
    }

    @Transactional
    public AssetStatusResponse completeAsset(Long assetId) {
        VideoAsset asset = findAsset(assetId);

        if (asset.getStatus() == VideoAssetStatus.TRANSCODING || asset.getStatus() == VideoAssetStatus.READY) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Asset is already " + asset.getStatus());
        }
        if (asset.getSourceKey() == null || asset.getSourceKey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Asset source key is missing");
        }
        if (!objectStorageService.objectExists(asset.getSourceKey())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uploaded source is not yet available in object storage");
        }

        asset.setStatus(VideoAssetStatus.TRANSCODING);
        asset.setErrorMessage(null);
        videoAssetRepository.save(asset);

        publishAfterCommit(new TranscodeJob(assetId, asset.getSourceKey(), 1));
        return toResponse(asset);
    }

    @Transactional(readOnly = true)
    public AssetStatusResponse getAsset(Long assetId) {
        return toResponse(findAsset(assetId));
    }

    @Transactional(readOnly = true)
    public List<AssetStatusResponse> listAssetsForTitle(Long titleId) {
        if (!titleRepository.existsById(titleId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Title not found");
        }
        return videoAssetRepository.findByTitle_IdOrderByCreatedAtDesc(titleId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public void applyTranscodeEvent(TranscodeEvent event) {
        VideoAsset asset = videoAssetRepository.findById(event.assetId()).orElse(null);
        if (asset == null) {
            log.warn("Ignoring transcode event for unknown asset {}", event.assetId());
            return;
        }

        switch (event.type()) {
            case STARTED -> attemptRepository.save(new TranscodeAttempt(asset, event.attempt()));
            case SUCCEEDED -> {
                attempt(asset, event.attempt()).finish(TranscodeStatus.SUCCEEDED, null);
                asset.setStatus(VideoAssetStatus.READY);
                asset.setMasterPlaylistKey(event.masterPlaylistKey());
                asset.setErrorMessage(null);
                if (event.durationSeconds() != null) {
                    asset.setDurationSeconds(event.durationSeconds());
                }
            }
            case FAILED -> {
                attempt(asset, event.attempt()).finish(TranscodeStatus.FAILED, event.error());
                if (!event.willRetry() && asset.getStatus() != VideoAssetStatus.READY) {
                    asset.setStatus(VideoAssetStatus.FAILED);
                    asset.setErrorMessage(event.error());
                }
            }
        }
    }

    private TranscodeAttempt attempt(VideoAsset asset, int attempt) {
        return attemptRepository.findFirstByVideoAsset_IdAndAttemptOrderByIdDesc(asset.getId(), attempt)
                .orElseGet(() -> attemptRepository.save(new TranscodeAttempt(asset, attempt)));
    }

    private void publishAfterCommit(TranscodeJob job) {
        // Publishing before commit could let the transcoder report back before the TRANSCODING update is visible.
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            transcodeJobPublisher.publish(job);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                transcodeJobPublisher.publish(job);
            }
        });
    }

    private VideoAsset findAsset(Long assetId) {
        return videoAssetRepository.findById(assetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset not found"));
    }

    private AssetStatusResponse toResponse(VideoAsset asset) {
        return new AssetStatusResponse(
                asset.getId(),
                asset.getTitle() == null ? null : asset.getTitle().getId(),
                asset.getStatus().name(),
                asset.getSourceKey(),
                asset.getMasterPlaylistKey(),
                asset.getDurationSeconds(),
                asset.getErrorMessage(),
                asset.getId() == null ? List.of() : attemptRepository.findByVideoAsset_IdOrderByIdAsc(asset.getId()).stream()
                        .map(a -> new AssetStatusResponse.Attempt(a.getAttempt(), a.getStatus().name(), a.getStartedAt(), a.getFinishedAt(), a.getLogTail()))
                        .toList());
    }
}
