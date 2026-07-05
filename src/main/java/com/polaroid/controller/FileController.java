package com.polaroid.controller;

import com.polaroid.service.FileService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {
    
    private final FileService fileService;
    
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("orderId") String orderId,
            @RequestParam(value = "customerEmail", required = false) String customerEmail,
            @RequestParam(value = "uploadToken", required = false) String uploadToken,
            @RequestParam(value = "orderItemId", required = false) String orderItemId,
            Authentication authentication,
            HttpServletRequest request) throws IOException {

        Map<String, Object> result;
        if (authentication != null) {
            result = fileService.uploadFile(file, orderId, orderItemId, customerEmail, authentication.getName());
        } else {
            result = fileService.uploadFileForOrder(file, orderId, orderItemId, customerEmail, uploadToken, clientIp(request));
        }

        Map<String, Object> response = new HashMap<>(result);
        response.put("success", true);
        return ResponseEntity.ok(response);
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
    
    @DeleteMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MARKETING', 'PACKER')")
    public ResponseEntity<Void> deleteFile(@RequestParam String key) throws IOException {
        fileService.deleteFile(key);
        return ResponseEntity.noContent().build();
    }
    
    @GetMapping("/order/{orderId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, String>>> listOrderFiles(
            @PathVariable String orderId,
            Authentication authentication) {
        return ResponseEntity.ok(fileService.listFiles(orderId, authentication.getName()));
    }
    
    @GetMapping("/order/{orderId}/download")
    @PreAuthorize("hasAnyRole('ADMIN', 'MARKETING', 'PACKER')")
    public ResponseEntity<byte[]> downloadOrderFiles(@PathVariable String orderId) throws IOException {
        List<Map<String, String>> files = fileService.listFilesForStaff(orderId);
        
        if (files.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        List<String> keys = files.stream()
                .map(f -> f.get("key"))
                .toList();
        
        byte[] zipData = fileService.downloadFiles(keys);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/zip"));
        headers.setContentDispositionFormData("attachment", orderId + "_images.zip");
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(zipData);
    }
}
