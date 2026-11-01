package com.polaroid.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReopenOrderRequest {

    @NotBlank(message = "A reason is required to reopen an order")
    @Size(max = 500, message = "Reason must be 500 characters or fewer")
    private String reason;

    /** Set when the customer has already paid for the reopened order. */
    private boolean paymentReceived;
}
