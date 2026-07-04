package com.polaroid.service;

import com.polaroid.dto.response.ReviewResponse;
import com.polaroid.model.Review;
import com.polaroid.model.User;
import com.polaroid.repository.ReviewRepository;
import com.polaroid.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;

    public List<ReviewResponse> getReviews(String sizeId, String userId, String orderId) {
        List<Review> reviews;

        if (orderId != null) {
            reviews = reviewRepository.findByOrderId(UUID.fromString(orderId));
        } else if (userId != null) {
            reviews = reviewRepository.findByUserIdOrderByCreatedAtDesc(UUID.fromString(userId));
        } else if (sizeId != null) {
            reviews = reviewRepository.findBySizeIdOrderByCreatedAtDesc(sizeId);
        } else {
            reviews = reviewRepository.findAllByOrderByCreatedAtDesc();
        }

        Map<UUID, User> userCache = reviews.stream()
                .filter(r -> r.getUserId() != null)
                .map(Review::getUserId)
                .distinct()
                .collect(Collectors.toMap(
                        id -> id,
                        id -> userRepository.findById(id).orElse(null)
                ));

        return reviews.stream()
                .map(r -> toResponse(r, userCache.get(r.getUserId())))
                .toList();
    }

    @Transactional
    public ReviewResponse createReview(Map<String, Object> body) {
        Review review = Review.builder()
                .userId(body.get("userId") != null ? UUID.fromString(body.get("userId").toString()) : null)
                .orderId(body.get("orderId") != null ? UUID.fromString(body.get("orderId").toString()) : null)
                .sizeId(body.get("sizeId") != null ? body.get("sizeId").toString() : null)
                .rating(Integer.valueOf(body.get("rating").toString()))
                .title(body.get("title").toString())
                .comment(body.get("comment").toString())
                .build();

        review = reviewRepository.save(review);

        User user = review.getUserId() != null
                ? userRepository.findById(review.getUserId()).orElse(null)
                : null;

        return toResponse(review, user);
    }

    private ReviewResponse toResponse(Review review, User user) {
        return ReviewResponse.builder()
                .id(review.getId())
                .userId(review.getUserId() != null ? review.getUserId().toString() : null)
                .orderId(review.getOrderId() != null ? review.getOrderId().toString() : null)
                .sizeId(review.getSizeId())
                .rating(review.getRating())
                .title(review.getTitle())
                .comment(review.getComment())
                .createdAt(review.getCreatedAt())
                .user(user != null
                        ? ReviewResponse.ReviewUser.builder()
                                .name(user.getName())
                                .avatar(user.getAvatarUrl())
                                .build()
                        : null)
                .build();
    }
}
