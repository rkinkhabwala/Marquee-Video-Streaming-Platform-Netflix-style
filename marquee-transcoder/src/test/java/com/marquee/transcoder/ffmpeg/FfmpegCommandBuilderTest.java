package com.marquee.transcoder.ffmpeg;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class FfmpegCommandBuilderTest {

    @Test
    void buildsRenditionCommandWithExpectedArguments() {
        FfmpegCommandBuilder builder = new FfmpegCommandBuilder();

        List<String> cmd = builder.buildRenditionCommand("/tmp/input.mp4", "/tmp/output", Rendition.P720, 24);

        assertThat(cmd).containsSubsequence(
                "ffmpeg",
                "-y",
                "-i",
                "/tmp/input.mp4",
                "-vf",
                "scale=w=1280:h=720:force_original_aspect_ratio=decrease,pad=1280:720:(ow-iw)/2:(oh-ih)/2",
                "-c:v",
                "libx264",
                "-preset",
                "veryfast",
                "-crf",
                "23",
                "-g",
                "48",
                "-keyint_min",
                "48",
                "-sc_threshold",
                "0",
                "-r",
                "24",
                "-c:a",
                "aac",
                "-b:a",
                "128k",
                "-b:v",
                "2800k",
                "-hls_time",
                "6",
                "-hls_playlist_type",
                "vod",
                "-hls_segment_filename",
                "/tmp/output/seg_%03d.ts",
                "/tmp/output/index.m3u8");
    }

    @Test
    void buildsMasterPlaylistWithVariants() {
        FfmpegCommandBuilder builder = new FfmpegCommandBuilder();

        List<String> m3u8 = builder.buildMasterPlaylist("hls/asset-1", List.of(Rendition.P360, Rendition.P720));

        assertThat(m3u8).contains(
                "#EXTM3U",
                "#EXT-X-VERSION:3",
                "#EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360",
                "hls/asset-1/640p/index.m3u8",
                "#EXT-X-STREAM-INF:BANDWIDTH=2800000,RESOLUTION=1280x720",
                "hls/asset-1/1280p/index.m3u8");
    }
}
