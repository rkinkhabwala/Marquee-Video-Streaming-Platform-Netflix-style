package com.marquee.transcoder.service;

import com.marquee.transcoder.ffmpeg.FfmpegCommandBuilder;
import com.marquee.transcoder.ffmpeg.FfprobeService;
import com.marquee.transcoder.ffmpeg.Rendition;
import com.marquee.transcoder.storage.TranscodeStorageService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TranscodeService {
    private final FfmpegCommandBuilder ffmpegCommandBuilder;
    private final FfprobeService ffprobeService;
    private final TranscodeStorageService transcodeStorageService;
    private final TranscodeStatusCallback transcodeStatusCallback;

    public TranscodeService(FfmpegCommandBuilder ffmpegCommandBuilder,
                           FfprobeService ffprobeService,
                           TranscodeStorageService transcodeStorageService,
                           TranscodeStatusCallback transcodeStatusCallback) {
        this.ffmpegCommandBuilder = ffmpegCommandBuilder;
        this.ffprobeService = ffprobeService;
        this.transcodeStorageService = transcodeStorageService;
        this.transcodeStatusCallback = transcodeStatusCallback;
    }

    public void transcode(TranscodeJobMessage job) {
        if (job == null || job.assetId() == null || job.sourceKey() == null) {
            throw new IllegalArgumentException("Transcode job must include assetId and sourceKey");
        }

        Path tempDir = createTempDir(job.assetId());
        try {
            Path sourceFile = tempDir.resolve("source.mp4");
            Files.copy(transcodeStorageService.download(job.sourceKey()), sourceFile);

            FfprobeService.ProbeMetadata metadata = ffprobeService.probe(sourceFile.toString());
            List<Rendition> renditions = selectRenditions(metadata.height());
            for (Rendition rendition : renditions) {
                List<String> command = ffmpegCommandBuilder.buildRenditionCommand(
                        sourceFile.toString(),
                        tempDir.resolve(rendition.width() + "p").toString(),
                        rendition,
                        metadata.fps());
                runProcess(command);
                transcodeStorageService.uploadDirectory(tempDir.resolve(rendition.width() + "p"),
                        "hls/%s/%sp/".formatted(job.assetId(), rendition.width()));
            }

            String masterPlaylist = String.join(System.lineSeparator(), ffmpegCommandBuilder.buildMasterPlaylist(
                    "hls/%s".formatted(job.assetId()), renditions));
            Path masterFile = tempDir.resolve("master.m3u8");
            Files.writeString(masterFile, masterPlaylist);
            transcodeStorageService.uploadFile(masterFile, "hls/%s/master.m3u8".formatted(job.assetId()));
            transcodeStatusCallback.onSuccess(job.assetId(), "hls/%s/master.m3u8".formatted(job.assetId()));
        } catch (Exception e) {
            transcodeStatusCallback.onFailure(job.assetId(), e.getMessage());
            throw new IllegalStateException("Transcode failed for asset " + job.assetId(), e);
        } finally {
            deleteIfExists(tempDir);
        }
    }

    private List<Rendition> selectRenditions(int sourceHeight) {
        List<Rendition> renditions = new ArrayList<>();
        if (sourceHeight >= 1080) {
            renditions.add(Rendition.P1080);
        }
        if (sourceHeight >= 720) {
            renditions.add(Rendition.P720);
        }
        if (sourceHeight >= 480) {
            renditions.add(Rendition.P480);
        }
        if (sourceHeight >= 360) {
            renditions.add(Rendition.P360);
        }
        if (renditions.isEmpty()) {
            renditions.add(Rendition.P360);
        }
        return renditions;
    }

    private Path createTempDir(Long assetId) {
        try {
            return Files.createTempDirectory("marquee-transcode-" + assetId);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create temp directory for transcode", e);
        }
    }

    private void runProcess(List<String> command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException("ffmpeg failed: " + output);
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Unable to run ffmpeg command: " + command, e);
        }
    }

    private void deleteIfExists(Path path) {
        try {
            Files.walk(path)
                    .sorted((left, right) -> right.compareTo(left))
                    .forEach(file -> {
                        try {
                            Files.deleteIfExists(file);
                        } catch (IOException ignored) {
                            // no-op
                        }
                    });
        } catch (IOException ignored) {
            // no-op
        }
    }
}
