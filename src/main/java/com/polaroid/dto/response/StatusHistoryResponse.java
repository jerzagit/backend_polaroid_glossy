package com.polaroid.dto.response;

import com.polaroid.model.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusHistoryResponse {
    private String id;
    private OrderStatus status;
    private String message;
    private LocalDateTime createdAt;
}
