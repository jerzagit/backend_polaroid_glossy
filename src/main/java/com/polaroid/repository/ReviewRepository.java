package com.polaroid.repository;

import com.polaroid.model.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findBySizeIdOrderByCreatedAtDesc(String sizeId);
    List<Review> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<Review> findByOrderId(UUID orderId);
    List<Review> findAllByOrderByCreatedAtDesc();
}
