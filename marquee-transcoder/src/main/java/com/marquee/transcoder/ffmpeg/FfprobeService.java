package com.marquee.transcoder.ffmpeg;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class FfprobeService {
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ProbeMetadata probe(String inputPath) {
        List<String> command = List.of(
                "ffprobe",
                "-v",
                "error",
                "-select_streams",
                "v:0",
                "-show_entries",
                "stream=width,height,avg_frame_rate:format=duration",
                "-of",
                "json",
                inputPath);

        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes());
            int exit = process.waitFor();
            if (exit != 0) {
                throw new IllegalStateException("ffprobe failed for %s: %s".formatted(inputPath, output));
            }
            return parse(output);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while probing " + inputPath, e);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to probe input video: " + inputPath, e);
        }
    }

    ProbeMetadata parse(String json) throws IOException {
        JsonNode root = objectMapper.readTree(json);
        JsonNode stream = root.path("streams").path(0);
        if (stream.isMissingNode()) {
            throw new IllegalStateException("Source has no video stream");
        }
        double duration = root.path("format").path("duration").asDouble(0);
        return new ProbeMetadata(
                stream.path("width").asInt(),
                stream.path("height").asInt(),
                parseFrameRate(stream.path("avg_frame_rate").asText("")),
                (int) Math.round(duration));
    }

    /** {@code avg_frame_rate} is a fraction such as {@code 30000/1001}; rounds to whole fps, default 24. */
    static int parseFrameRate(String fraction) {
        String[] parts = fraction.split("/");
        try {
            double numerator = Double.parseDouble(parts[0]);
            double denominator = parts.length > 1 ? Double.parseDouble(parts[1]) : 1;
            if (numerator > 0 && denominator > 0) {
                return (int) Math.round(numerator / denominator);
            }
        } catch (NumberFormatException ignored) {
            // fall through to default
        }
        return 24;
    }

    public record ProbeMetadata(int width, int height, int fps, int durationSeconds) {
    }
}
