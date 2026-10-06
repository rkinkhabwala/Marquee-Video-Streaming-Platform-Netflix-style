package com.marquee.api.playback;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlaybackTokenService {
    private final String signingSecret;
    private final Duration ttl;

    public PlaybackTokenService(@Value("${app.jwt.stream-secret:${app.jwt.secret}}") String signingSecret,
                                @Value("${app.stream.token-ttl-hours:2}") long ttlHours) {
        this.signingSecret = signingSecret;
        this.ttl = Duration.ofHours(ttlHours);
    }

    public String generate(Long assetId, Long profileId) {
        long exp = Instant.now().plus(ttl).toEpochMilli();
        String payload = assetId + ":" + profileId + ":" + exp;
        return encode(payload) + "." + sign(payload);
    }

    public PlaybackTokenClaims validate(String token) {
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing playback token");
        }

        String[] parts = token.split("\\.");
        if (parts.length != 2) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid playback token");
        }

        String payload = decode(parts[0]);
        String suppliedSignature = parts[1];
        String expectedSignature = sign(payload);
        if (!MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.UTF_8), suppliedSignature.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid playback token");
        }

        String[] values = payload.split(":");
        if (values.length != 3) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid playback token");
        }

        long assetId = Long.parseLong(values[0]);
        long profileId = Long.parseLong(values[1]);
        long expiresAt = Long.parseLong(values[2]);
        if (Instant.ofEpochMilli(expiresAt).isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Playback token expired");
        }

        return new PlaybackTokenClaims(assetId, profileId, Instant.ofEpochMilli(expiresAt));
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] digest = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign playback token", e);
        }
    }

    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
