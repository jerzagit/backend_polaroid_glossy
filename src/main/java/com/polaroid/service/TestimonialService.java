package com.polaroid.service;

import com.polaroid.dto.response.TestimonialResponse;
import com.polaroid.repository.TestimonialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TestimonialService {

    private final TestimonialRepository testimonialRepository;

    public List<TestimonialResponse> getActiveTestimonials() {
        return testimonialRepository.findByIsActiveTrueOrderBySortOrderAsc().stream()
                .map(t -> TestimonialResponse.builder()
                        .id(t.getId())
                        .name(t.getName())
                        .location(t.getLocation())
                        .text(t.getText())
                        .printType(t.getPrintType())
                        .imageUrl(t.getImageUrl())
                        .textMy(t.getTextMy())
                        .printTypeMy(t.getPrintTypeMy())
                        .rating(t.getRating())
                        .sortOrder(t.getSortOrder())
                        .isActive(t.getIsActive())
                        .build())
                .toList();
    }
}
