package com.marquee.api.storage;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Service
public class MinioStorageService implements ObjectStorageService {
    private final S3Presigner presigner;
    private final String bucketName;

    public MinioStorageService(@Value("${app.storage.endpoint}") String endpoint,
                              @Value("${app.storage.bucket}") String bucketName,
                              @Value("${app.storage.access-key}") String accessKey,
                              @Value("${app.storage.secret-key}") String secretKey,
                              @Value("${app.storage.region}") String region) {
        this.bucketName = bucketName;
        this.presigner = S3Presigner.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .region(Region.of(region))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    @Override
    public PresignedUploadResponse presignImageUpload(ImageUploadRequest request) {
        String kind = request.kind().trim().toLowerCase(Locale.ROOT);
        if (!"poster".equals(kind) && !"backdrop".equals(kind)) {
            throw new IllegalArgumentException("kind must be poster or backdrop");
        }

        String safeFileName = request.fileName() == null ? "image.jpg" : request.fileName().trim();
        if (safeFileName.isEmpty()) {
            throw new IllegalArgumentException("fileName is required");
        }

        String objectKey = "images/" + (request.titleId() != null ? request.titleId() : "upload") + "/" + kind + "/" + safeFileName;
        String contentType = detectContentType(safeFileName);

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .contentType(contentType)
                .build();

        return new PresignedUploadResponse(
                objectKey,
                presigner.presignPutObject(builder -> builder
                        .signatureDuration(Duration.ofMinutes(10))
                        .putObjectRequest(putObjectRequest))
                        .url()
                        .toString(),
                contentType);
    }

    private String detectContentType(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }
}
