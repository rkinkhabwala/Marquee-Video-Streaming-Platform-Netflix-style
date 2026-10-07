package com.marquee.transcoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.marquee.common.jobs.TranscodeEvent;
import com.marquee.common.jobs.TranscodeJob;
import com.marquee.common.jobs.TranscodeQueues;
import com.marquee.common.storage.StorageKeys;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

/** Runs the real transcoder against RabbitMQ and MinIO containers using the local ffmpeg. */
@SpringBootTest
@Testcontainers
class TranscodePipelineIT {
    private static final String BUCKET = "marquee";
    private static final long EVENT_TIMEOUT_MS = 120_000;

    @Container
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3-management-alpine");

    @Container
    static MinIOContainer minio = new MinIOContainer(
            DockerImageName.parse("docker.io/pgsty/minio:RELEASE.2026-08-04T00-00-00Z").asCompatibleSubstituteFor("minio/minio"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbit::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbit::getAdminPassword);
        registry.add("app.storage.endpoint", minio::getS3URL);
        registry.add("app.storage.access-key", minio::getUserName);
        registry.add("app.storage.secret-key", minio::getPassword);
        registry.add("app.transcode.retry-delay-ms", () -> 200);
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    private static S3Client s3;

    @BeforeAll
    static void requireFfmpegAndBucket() throws Exception {
        assumeTrue(commandAvailable("ffmpeg") && commandAvailable("ffprobe"), "ffmpeg/ffprobe not on PATH");
        s3 = S3Client.builder()
                .endpointOverride(URI.create(minio.getS3URL()))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(minio.getUserName(), minio.getPassword())))
                .region(Region.US_EAST_1)
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
        s3.createBucket(b -> b.bucket(BUCKET));
    }

    @Test
    void transcodesFiveSecondClipIntoMultiVariantHls(@TempDir Path tmp) throws Exception {
        Path clip = tmp.resolve("clip.mp4");
        exec("ffmpeg", "-y", "-f", "lavfi", "-i", "testsrc=size=854x480:rate=24:duration=5",
                "-f", "lavfi", "-i", "sine=frequency=440:duration=5",
                "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", "-shortest", clip.toString());
        s3.putObject(b -> b.bucket(BUCKET).key(StorageKeys.source(1L)), clip);

        rabbitTemplate.convertAndSend(TranscodeQueues.JOBS, new TranscodeJob(1L, StorageKeys.source(1L), 1));

        assertThat(nextEvent()).isEqualTo(TranscodeEvent.started(1L, 1));
        assertThat(nextEvent()).isEqualTo(TranscodeEvent.succeeded(1L, 1, 5, "hls/1/master.m3u8"));

        String master = s3.getObjectAsBytes(b -> b.bucket(BUCKET).key("hls/1/master.m3u8")).asString(StandardCharsets.UTF_8);
        assertThat(master.lines().filter(line -> line.startsWith("#EXT-X-STREAM-INF")).count()).isEqualTo(2);
        assertThat(master).contains("480p/index.m3u8", "360p/index.m3u8");
        assertThat(keys("hls/1/480p/")).contains("hls/1/480p/index.m3u8", "hls/1/480p/seg_00000.ts");
        assertThat(keys("thumbs/1/")).isNotEmpty();
    }

    @Test
    void retriesThenDeadLettersAJobThatKeepsFailing() {
        rabbitTemplate.convertAndSend(TranscodeQueues.JOBS, new TranscodeJob(2L, "raw/2/missing.mp4", 1));

        for (int attempt = 1; attempt <= 3; attempt++) {
            assertThat(nextEvent()).isEqualTo(TranscodeEvent.started(2L, attempt));
            TranscodeEvent failed = nextEvent();
            assertThat(failed.type()).isEqualTo(TranscodeEvent.Type.FAILED);
            assertThat(failed.attempt()).isEqualTo(attempt);
            assertThat(failed.willRetry()).isEqualTo(attempt < 3);
        }
        TranscodeJob deadLettered = rabbitTemplate.receiveAndConvert(TranscodeQueues.DEAD_LETTER, EVENT_TIMEOUT_MS,
                new ParameterizedTypeReference<TranscodeJob>() { });
        assertThat(deadLettered).isEqualTo(new TranscodeJob(2L, "raw/2/missing.mp4", 3));
    }

    private TranscodeEvent nextEvent() {
        TranscodeEvent event = rabbitTemplate.receiveAndConvert(TranscodeQueues.EVENTS, EVENT_TIMEOUT_MS,
                new ParameterizedTypeReference<TranscodeEvent>() { });
        assertThat(event).as("transcode event").isNotNull();
        return event;
    }

    private static List<String> keys(String prefix) {
        return s3.listObjectsV2(b -> b.bucket(BUCKET).prefix(prefix)).contents().stream().map(o -> o.key()).toList();
    }

    private static boolean commandAvailable(String command) {
        try {
            return new ProcessBuilder(command, "-version").redirectErrorStream(true).start().waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static void exec(String... command) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        assertThat(process.waitFor()).as(output).isZero();
    }
}
