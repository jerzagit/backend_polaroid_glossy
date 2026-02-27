package com.polaroid.dto.mapper;

import com.polaroid.dto.response.PrintSizeResponse;
import com.polaroid.dto.request.PrintSizeRequest;
import com.polaroid.model.PrintSize;
import org.springframework.stereotype.Component;

@Component
public class PrintSizeMapper {
    
    public PrintSize toEntity(PrintSizeRequest dto) {
        if (dto == null) return null;
        
        return PrintSize.builder()
                .id(dto.getId())
                .name(dto.getName())
                .displayName(dto.getDisplayName())
                .width(dto.getWidth())
                .height(dto.getHeight())
                .price(dto.getPrice())
                .description(dto.getDescription())
                .isActive(dto.getIsActive())
                .build();
    }
    
    public PrintSizeResponse toDto(PrintSize entity) {
        if (entity == null) return null;
        
        return PrintSizeResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .displayName(entity.getDisplayName())
                .width(entity.getWidth())
                .height(entity.getHeight())
                .price(entity.getPrice())
                .description(entity.getDescription())
                .isActive(entity.getIsActive())
                .build();
    }
}
