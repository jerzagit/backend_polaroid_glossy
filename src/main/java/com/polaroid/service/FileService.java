package com.polaroid.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileService {
    
    private final WebClient supabaseWebClient;
    private final RestTemplate restTemplate;
    
    @Value("${supabase.url:https://placeholder.supabase.co}")
    private String supabaseUrl;
    
    @Value("${supabase.key:placeholder-key}")
    private String supabaseKey;
    
    @Value("${supabase.storage-bucket:polaroid-glossy}")
    private String bucketName;
    
    public Map<String, String> uploadFile(MultipartFile file, String orderId) throws IOException {
        String fileName = UUID.randomUUID().toString() + ".jpg";
        String folder = "original/" + orderId;
        String key = folder + "/" + fileName;
        
        try {
            String uploadUrl = String.format("%s/storage/v1/object/%s/%s", supabaseUrl, bucketName, key);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_JPEG);
            headers.set("Authorization", "Bearer " + supabaseKey);
            headers.set("x-upsert", "true");
            
            HttpEntity<byte[]> requestEntity = new HttpEntity<>(file.getBytes(), headers);
            
            restTemplate.exchange(uploadUrl, HttpMethod.PUT, requestEntity, String.class);
            
            String publicUrl = getPublicUrl(key);
            
            Map<String, String> result = new HashMap<>();
            result.put("key", key);
            result.put("url", publicUrl);
            result.put("fileName", fileName);
            
            return result;
        } catch (Exception e) {
            log.error("Failed to upload file: {}", e.getMessage());
            throw new IOException("Failed to upload file: " + e.getMessage());
        }
    }
    
    public void deleteFile(String key) throws IOException {
        try {
            String deleteUrl = String.format("%s/storage/v1/object/%s/%s", supabaseUrl, bucketName, key);
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + supabaseKey);
            
            HttpEntity<?> requestEntity = new HttpEntity<>(headers);
            
            restTemplate.exchange(deleteUrl, HttpMethod.DELETE, requestEntity, String.class);
        } catch (Exception e) {
            log.error("Failed to delete file: {}", e.getMessage());
            throw new IOException("Failed to delete file: " + e.getMessage());
        }
    }
    
    public byte[] downloadFiles(List<String> keys) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        for (String key : keys) {
            try {
                String downloadUrl = String.format("%s/storage/v1/object/%s/%s", supabaseUrl, bucketName, key);
                
                HttpHeaders headers = new HttpHeaders();
                headers.set("Authorization", "Bearer " + supabaseKey);
                
                HttpEntity<?> requestEntity = new HttpEntity<>(headers);
                
                ResponseEntity<byte[]> response = restTemplate.exchange(
                    downloadUrl, HttpMethod.GET, requestEntity, byte[].class);
                
                if (response.getBody() != null) {
                    outputStream.write(response.getBody());
                    outputStream.write(System.lineSeparator().getBytes());
                }
            } catch (Exception e) {
                log.warn("Failed to download file {}: {}", key, e.getMessage());
            }
        }
        
        return outputStream.toByteArray();
    }
    
    public List<Map<String, String>> listFiles(String orderId) {
        String folder = "original/" + orderId;
        List<Map<String, String>> files = new ArrayList<>();
        
        try {
            String listUrl = String.format("%s/storage/v1/object/list/%s/%s", supabaseUrl, bucketName, folder);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + supabaseKey);
            
            Map<String, Object> body = new HashMap<>();
            body.put("limit", 100);
            body.put("offset", 0);
            
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> response = restTemplate.postForObject(listUrl, requestEntity, List.class);
            
            if (response != null) {
                for (Map<String, Object> item : response) {
                    Map<String, String> fileInfo = new HashMap<>();
                    fileInfo.put("name", (String) item.get("name"));
                    fileInfo.put("key", folder + "/" + item.get("name"));
                    fileInfo.put("url", getPublicUrl(folder + "/" + item.get("name")));
                    files.add(fileInfo);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to list files for {}: {}", folder, e.getMessage());
        }
        
        return files;
    }
    
    public String getPublicUrl(String key) {
        return String.format("%s/storage/v1/object/public/%s/%s", supabaseUrl, bucketName, key);
    }
    
    public long getStorageUsage() {
        try {
            String statsUrl = String.format("%s/storage/v1/bucket/%s", supabaseUrl, bucketName);
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + supabaseKey);
            
            HttpEntity<?> requestEntity = new HttpEntity<>(headers);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.exchange(
                statsUrl, HttpMethod.GET, requestEntity, Map.class).getBody();
            
            if (response != null && response.containsKey("files_count")) {
                return ((Number) response.get("files_count")).longValue();
            }
        } catch (Exception e) {
            log.warn("Failed to get storage usage: {}", e.getMessage());
        }
        return 0;
    }
}
