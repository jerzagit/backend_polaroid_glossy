package com.polaroid.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import java.util.List;

@Data
public class UpdateOrderRequest {
    private String orderNumber;
    private String customerName;
    private String customerEmail;
    private String customerPhone;
    private String customerHouseUnitNo;
    private String customerAddressLine1;
    private String customerAddressLine2;
    @Pattern(regexp = "\\d{5}", message = "Postcode must be 5 digits")
    private String customerPostcode;
    private String customerCity;
    private String customerState;
    private String notes;
    @Valid
    private List<OrderRequest.OrderItemRequest> items;
}
