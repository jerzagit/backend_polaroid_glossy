package com.polaroid.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VerifyPaymentRequest {
    @NotBlank(message = "Action is required (APPROVE or REJECT)")
    private String action;

    private String reason;
}
