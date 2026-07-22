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
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;

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

    @ConfigProperty(name = "r2.public-url-prefix")
    Optional<String> publicUrlPrefix;

    private volatile S3Presigner presigner;
    private volatile S3Client s3Client;
    private String bucketName;

    // Stored for lazy client creation (SnapStart-safe: no network in @PostConstruct)
    private String configuredEndpoint;
    private String configuredKeyId;
    private String configuredSecret;

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

        // Store config only — do NOT create S3Client/S3Presigner here.
        // AWS SDK client construction may trigger network I/O (endpoint resolution, IMDS)
        // which fails during SnapStart snapshot creation. Clients are built lazily on first use.
        this.configuredEndpoint = normalizeEndpoint(endpoint);
        this.configuredKeyId = keyId;
        this.configuredSecret = secret;
        log.info("R2 storage config ready (clients will be built lazily on first use — SnapStart-safe). bucket={} endpoint={}",
                bucketName, this.configuredEndpoint);
    }

    /** Lazily builds and caches S3Client + S3Presigner on first use (after SnapStart restore). */
    private synchronized void getOrInitClients() {
        if (s3Client != null && presigner != null) {
            return;
        }
        if (configuredEndpoint == null) {
            return; // Not configured
        }
        try {
            // NOTE: the real TLS 1.2 pin (to avoid JVM TLS 1.3 handshake_failure with Cloudflare
            // R2) must be set via JAVA_TOOL_OPTIONS (-Dhttps.protocols=TLSv1.2
            // -Djdk.tls.client.protocols=TLSv1.2) at JVM startup — see pom.xml sam.jvm.yaml patch.
            // The JDK caches these properties the first time any SSL/TLS class is touched
            // (e.g. an earlier HTTPS call for JWKS during the same invocation), so setting them
            // here via System.setProperty is too late to have any effect; kept only as a
            // best-effort fallback for local/dev runs that don't go through JAVA_TOOL_OPTIONS.
            System.setProperty("https.protocols", "TLSv1.2");
            System.setProperty("jdk.tls.client.protocols", "TLSv1.2");
            log.info("R2 lazy-initializing S3Client. TLS: https.protocols={}, jdk.tls.client.protocols={}",
                    System.getProperty("https.protocols"),
                    System.getProperty("jdk.tls.client.protocols"));

            var credentials = StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(configuredKeyId, configuredSecret));
            S3Configuration s3Config = S3Configuration.builder()
                    .pathStyleAccessEnabled(true)
                    .chunkedEncodingEnabled(false)
                    .build();
            presigner = S3Presigner.builder()
                    .region(Region.US_EAST_1)
                    .endpointOverride(URI.create(configuredEndpoint))
                    .credentialsProvider(credentials)
                    .serviceConfiguration(s3Config)
                    .build();
            s3Client = S3Client.builder()
                    .region(Region.US_EAST_1)
                    .endpointOverride(URI.create(configuredEndpoint))
                    .credentialsProvider(credentials)
                    .serviceConfiguration(s3Config)
                    .httpClientBuilder(UrlConnectionHttpClient.builder())
                    .build();
            log.info("R2 storage ready bucket={} endpoint={}", bucketName, configuredEndpoint);
        } catch (Throwable t) {
            log.error("Failed to lazy-initialize R2 storage endpoint={}", configuredEndpoint, t);
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
        return bucketName != null && configuredEndpoint != null;
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

    public String getPublicUrl(String s3Key) {
        if (s3Key == null) {
            return null;
        }
        String prefix = publicUrlPrefix.map(ObjectStorageService::normalizeConfigValue).orElse("");
        if (prefix.isBlank()) {
            return s3Key;
        }
        if (!prefix.endsWith("/")) {
            prefix += "/";
        }
        return prefix + s3Key;
    }

    private void ensureConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException("Object storage is not configured");
        }
        getOrInitClients();
        if (s3Client == null || presigner == null) {
            throw new IllegalStateException("Object storage failed to initialize — check R2 credentials and endpoint");
        }
    }
}
