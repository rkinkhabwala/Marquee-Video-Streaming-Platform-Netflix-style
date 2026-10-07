package com.marquee.transcoder.ffmpeg;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FfprobeServiceTest {
    @Test
    void parsesDimensionsFrameRateAndDuration() throws Exception {
        String json = """
                {"streams":[{"width":1920,"height":1080,"avg_frame_rate":"30000/1001"}],
                 "format":{"duration":"5.012000"}}
                """;

        assertThat(new FfprobeService().parse(json))
                .isEqualTo(new FfprobeService.ProbeMetadata(1920, 1080, 30, 5));
    }

    @Test
    void fallsBackToTwentyFourFpsForUnknownRate() {
        assertThat(FfprobeService.parseFrameRate("0/0")).isEqualTo(24);
        assertThat(FfprobeService.parseFrameRate("")).isEqualTo(24);
        assertThat(FfprobeService.parseFrameRate("25/1")).isEqualTo(25);
    }
}
