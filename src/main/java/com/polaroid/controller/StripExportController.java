package com.polaroid.controller;

import com.polaroid.service.StripExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;

@RestController
@RequestMapping("/api/files/order/{orderId}/items/{itemId}")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'MARKETING', 'PACKER')")
public class StripExportController {
    private final StripExportService exports;
    @GetMapping("/designs/{designId}/download")
    public ResponseEntity<InputStreamResource> design(@PathVariable String orderId, @PathVariable String itemId, @PathVariable String designId) throws IOException {
        return exports.download(orderId, itemId, designId);
    }
    @GetMapping("/download")
    public ResponseEntity<InputStreamResource> item(@PathVariable String orderId, @PathVariable String itemId) throws IOException {
        return exports.download(orderId, itemId, null);
    }
    @GetMapping("/designs/{designId}/slots/{position}/download")
    public ResponseEntity<byte[]> slot(@PathVariable String orderId, @PathVariable String itemId, @PathVariable String designId, @PathVariable int position) {
        return exports.slot(orderId, itemId, designId, position);
    }
}
