package com.polaroid.storage;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "s3")
@Slf4j
public class S3StorageService implements StorageService {

    @Value("${app.storage.s3.endpoint}")
    private String endpoint;

    @Value("${app.storage.s3.region:auto}")
    private String region;

    @Value("${app.storage.s3.access-key}")
    private String accessKey;

    @Value("${app.storage.s3.secret-key}")
    private String secretKey;

    @Value("${app.storage.s3.bucket}")
    private String bucketName;

    @Value("${app.storage.s3.path-style:false}")
    private boolean pathStyleAccess;

    private S3Client s3Client;
    private S3Presigner presigner;

    @PostConstruct
    public void init() {
        var credentials = new AwsCredentials() {
            @Override
            public String accessKeyId() { return accessKey; }
            @Override
            public String secretAccessKey() { return secretKey; }
        };

        var provider = new AwsCredentialsProvider() {
            @Override
            public AwsCredentials resolveCredentials() { return credentials; }
        };

        var s3Config = S3Configuration.builder()
                .pathStyleAccessEnabled(pathStyleAccess)
                .build();

        s3Client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(provider)
                .serviceConfiguration(s3Config)
                .build();

        presigner = S3Presigner.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(provider)
                .build();

        log.info("S3StorageService initialized with endpoint: {}", endpoint);
    }

    @PreDestroy
    public void destroy() {
        if (s3Client != null) s3Client.close();
        if (presigner != null) presigner.close();
    }

    @Override
    public String upload(String key, byte[] data, String contentType) {
        try {
            s3Client.putObject(PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(key)
                            .contentType(contentType)
                            .build(),
                    RequestBody.fromBytes(data));
            return key;
        } catch (Exception e) {
            log.error("Failed to upload {}: {}", key, e.getMessage());
            throw new RuntimeException("Failed to upload file to S3", e);
        }
    }

    @Override
    public byte[] download(String key) {
        try {
            var response = s3Client.getObject(GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build());
            return response.readAllBytes();
        } catch (NoSuchKeyException e) {
            log.warn("File not found: {}", key);
            return new byte[0];
        } catch (Exception e) {
            log.error("Failed to download {}: {}", key, e.getMessage());
            return new byte[0];
        }
    }

    @Override
    public void delete(String key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build());
            log.info("Deleted: {}", key);
        } catch (Exception e) {
            log.error("Failed to delete {}: {}", key, e.getMessage());
            throw new RuntimeException("Failed to delete file from S3", e);
        }
    }

    @Override
    public List<StorageFileInfo> listFiles(String prefix) {
        List<StorageFileInfo> files = new ArrayList<>();
        try {
            var response = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .prefix(prefix)
                    .build());

            for (S3Object obj : response.contents()) {
                String key = obj.key();
                String name = key.substring(key.lastIndexOf('/') + 1);
                String url = getSignedUrl(key, 3600);
                files.add(new StorageFileInfo(name, key, url));
            }
        } catch (Exception e) {
            log.warn("Failed to list files for {}: {}", prefix, e.getMessage());
        }
        return files;
    }

    @Override
    public String getSignedUrl(String key, int expirationSeconds) {
        try {
            var presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofSeconds(expirationSeconds))
                    .getObjectRequest(ro -> ro.bucket(bucketName).key(key))
                    .build();
            return presigner.presignGetObject(presignRequest).url().toString();
        } catch (Exception e) {
            log.error("Failed to create signed URL for {}: {}", key, e.getMessage());
            throw new RuntimeException("Failed to create signed URL", e);
        }
    }

    @Override
    public long getStorageUsage() {
        try {
            var response = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .build());
            return response.keyCount();
        } catch (Exception e) {
            log.warn("Failed to get storage usage: {}", e.getMessage());
            return 0;
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder()
                    .bucket(bucketName)
                    .build());
            return true;
        } catch (Exception e) {
            log.warn("S3 storage unavailable: {}", e.getMessage());
            return false;
        }
    }
}
