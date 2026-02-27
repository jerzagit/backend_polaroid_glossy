package com.polaroid.controller;

import com.polaroid.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
            @RequestParam("orderId") String orderId) throws IOException {
        
        return ResponseEntity.ok(fileService.uploadFile(file, orderId));
    }
    
    @DeleteMapping("/{key}")
    public ResponseEntity<Void> deleteFile(@PathVariable String key) throws IOException {
        fileService.deleteFile(key);
        return ResponseEntity.noContent().build();
    }
    
    @GetMapping("/order/{orderId}")
    public ResponseEntity<List<Map<String, String>>> listOrderFiles(@PathVariable String orderId) {
        return ResponseEntity.ok(fileService.listFiles(orderId));
    }
    
    @GetMapping("/order/{orderId}/download")
    @PreAuthorize("hasAnyRole('ADMIN', 'MARKETING', 'PACKER')")
    public ResponseEntity<byte[]> downloadOrderFiles(@PathVariable String orderId) throws IOException {
        List<Map<String, String>> files = fileService.listFiles(orderId);
        
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
