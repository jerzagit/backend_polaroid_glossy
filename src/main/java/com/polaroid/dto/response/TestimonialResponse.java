package com.polaroid.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestimonialResponse {
    private Long id;
    private String name;
    private String location;
    private String text;
    private String printType;
    private String imageUrl;
    private String textMy;
    private String printTypeMy;
    private Integer rating;
    private Integer sortOrder;
    private Boolean isActive;
}
