package com.polaroid.storage;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "r2")
@Slf4j
public class R2StorageService implements StorageService {
    private static final int DEFAULT_SIGNED_URL_EXPIRATION_SECONDS = 300;

    @Value("${app.storage.r2.account-id}")
    private String accountId;

    @Value("${app.storage.r2.api-token}")
    private String apiToken;

    @Value("${app.storage.r2.bucket}")
    private String bucketName;

    @Value("${app.storage.r2.s3-endpoint:}")
    private String s3Endpoint;

    @Value("${app.storage.r2.region:auto}")
    private String region;

    @Value("${app.storage.r2.access-key:}")
    private String accessKey;

    @Value("${app.storage.r2.secret-key:}")
    private String secretKey;

    @Value("${app.storage.r2.path-style:true}")
    private boolean pathStyleAccess;

    private final RestTemplate restTemplate = new RestTemplate();

    private String apiBase;
    private S3Presigner presigner;

    @PostConstruct
    public void init() {
        apiBase = "https://api.cloudflare.com/client/v4/accounts/" + accountId + "/r2/buckets/" + bucketName;
        initPresigner();
        log.info("R2StorageService initialized for account: {}", accountId);
    }

    @PreDestroy
    public void destroy() {
        if (presigner != null) {
            presigner.close();
        }
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiToken);
        return headers;
    }

    @Override
    public String upload(String key, byte[] data, String contentType) {
        try {
            URI uri = objectsUri(key);
            HttpHeaders headers = authHeaders();
            headers.setContentType(MediaType.parseMediaType(contentType));

            HttpEntity<byte[]> entity = new HttpEntity<>(data, headers);
            ResponseEntity<Map> response = restTemplate.exchange(uri, HttpMethod.PUT, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.debug("Uploaded: {}", key);
                return key;
            }
            throw new RuntimeException("Upload failed: " + response.getStatusCode());
        } catch (Exception e) {
            log.error("Failed to upload {}: {}", key, e.getMessage());
            throw new RuntimeException("Failed to upload file to R2", e);
        }
    }

    @Override
    public byte[] download(String key) {
        try {
            URI uri = objectsUri(key);
            HttpHeaders headers = authHeaders();
            HttpEntity<?> entity = new HttpEntity<>(headers);
            ResponseEntity<byte[]> response = restTemplate.exchange(uri, HttpMethod.GET, entity, byte[].class);
            return response.getBody() != null ? response.getBody() : new byte[0];
        } catch (Exception e) {
            log.warn("Failed to download {}: {}", key, e.getMessage());
            return new byte[0];
        }
    }

    @Override
    public void delete(String key) {
        try {
            URI uri = objectsUri(key);
            HttpHeaders headers = authHeaders();
            HttpEntity<?> entity = new HttpEntity<>(headers);
            restTemplate.exchange(uri, HttpMethod.DELETE, entity, Map.class);
            log.debug("Deleted: {}", key);
        } catch (Exception e) {
            log.error("Failed to delete {}: {}", key, e.getMessage());
            throw new RuntimeException("Failed to delete file from R2", e);
        }
    }

    @Override
    public List<StorageFileInfo> listFiles(String prefix) {
        List<StorageFileInfo> files = new ArrayList<>();
        try {
            URI uri = URI.create(apiBase + "/objects?prefix=" + prefix.replace("/", "%2F"));
            HttpHeaders headers = authHeaders();
            HttpEntity<?> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(uri, HttpMethod.GET, entity, Map.class);

            if (response.getBody() != null && response.getBody().containsKey("result")) {
                List<Map<String, Object>> objects = extractObjects(response.getBody().get("result"));
                for (Map<String, Object> obj : objects) {
                    if (!(obj.get("key") instanceof String objectKey) || objectKey.isBlank()) {
                        continue;
                    }
                    String name = objectKey.substring(objectKey.lastIndexOf('/') + 1);
                    files.add(new StorageFileInfo(name, objectKey, getSignedUrl(objectKey, DEFAULT_SIGNED_URL_EXPIRATION_SECONDS)));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to list files for {}: {}", prefix, e.getMessage());
        }
        return files;
    }

    @Override
    public String getSignedUrl(String key, int expirationSeconds) {
        if (presigner == null) {
            throw new IllegalStateException("R2 signed URL credentials are not configured");
        }

        try {
            var presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofSeconds(expirationSeconds))
                    .getObjectRequest(request -> request.bucket(bucketName).key(key))
                    .build();
            return presigner.presignGetObject(presignRequest).url().toString();
        } catch (Exception e) {
            log.warn("Failed to create signed R2 URL for {}: {}", key, e.getMessage());
            throw new RuntimeException("Failed to create signed R2 URL", e);
        }
    }

    @Override
    public long getStorageUsage() {
        try {
            URI uri = URI.create(apiBase + "/objects?limit=1");
            HttpHeaders headers = authHeaders();
            HttpEntity<?> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(uri, HttpMethod.GET, entity, Map.class);

            if (response.getBody() != null && response.getBody().containsKey("result")) {
                Map<String, Object> result = (Map<String, Object>) response.getBody().get("result");
                if (result != null && result.containsKey("count")) {
                    return ((Number) result.get("count")).longValue();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get storage usage: {}", e.getMessage());
        }
        return 0;
    }

    @Override
    public boolean isAvailable() {
        try {
            URI uri = URI.create(apiBase);
            HttpHeaders headers = authHeaders();
            HttpEntity<?> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(uri, HttpMethod.GET, entity, Map.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("R2 storage unavailable: {}", e.getMessage());
            return false;
        }
    }

    private URI objectsUri(String key) {
        return URI.create(apiBase + "/objects/" + key.replace("/", "%2F"));
    }

    private void initPresigner() {
        if (accessKey == null || accessKey.isBlank() || secretKey == null || secretKey.isBlank()) {
            log.info("R2 S3 credentials are not configured; signed object URLs are disabled");
            return;
        }

        String endpoint = s3Endpoint != null && !s3Endpoint.isBlank()
                ? s3Endpoint
                : "https://" + accountId + ".r2.cloudflarestorage.com";

        var credentialsProvider = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey));
        var s3Config = S3Configuration.builder()
                .pathStyleAccessEnabled(pathStyleAccess)
                .build();

        presigner = S3Presigner.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider)
                .serviceConfiguration(s3Config)
                .build();
    }

    private List<Map<String, Object>> extractObjects(Object result) {
        if (result instanceof List<?>) {
            return mapEntries((List<?>) result);
        }
        if (result instanceof Map<?, ?> resultMap && resultMap.get("objects") instanceof List<?>) {
            return mapEntries((List<?>) resultMap.get("objects"));
        }
        return List.of();
    }

    private List<Map<String, Object>> mapEntries(List<?> entries) {
        List<Map<String, Object>> objects = new ArrayList<>();
        for (Object entry : entries) {
            if (entry instanceof Map<?, ?> map) {
                Map<String, Object> object = new java.util.HashMap<>();
                for (Map.Entry<?, ?> mapEntry : map.entrySet()) {
                    if (mapEntry.getKey() instanceof String key) {
                        object.put(key, mapEntry.getValue());
                    }
                }
                objects.add(object);
            }
        }
        return objects;
    }

    private String getObjectUrl(String key) {
        return apiBase + "/objects/" + key.replace("/", "%2F");
    }
}
