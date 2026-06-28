package com.polaroid.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class OrderRequest {
    @NotBlank(message = "Customer name is required")
    private String customerName;
    
    @NotBlank(message = "Customer email is required")
    private String customerEmail;
    
    private String customerPhone;

    @NotBlank(message = "House/unit number is required")
    private String customerHouseUnitNo;

    @NotBlank(message = "Address line 1 is required")
    private String customerAddressLine1;

    private String customerAddressLine2;

    @NotBlank(message = "Postcode is required")
    @Pattern(regexp = "\\d{5}", message = "Postcode must be 5 digits")
    private String customerPostcode;

    @NotBlank(message = "City is required")
    private String customerCity;

    @NotBlank(message = "State is required")
    private String customerState;

    @NotBlank(message = "Country is required")
    private String customerCountry;

    private String affiliateCode;

    private String notes;
    
    @NotNull(message = "Items are required")
    private List<OrderItemRequest> items;
    
    @Data
    public static class OrderItemRequest {
        @NotBlank(message = "Size ID is required")
        private String sizeId;
        
        @NotNull(message = "Quantity is required")
        private Integer quantity;
        
        private List<String> imageUrls;
        private List<String> customTexts;
    }
}
