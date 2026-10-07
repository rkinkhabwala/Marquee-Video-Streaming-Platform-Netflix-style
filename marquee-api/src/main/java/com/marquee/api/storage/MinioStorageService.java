package com.marquee.api.storage;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Service
public class MinioStorageService implements ObjectStorageService {
    private final S3Presigner presigner;
    private final S3Client s3Client;
    private final String bucketName;

    public MinioStorageService(@Value("${app.storage.endpoint}") String endpoint,
                              @Value("${app.storage.bucket}") String bucketName,
                              @Value("${app.storage.access-key}") String accessKey,
                              @Value("${app.storage.secret-key}") String secretKey,
                              @Value("${app.storage.region}") String region,
                              @Value("${app.storage.public-endpoint:${app.storage.endpoint}}") String publicEndpoint) {
        this.bucketName = bucketName;
        // Presigned URLs are used by clients outside Docker, so they must be signed for a host those clients can reach.
        this.presigner = S3Presigner.builder()
                .endpointOverride(URI.create(publicEndpoint))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .region(Region.of(region))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
        this.s3Client = S3Client.builder()
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

        String contentType = detectContentType(safeFileName);
        // Spec layout: images/{titleId}/poster.jpg | backdrop.jpg (extension follows the upload type).
        String objectKey = "images/" + request.titleId() + "/" + kind + "." + extensionFor(contentType);

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

    @Override
    public PresignedUploadResponse presignUpload(String objectKey, String contentType) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .contentType(contentType)
                .build();

        return new PresignedUploadResponse(
                objectKey,
                presigner.presignPutObject(builder -> builder
                        .signatureDuration(Duration.ofMinutes(15))
                        .putObjectRequest(putObjectRequest))
                        .url()
                        .toString(),
                contentType);
    }

    @Override
    public boolean objectExists(String objectKey) {
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(bucketName).key(objectKey).build());
            return true;
        } catch (SdkException e) {
            return false;
        }
    }

    @Override
    public ResponseInputStream<GetObjectResponse> getObject(String objectKey, String rangeHeader) {
        GetObjectRequest.Builder builder = GetObjectRequest.builder().bucket(bucketName).key(objectKey);
        if (rangeHeader != null && !rangeHeader.isBlank()) {
            builder.range(rangeHeader);
        }
        return s3Client.getObject(builder.build());
    }

    @Override
    public byte[] getObjectBytes(String objectKey, String rangeHeader) {
        try (ResponseInputStream<GetObjectResponse> stream = getObject(objectKey, rangeHeader)) {
            return stream.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read object bytes for " + objectKey, e);
        }
    }

    private static String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
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
