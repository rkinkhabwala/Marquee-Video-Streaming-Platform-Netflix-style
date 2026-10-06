package com.marquee.transcoder.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
public class MinioTranscodeStorageService implements TranscodeStorageService {
    private final S3Client s3Client;
    private final String bucketName;

    public MinioTranscodeStorageService(@Value("${app.storage.endpoint}") String endpoint,
                                       @Value("${app.storage.bucket}") String bucketName,
                                       @Value("${app.storage.access-key}") String accessKey,
                                       @Value("${app.storage.secret-key}") String secretKey,
                                       @Value("${app.storage.region}") String region) {
        this.bucketName = bucketName;
        this.s3Client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .region(Region.of(region))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    @Override
    public InputStream download(String objectKey) throws IOException {
        return s3Client.getObject(GetObjectRequest.builder().bucket(bucketName).key(objectKey).build());
    }

    @Override
    public void uploadDirectory(Path directory, String destinationPrefix) throws IOException {
        try (Stream<Path> files = Files.walk(directory)) {
            files.filter(Files::isRegularFile).forEach(file -> {
                String relative = directory.relativize(file).toString().replace('\\', '/');
                String key = destinationPrefix + relative;
                try {
                    uploadFile(file, key);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

    @Override
    public void uploadFile(Path sourceFile, String destinationKey) throws IOException {
        try (InputStream inputStream = Files.newInputStream(sourceFile)) {
            s3Client.putObject(PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(destinationKey)
                    .contentType(guessContentType(destinationKey))
                    .build(), RequestBody.fromInputStream(inputStream, Files.size(sourceFile)));
        }
    }

    private String guessContentType(String destinationKey) {
        if (destinationKey.endsWith(".m3u8")) {
            return "application/vnd.apple.mpegurl";
        }
        if (destinationKey.endsWith(".ts")) {
            return "video/mp2t";
        }
        return "application/octet-stream";
    }
}
