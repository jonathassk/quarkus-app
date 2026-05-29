package org.example.infrastructure.storage;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

@Slf4j
@ApplicationScoped
public class ObjectStorageService {

    @ConfigProperty(name = "r2.bucket")
    Optional<String> bucket;

    @ConfigProperty(name = "r2.endpoint")
    Optional<String> endpointOverride;

    @ConfigProperty(name = "r2.access-key-id")
    Optional<String> accessKeyId;

    @ConfigProperty(name = "r2.secret-access-key")
    Optional<String> secretAccessKey;

    @ConfigProperty(name = "documents.presign.upload.minutes", defaultValue = "15")
    int uploadPresignMinutes;

    @ConfigProperty(name = "documents.presign.view.minutes", defaultValue = "30")
    int viewPresignMinutes;

    private S3Presigner presigner;
    private S3Client s3Client;
    private String bucketName;

    @PostConstruct
    void init() {
        bucketName = bucket.map(ObjectStorageService::normalizeConfigValue).filter(b -> !b.isBlank()).orElse(null);
        String endpoint = endpointOverride.map(ObjectStorageService::normalizeConfigValue).orElse(null);
        String keyId = accessKeyId.map(ObjectStorageService::normalizeConfigValue).orElse(null);
        String secret = secretAccessKey.map(ObjectStorageService::normalizeConfigValue).orElse(null);

        if (bucketName == null || endpoint == null || keyId == null || secret == null) {
            log.error(
                    "Object storage (R2) not fully configured — document upload/view unavailable. "
                            + "Set R2_BUCKET_NAME, R2_ENDPOINT, R2_ACCESS_KEY_ID, R2_SECRET_ACCESS_KEY on the API.");
            return;
        }

        String normalizedEndpoint = normalizeEndpoint(endpoint);

        S3Configuration s3Config = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .chunkedEncodingEnabled(false)
                .build();

        try {
            var credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(keyId, secret));
            presigner = S3Presigner.builder()
                    .region(Region.US_EAST_1)
                    .endpointOverride(URI.create(normalizedEndpoint))
                    .credentialsProvider(credentials)
                    .serviceConfiguration(s3Config)
                    .build();
            s3Client = S3Client.builder()
                    .region(Region.US_EAST_1)
                    .endpointOverride(URI.create(normalizedEndpoint))
                    .credentialsProvider(credentials)
                    .serviceConfiguration(s3Config)
                    .build();
            log.info("R2 storage ready bucket={} endpoint={}", bucketName, normalizedEndpoint);
        } catch (Exception e) {
            log.error("Failed to initialize R2 storage endpoint={}", normalizedEndpoint, e);
            presigner = null;
            s3Client = null;
        }
    }

    /** Strips spaces/newlines accidentally pasted into Lambda env vars. */
    private static String normalizeConfigValue(String value) {
        if (value == null) {
            return null;
        }
        return value.strip();
    }

    private static String normalizeEndpoint(String endpoint) {
        String e = endpoint.trim();
        while (e.endsWith("/")) {
            e = e.substring(0, e.length() - 1);
        }
        if (!e.startsWith("http://") && !e.startsWith("https://")) {
            e = "https://" + e;
        }
        return e;
    }

    public boolean isConfigured() {
        return presigner != null && s3Client != null && bucketName != null;
    }

    /** Server-side upload (no browser CORS to R2). */
    public void putObject(String s3Key, byte[] body, String contentType) {
        ensureConfigured();
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .contentType(contentType)
                .contentLength((long) body.length)
                .build();
        try {
            s3Client.putObject(request, RequestBody.fromBytes(body));
        } catch (Exception e) {
            log.error("R2 putObject failed key={} bucket={} bytes={}", s3Key, bucketName, body.length, e);
            throw new IllegalStateException("Failed to store document: " + e.getMessage(), e);
        }
    }

    public String getBucketName() {
        return bucketName;
    }

    public int getUploadPresignSeconds() {
        return uploadPresignMinutes * 60;
    }

    public int getViewPresignSeconds() {
        return viewPresignMinutes * 60;
    }

    public String presignPut(String s3Key, String contentType) {
        ensureConfigured();
        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(uploadPresignMinutes))
                .putObjectRequest(objectRequest)
                .build();

        try {
            PresignedPutObjectRequest presigned = presigner.presignPutObject(presignRequest);
            return presigned.url().toString();
        } catch (Exception e) {
            log.error("Presign PUT failed key={} bucket={} contentType={}", s3Key, bucketName, contentType, e);
            throw new IllegalStateException("Failed to sign upload URL: " + e.getMessage(), e);
        }
    }

    /** Remove object from R2 (best-effort when cleaning up trip documents). */
    public void deleteObject(String s3Key) {
        ensureConfigured();
        if (s3Key == null || s3Key.isBlank()) {
            return;
        }
        try {
            s3Client.deleteObject(
                    DeleteObjectRequest.builder().bucket(bucketName).key(s3Key).build());
        } catch (Exception e) {
            log.error("R2 deleteObject failed key={} bucket={}", s3Key, bucketName, e);
            throw new IllegalStateException("Failed to delete document from storage: " + e.getMessage(), e);
        }
    }

    public String presignGet(String s3Key) {
        ensureConfigured();
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(viewPresignMinutes))
                .getObjectRequest(getRequest)
                .build();

        try {
            PresignedGetObjectRequest presigned = presigner.presignGetObject(presignRequest);
            return presigned.url().toString();
        } catch (Exception e) {
            log.error("Presign GET failed key={} bucket={}", s3Key, bucketName, e);
            throw new IllegalStateException("Failed to sign view URL: " + e.getMessage(), e);
        }
    }

    private void ensureConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException("Object storage is not configured");
        }
    }
}
