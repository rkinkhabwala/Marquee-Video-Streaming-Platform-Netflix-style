package com.marquee.api.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.marquee.api.catalog.Title;
import com.marquee.api.catalog.TitleRepository;
import com.marquee.api.catalog.TitleType;
import com.marquee.api.catalog.VideoAsset;
import com.marquee.api.catalog.VideoAssetRepository;
import com.marquee.api.catalog.VideoAssetStatus;
import com.marquee.api.storage.ObjectStorageService;
import com.marquee.api.storage.PresignedUploadResponse;
import java.lang.reflect.Field;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IngestServiceTest {

    @Mock
    private VideoAssetRepository videoAssetRepository;

    @Mock
    private TitleRepository titleRepository;

    @Mock
    private ObjectStorageService objectStorageService;

    @Mock
    private TranscodeJobPublisher transcodeJobPublisher;

    @InjectMocks
    private IngestService ingestService;

    @Test
    void createAssetGeneratesPresignedUploadUrl() throws Exception {
        Title title = new Title(TitleType.MOVIE, "Night Shift", "Summary", 2025, null);
        setId(title, 10L);
        when(titleRepository.findById(10L)).thenReturn(Optional.of(title));

        VideoAsset savedAsset = new VideoAsset(title, VideoAssetStatus.UPLOADED);
        setId(savedAsset, 99L);
        when(videoAssetRepository.save(any(VideoAsset.class))).thenAnswer(invocation -> {
            VideoAsset asset = invocation.getArgument(0);
            if (asset.getId() == null) {
                setId(asset, 99L);
            }
            return asset;
        });
        when(objectStorageService.presignUpload("raw/99/source.mp4", "video/mp4"))
                .thenReturn(new PresignedUploadResponse("raw/99/source.mp4", "https://example.test/upload", "video/mp4"));

        AssetUploadResponse response = ingestService.createAsset(new CreateAssetRequest(10L));

        assertThat(response.assetId()).isEqualTo(99L);
        assertThat(response.sourceKey()).isEqualTo("raw/99/source.mp4");
        assertThat(response.uploadUrl()).contains("example.test");
        verify(transcodeJobPublisher, never()).publish(any());
    }

    @Test
    void completeAssetMarksTranscodingAndPublishesJob() throws Exception {
        Title title = new Title(TitleType.SERIES, "Example", "Synopsis", 2025, null);
        setId(title, 4L);

        VideoAsset asset = new VideoAsset(title, VideoAssetStatus.UPLOADED);
        setId(asset, 21L);
        asset.setSourceKey("raw/21/source.mp4");

        when(videoAssetRepository.findById(21L)).thenReturn(Optional.of(asset));
        when(objectStorageService.objectExists("raw/21/source.mp4")).thenReturn(true);
        when(videoAssetRepository.save(asset)).thenReturn(asset);

        AssetStatusResponse response = ingestService.completeAsset(21L);

        assertThat(response.status()).isEqualTo(VideoAssetStatus.TRANSCODING.name());
        verify(transcodeJobPublisher).publish(any(TranscodeJobMessage.class));
    }

    private static void setId(Object target, Long id) throws Exception {
        Field field = target.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, id);
    }
}
