package com.polaroid.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewResponse {
    private Long id;
    private String userId;
    private String orderId;
    private String sizeId;
    private Integer rating;
    private String title;
    private String comment;
    private LocalDateTime createdAt;
    private ReviewUser user;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReviewUser {
        private String name;
        private String avatar;
    }
}
