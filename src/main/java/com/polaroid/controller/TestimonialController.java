package com.polaroid.controller;

import com.polaroid.dto.response.TestimonialResponse;
import com.polaroid.service.TestimonialService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/testimonials")
@RequiredArgsConstructor
public class TestimonialController {

    private final TestimonialService testimonialService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getTestimonials() {
        List<TestimonialResponse> testimonials = testimonialService.getActiveTestimonials();
        return ResponseEntity.ok(Map.of("success", true, "testimonials", testimonials));
    }
}
