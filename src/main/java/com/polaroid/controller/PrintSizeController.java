package com.polaroid.controller;

import com.polaroid.dto.response.PrintSizeResponse;
import com.polaroid.service.PrintSizeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/print-sizes")
@RequiredArgsConstructor
public class PrintSizeController {

    private final PrintSizeService printSizeService;

    @GetMapping
    public ResponseEntity<List<PrintSizeResponse>> getActivePrintSizes() {
        return ResponseEntity.ok(printSizeService.getActivePrintSizes());
    }
}
