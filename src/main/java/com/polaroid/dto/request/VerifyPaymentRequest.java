package com.polaroid.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VerifyPaymentRequest {
    @NotBlank(message = "Action is required (APPROVE or REJECT)")
    private String action;

    @jakarta.validation.constraints.Size(max = 1000)
    private String reason;
    @jakarta.validation.constraints.DecimalMin("0.01")
    @jakarta.validation.constraints.Digits(integer = 8, fraction = 2)
    private java.math.BigDecimal amountReceived;
    @jakarta.validation.constraints.Size(max = 200)
    private String reference;
    @jakarta.validation.constraints.Size(max = 1000)
    private String notes;
    private boolean fundsConfirmed;
    /** The proof reviewed by the admin; reject stale reviews after a replacement upload. */
    private String proofKey;
}
