package com.marquee.api.ingest;

import com.marquee.api.catalog.Title;
import com.marquee.api.catalog.TitleRepository;
import com.marquee.api.catalog.VideoAsset;
import com.marquee.api.catalog.VideoAssetRepository;
import com.marquee.api.catalog.VideoAssetStatus;
import com.marquee.api.storage.ObjectStorageService;
import com.marquee.api.storage.PresignedUploadResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IngestService {
    private final VideoAssetRepository videoAssetRepository;
    private final TitleRepository titleRepository;
    private final ObjectStorageService objectStorageService;
    private final TranscodeJobPublisher transcodeJobPublisher;

    public IngestService(VideoAssetRepository videoAssetRepository,
                        TitleRepository titleRepository,
                        ObjectStorageService objectStorageService,
                        TranscodeJobPublisher transcodeJobPublisher) {
        this.videoAssetRepository = videoAssetRepository;
        this.titleRepository = titleRepository;
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

        VideoAsset asset = new VideoAsset(title, VideoAssetStatus.UPLOADED);
        VideoAsset saved = videoAssetRepository.save(asset);
        String sourceKey = "raw/" + saved.getId() + "/source.mp4";
        saved.setSourceKey(sourceKey);
        saved = videoAssetRepository.save(saved);

        PresignedUploadResponse upload = objectStorageService.presignUpload(sourceKey, "video/mp4");
        return new AssetUploadResponse(saved.getId(), title.getId(), sourceKey, upload.uploadUrl(), upload.contentType(), saved.getStatus().name());
    }

    @Transactional
    public AssetStatusResponse completeAsset(Long assetId) {
        VideoAsset asset = videoAssetRepository.findById(assetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset not found"));

        if (asset.getSourceKey() == null || asset.getSourceKey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Asset source key is missing");
        }

        if (!objectStorageService.objectExists(asset.getSourceKey())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uploaded source is not yet available in object storage");
        }

        asset.setStatus(VideoAssetStatus.TRANSCODING);
        videoAssetRepository.save(asset);

        transcodeJobPublisher.publish(new TranscodeJobMessage(assetId, asset.getSourceKey()));
        return new AssetStatusResponse(asset.getId(), asset.getTitle() == null ? null : asset.getTitle().getId(), asset.getStatus().name(), asset.getSourceKey());
    }

    @Transactional(readOnly = true)
    public AssetStatusResponse getAsset(Long assetId) {
        VideoAsset asset = videoAssetRepository.findById(assetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset not found"));
        return new AssetStatusResponse(asset.getId(), asset.getTitle() == null ? null : asset.getTitle().getId(), asset.getStatus().name(), asset.getSourceKey());
    }
}
