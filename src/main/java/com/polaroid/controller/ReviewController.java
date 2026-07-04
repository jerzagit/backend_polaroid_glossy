package com.polaroid.controller;

import com.polaroid.dto.response.ReviewResponse;
import com.polaroid.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getReviews(
            @RequestParam(required = false) String sizeId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String orderId) {
        List<ReviewResponse> reviews = reviewService.getReviews(sizeId, userId, orderId);
        return ResponseEntity.ok(Map.of("success", true, "reviews", reviews));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createReview(@RequestBody Map<String, Object> body) {
        ReviewResponse review = reviewService.createReview(body);
        return ResponseEntity.ok(Map.of("success", true, "review", review));
    }
}
