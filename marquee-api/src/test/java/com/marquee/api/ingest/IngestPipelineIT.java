package com.marquee.api.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.marquee.api.IntegrationTestSupport;
import com.marquee.api.catalog.Title;
import com.marquee.api.catalog.TitleRepository;
import com.marquee.api.catalog.TitleType;
import com.marquee.common.jobs.TranscodeEvent;
import com.marquee.common.jobs.TranscodeJob;
import com.marquee.common.jobs.TranscodeQueues;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;

/**
 * API side of upload → READY: presigned upload into MinIO, job published to RabbitMQ, and
 * transcoder events applied to the asset. The transcoder side runs in TranscodePipelineIT.
 */
class IngestPipelineIT extends IntegrationTestSupport {
    @Autowired private IngestService ingestService;
    @Autowired private TitleRepository titleRepository;
    @Autowired private RabbitTemplate rabbitTemplate;

    @Test
    void uploadThroughPresignedUrlThenTranscodeEventsMakeAssetReady() throws Exception {
        Title title = titleRepository.save(new Title(TitleType.MOVIE, "Ingest " + System.nanoTime(), null, 2024, null));
        AssetUploadResponse upload = ingestService.createAsset(new CreateAssetRequest(title.getId()));
        Long assetId = upload.assetId();

        HttpResponse<String> put = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(upload.uploadUrl()))
                .header("Content-Type", upload.contentType())
                .PUT(HttpRequest.BodyPublishers.ofByteArray(new byte[] {0, 0, 0, 24, 'f', 't', 'y', 'p'}))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(put.statusCode()).as(put.body()).isEqualTo(200);

        assertThat(ingestService.completeAsset(assetId).status()).isEqualTo("TRANSCODING");
        TranscodeJob job = rabbitTemplate.receiveAndConvert(TranscodeQueues.JOBS, 10_000, new ParameterizedTypeReference<TranscodeJob>() { });
        assertThat(job).isEqualTo(new TranscodeJob(assetId, "raw/" + assetId + "/source.mp4", 1));

        rabbitTemplate.convertAndSend(TranscodeQueues.EVENTS, TranscodeEvent.started(assetId, 1));
        rabbitTemplate.convertAndSend(TranscodeQueues.EVENTS, TranscodeEvent.failed(assetId, 1, "ffmpeg exited with 1", true));
        rabbitTemplate.convertAndSend(TranscodeQueues.EVENTS, TranscodeEvent.started(assetId, 2));
        rabbitTemplate.convertAndSend(TranscodeQueues.EVENTS, TranscodeEvent.succeeded(assetId, 2, 5, "hls/" + assetId + "/master.m3u8"));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            AssetStatusResponse asset = ingestService.getAsset(assetId);
            assertThat(asset.status()).isEqualTo("READY");
            assertThat(asset.durationSeconds()).isEqualTo(5);
            assertThat(asset.masterPlaylistKey()).isEqualTo("hls/" + assetId + "/master.m3u8");
            assertThat(asset.attempts()).extracting(AssetStatusResponse.Attempt::status).containsExactly("FAILED", "SUCCEEDED");
        });
        assertThat(ingestService.listAssetsForTitle(title.getId()))
                .extracting(AssetStatusResponse::assetId, AssetStatusResponse::status)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(assetId, "READY"));
    }

    @Test
    void completeFailsUntilSourceIsUploaded() {
        Title title = titleRepository.save(new Title(TitleType.MOVIE, "Missing " + System.nanoTime(), null, 2024, null));
        Long assetId = ingestService.createAsset(new CreateAssetRequest(title.getId())).assetId();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> ingestService.completeAsset(assetId))
                .hasMessageContaining("not yet available");
        assertThat(ingestService.getAsset(assetId).status()).isEqualTo("UPLOADED");
    }
}
