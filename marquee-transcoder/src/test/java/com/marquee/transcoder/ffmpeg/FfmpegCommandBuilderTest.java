package com.marquee.transcoder.ffmpeg;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class FfmpegCommandBuilderTest {
    private final FfmpegCommandBuilder builder = new FfmpegCommandBuilder();

    @Test
    void buildsRenditionCommandWithExactArguments() {
        List<String> cmd = builder.buildRenditionCommand("/tmp/input.mp4", "/tmp/out/720p", Rendition.P720, 30);

        assertThat(cmd).containsExactly(
                "ffmpeg", "-y",
                "-i", "/tmp/input.mp4",
                "-vf", "scale=w=1280:h=720:force_original_aspect_ratio=decrease,pad=1280:720:(ow-iw)/2:(oh-ih)/2",
                "-c:v", "libx264",
                "-preset", "veryfast",
                "-pix_fmt", "yuv420p",
                "-b:v", "2800k",
                "-maxrate", "2800k",
                "-bufsize", "5600k",
                "-r", "30",
                "-g", "60",
                "-keyint_min", "60",
                "-sc_threshold", "0",
                "-c:a", "aac",
                "-ar", "48000",
                "-b:a", "128k",
                "-f", "hls",
                "-hls_time", "6",
                "-hls_playlist_type", "vod",
                "-hls_segment_filename", "/tmp/out/720p/seg_%05d.ts",
                "/tmp/out/720p/index.m3u8");
    }

    @Test
    void defaultsToTwentyFourFpsWhenSourceRateUnknown() {
        List<String> cmd = builder.buildRenditionCommand("in.mp4", "out", Rendition.P360, 0);

        assertThat(cmd).containsSubsequence("-r", "24", "-g", "48", "-keyint_min", "48");
    }

    @Test
    void buildsThumbnailCommandEveryTenSeconds() {
        assertThat(builder.buildThumbnailCommand("/tmp/input.mp4", "/tmp/thumbs")).containsExactly(
                "ffmpeg", "-y",
                "-i", "/tmp/input.mp4",
                "-vf", "fps=1/10,scale=320:-2",
                "-q:v", "5",
                "/tmp/thumbs/thumb_%d.jpg");
    }

    @Test
    void buildsMasterPlaylistWithRelativeVariantUris() {
        assertThat(builder.buildMasterPlaylist(List.of(Rendition.P720, Rendition.P360))).containsExactly(
                "#EXTM3U",
                "#EXT-X-VERSION:3",
                "#EXT-X-STREAM-INF:BANDWIDTH=2928000,RESOLUTION=1280x720",
                "720p/index.m3u8",
                "#EXT-X-STREAM-INF:BANDWIDTH=896000,RESOLUTION=640x360",
                "360p/index.m3u8");
    }

    @Test
    void skipsRungsAboveSourceHeight() {
        assertThat(builder.selectRenditions(1080)).containsExactly(Rendition.P1080, Rendition.P720, Rendition.P480, Rendition.P360);
        assertThat(builder.selectRenditions(720)).containsExactly(Rendition.P720, Rendition.P480, Rendition.P360);
        assertThat(builder.selectRenditions(480)).containsExactly(Rendition.P480, Rendition.P360);
        assertThat(builder.selectRenditions(240)).containsExactly(Rendition.P360);
    }
}
