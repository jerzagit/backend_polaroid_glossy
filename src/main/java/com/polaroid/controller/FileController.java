package com.polaroid.controller;

import com.polaroid.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {
    
    private final FileService fileService;
    
    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("orderId") String orderId,
            @RequestParam(value = "orderItemId", required = false) String orderItemId,
            Authentication authentication) throws IOException {
        
        return ResponseEntity.ok(fileService.uploadFile(file, orderId, orderItemId, authentication.getName()));
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
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", orderId + "_images.zip");
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(zipData);
    }
}
