package com.polaroid.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.UUID;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import com.polaroid.exception.BadRequestException;
import com.polaroid.exception.ForbiddenException;
import com.polaroid.exception.ResourceNotFoundException;
import com.polaroid.model.Order;
import com.polaroid.model.User;
import com.polaroid.model.enums.Role;
import com.polaroid.repository.OrderRepository;
import com.polaroid.repository.UserRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileService {
    
    private final WebClient supabaseWebClient;
    private final RestTemplate restTemplate;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    
    @Value("${supabase.url:https://placeholder.supabase.co}")
    private String supabaseUrl;
    
    @Value("${supabase.key:placeholder-key}")
    private String supabaseKey;
    
    @Value("${supabase.storage-bucket:polaroid-glossy}")
    private String bucketName;
    
    @Value("${supabase.signed-url-expiration:3600}")
    private int signedUrlExpiration;

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024L;
    private static final long MAX_DECODED_MEMORY = 100 * 1024 * 1024L;
    private static final int MAX_IMAGE_DIMENSION = 10000;
    private static final Set<String> ALLOWED_FORMATS = Set.of("jpeg", "png");

    public Map<String, String> uploadFile(MultipartFile file, String orderId, String userEmail) throws IOException {
        Order order = findAuthorizedOrder(orderId, userEmail);
        byte[] processedImage = validateAndProcessImage(file);

        String format = detectImageFormat(processedImage);
        boolean isJpeg = "jpeg".equals(format);
        String extension = isJpeg ? "jpg" : "png";
        String fileName = UUID.randomUUID().toString() + "." + extension;
        String folder = "orders/" + order.getId() + "/original";
        String key = folder + "/" + fileName;
        
        try {
            String uploadUrl = String.format("%s/storage/v1/object/%s/%s", supabaseUrl, bucketName, key);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(isJpeg ? MediaType.IMAGE_JPEG : MediaType.IMAGE_PNG);
            headers.set("Authorization", "Bearer " + supabaseKey);
            headers.set("x-upsert", "true");
            
            HttpEntity<byte[]> requestEntity = new HttpEntity<>(processedImage, headers);
            
            restTemplate.exchange(uploadUrl, HttpMethod.PUT, requestEntity, String.class);
            
            String signedUrl = createSignedUrl(key);
            
            Map<String, String> result = new HashMap<>();
            result.put("key", key);
            result.put("url", signedUrl);
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
    
    public List<Map<String, String>> listFiles(String orderId, String userEmail) {
        Order order = findAuthorizedOrder(orderId, userEmail);
        return listFilesForOrder(order);
    }

    public List<Map<String, String>> listFilesForStaff(String orderId) {
        Order order = findOrder(orderId);
        return listFilesForOrder(order);
    }

    private List<Map<String, String>> listFilesForOrder(Order order) {
        String folder = "orders/" + order.getId() + "/original";
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
                    fileInfo.put("url", createSignedUrl(folder + "/" + item.get("name")));
                    files.add(fileInfo);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to list files for {}: {}", folder, e.getMessage());
        }
        
        return files;
    }
    
    public String createSignedUrl(String key) {
        try {
            String signUrl = String.format("%s/storage/v1/object/sign/%s/%s", supabaseUrl, bucketName, key);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + supabaseKey);

            Map<String, Object> body = Map.of("expiresIn", signedUrlExpiration);
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(signUrl, requestEntity, Map.class);
            if (response == null) {
                throw new IllegalStateException("Empty signed URL response");
            }

            Object signedUrl = response.getOrDefault("signedURL", response.get("signedUrl"));
            if (signedUrl == null) {
                throw new IllegalStateException("Missing signed URL in response");
            }

            String url = signedUrl.toString();
            if (url.startsWith("http")) return url;
            return supabaseUrl + (url.startsWith("/storage") ? "" : "/storage/v1") + url;
        } catch (Exception e) {
            log.error("Failed to create signed URL for {}: {}", key, e.getMessage());
            throw new IllegalStateException("Failed to create signed URL");
        }
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

    private byte[] validateAndProcessImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is required");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BadRequestException("File exceeds 10MB limit");
        }

        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("Failed to read file");
        }

        String detectedFormat = detectImageFormat(fileBytes);
        if (detectedFormat == null || !ALLOWED_FORMATS.contains(detectedFormat)) {
            throw new BadRequestException(
                "Invalid image file: only JPEG and PNG are allowed (magic bytes mismatch)");
        }

        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(fileBytes));
        } catch (IOException e) {
            throw new BadRequestException("Failed to decode image: " + e.getMessage());
        }

        if (image == null) {
            throw new BadRequestException("Invalid image file: could not decode");
        }

        long decodedMemory = (long) image.getWidth() * image.getHeight() * 4L;
        if (decodedMemory > MAX_DECODED_MEMORY) {
            throw new BadRequestException(String.format(
                "Image decompression size (%dMB) exceeds allowed limit (100MB)",
                decodedMemory / (1024 * 1024)));
        }

        if (image.getWidth() > MAX_IMAGE_DIMENSION || image.getHeight() > MAX_IMAGE_DIMENSION) {
            throw new BadRequestException(String.format(
                "Image dimensions (%dx%d) exceed maximum allowed (%dx%d)",
                image.getWidth(), image.getHeight(),
                MAX_IMAGE_DIMENSION, MAX_IMAGE_DIMENSION));
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        String formatName = "jpeg".equals(detectedFormat) ? "jpg" : detectedFormat;

        ImageWriter writer = ImageIO.getImageWritersByFormatName(formatName).next();
        try {
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(0.95f);
            }
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(image, null, null), param);
            }
        } catch (IOException e) {
            throw new BadRequestException("Failed to re-encode image: " + e.getMessage());
        } finally {
            writer.dispose();
        }

        return baos.toByteArray();
    }

    private String detectImageFormat(byte[] fileBytes) {
        if (fileBytes == null || fileBytes.length < 12) return null;

        if ((fileBytes[0] & 0xFF) == 0xFF
                && (fileBytes[1] & 0xFF) == 0xD8
                && (fileBytes[2] & 0xFF) == 0xFF) {
            return "jpeg";
        }

        if ((fileBytes[0] & 0xFF) == 0x89
                && fileBytes[1] == 0x50
                && fileBytes[2] == 0x4E
                && fileBytes[3] == 0x47
                && fileBytes[4] == 0x0D
                && fileBytes[5] == 0x0A
                && fileBytes[6] == 0x1A
                && fileBytes[7] == 0x0A) {
            return "png";
        }

        return null;
    }

    private Order findAuthorizedOrder(String orderId, String userEmail) {
        Order order = findOrder(orderId);
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (isStaff(user.getRole()) || (order.getUserId() != null && order.getUserId().equals(user.getId()))) {
            return order;
        }

        throw new ForbiddenException("Not authorized to access files for this order");
    }

    private Order findOrder(String orderId) {
        try {
            return orderRepository.findById(UUID.fromString(orderId))
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        } catch (IllegalArgumentException ignored) {
            return orderRepository.findByOrderNumber(orderId)
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        }
    }

    private boolean isStaff(Role role) {
        return role == Role.ADMIN || role == Role.MARKETING || role == Role.PACKER;
    }
}
