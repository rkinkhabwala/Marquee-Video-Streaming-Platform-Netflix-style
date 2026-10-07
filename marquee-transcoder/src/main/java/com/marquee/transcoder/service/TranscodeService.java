package com.marquee.transcoder.service;

import com.marquee.common.jobs.TranscodeJob;
import com.marquee.common.storage.StorageKeys;
import com.marquee.transcoder.ffmpeg.FfmpegCommandBuilder;
import com.marquee.transcoder.ffmpeg.FfprobeService;
import com.marquee.transcoder.ffmpeg.Rendition;
import com.marquee.transcoder.storage.TranscodeStorageService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TranscodeService {
    private static final Logger log = LoggerFactory.getLogger(TranscodeService.class);

    private final FfmpegCommandBuilder ffmpegCommandBuilder;
    private final FfprobeService ffprobeService;
    private final TranscodeStorageService storage;

    public TranscodeService(FfmpegCommandBuilder ffmpegCommandBuilder,
                            FfprobeService ffprobeService,
                            TranscodeStorageService storage) {
        this.ffmpegCommandBuilder = ffmpegCommandBuilder;
        this.ffprobeService = ffprobeService;
        this.storage = storage;
    }

    /** {@code durationSeconds} is null when the job was skipped because output already exists. */
    public record Result(String masterPlaylistKey, Integer durationSeconds) {
    }

    public Result transcode(TranscodeJob job) throws IOException {
        String masterKey = StorageKeys.masterPlaylist(job.assetId());
        // The master playlist is uploaded last, so its presence means a previous run finished.
        if (storage.exists(masterKey)) {
            log.info("Asset {} already has {}, skipping", job.assetId(), masterKey);
            return new Result(masterKey, null);
        }

        Path workDir = Files.createTempDirectory("marquee-transcode-" + job.assetId());
        try {
            Path source = workDir.resolve("source.mp4");
            try (InputStream in = storage.download(job.sourceKey())) {
                Files.copy(in, source);
            }

            FfprobeService.ProbeMetadata metadata = ffprobeService.probe(source.toString());
            List<Rendition> renditions = ffmpegCommandBuilder.selectRenditions(metadata.height());
            for (Rendition rendition : renditions) {
                Path outputDir = Files.createDirectories(workDir.resolve(rendition.name()));
                run(ffmpegCommandBuilder.buildRenditionCommand(source.toString(), outputDir.toString(), rendition, metadata.fps()));
                storage.uploadDirectory(outputDir, StorageKeys.hlsPrefix(job.assetId()) + rendition.name() + "/");
            }

            Path thumbsDir = Files.createDirectories(workDir.resolve("thumbs"));
            run(ffmpegCommandBuilder.buildThumbnailCommand(source.toString(), thumbsDir.toString()));
            storage.uploadDirectory(thumbsDir, StorageKeys.thumbnailsPrefix(job.assetId()));

            Path master = workDir.resolve("master.m3u8");
            Files.write(master, ffmpegCommandBuilder.buildMasterPlaylist(renditions));
            storage.uploadFile(master, masterKey);
            return new Result(masterKey, metadata.durationSeconds());
        } finally {
            deleteRecursively(workDir);
        }
    }

    private void run(List<String> command) throws IOException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException("ffmpeg exited with " + exitCode + ": " + output);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while running ffmpeg", e);
        }
    }

    private void deleteRecursively(Path path) {
        try (Stream<Path> files = Files.walk(path)) {
            files.sorted(Comparator.reverseOrder()).forEach(file -> file.toFile().delete());
        } catch (IOException e) {
            log.warn("Unable to clean up {}", path, e);
        }
    }
}
