package com.marquee.api.playback;

import com.marquee.api.security.UserPrincipal;
import com.marquee.api.storage.ObjectStorageService;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

@Controller
public class PlaybackController {
    private final PlaybackService playbackService;
    private final ObjectStorageService objectStorageService;

    public PlaybackController(PlaybackService playbackService, ObjectStorageService objectStorageService) {
        this.playbackService = playbackService;
        this.objectStorageService = objectStorageService;
    }

    @GetMapping("/api/playback/{assetId}")
    @ResponseBody
    public PlaybackResponse getPlayback(@AuthenticationPrincipal UserPrincipal principal,
                                       @RequestHeader(value = "X-Profile-Id", required = false) Long profileId,
                                       @PathVariable Long assetId) {
        if (profileId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Profile-Id header is required");
        }
        return playbackService.getPlayback(principal.getId(), profileId, assetId);
    }

    @GetMapping("/stream/{assetId}/{path:.+}")
    public ResponseEntity<StreamingResponseBody> stream(@PathVariable Long assetId,
                                                      @PathVariable String path,
                                                      @RequestParam String token,
                                                      @RequestHeader(value = "Range", required = false) String rangeHeader) {
        playbackService.authorizeStream(assetId, token);
        String sanitizedPath = sanitizePath(path);
        String objectKey = "hls/" + assetId + "/" + sanitizedPath;

        if (sanitizedPath.toLowerCase(Locale.ROOT).endsWith(".m3u8")) {
            byte[] bytes = objectStorageService.getObjectBytes(objectKey, rangeHeader);
            String manifest = new String(bytes, StandardCharsets.UTF_8);
            String rewritten = rewriteManifest(manifest, assetId, token);
            byte[] output = rewritten.getBytes(StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/vnd.apple.mpegurl"))
                    .cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS))
                    .header("Accept-Ranges", "bytes")
                    .body(outputStream -> outputStream.write(output));
        }

        ResponseInputStream<GetObjectResponse> stream = objectStorageService.getObject(objectKey, rangeHeader);
        String contentType = inferContentType(sanitizedPath, stream.response().contentType());
        HttpStatus status = rangeHeader != null && !rangeHeader.isBlank() ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK;
        return ResponseEntity.status(status)
                .contentType(MediaType.parseMediaType(contentType))
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).mustRevalidate())
                .header("Accept-Ranges", "bytes")
                .body(outputStream -> {
                    try (ResponseInputStream<GetObjectResponse> inputStream = stream) {
                        inputStream.transferTo(outputStream);
                    }
                });
    }

    private String sanitizePath(String path) {
        String sanitized = path.replace('\\', '/');
        if (sanitized.contains("..")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid stream path");
        }
        if (sanitized.startsWith("/")) {
            sanitized = sanitized.substring(1);
        }
        return sanitized;
    }

    private String rewriteManifest(String playlist, Long assetId, String token) {
        StringBuilder rewritten = new StringBuilder();
        for (String line : playlist.split("\\R")) {
            if (line.isBlank() || line.startsWith("#")) {
                rewritten.append(line).append(System.lineSeparator());
                continue;
            }
            String cleaned = line.trim();
            String suffix = cleaned.contains("?") ? "&token=" : "?token=";
            rewritten.append("/stream/").append(assetId).append('/').append(cleaned).append(suffix).append(token).append(System.lineSeparator());
        }
        return rewritten.toString();
    }

    private String inferContentType(String path, String fallback) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".m3u8")) {
            return "application/vnd.apple.mpegurl";
        }
        if (lower.endsWith(".ts")) {
            return "video/mp2t";
        }
        return fallback == null ? "application/octet-stream" : fallback;
    }
}
