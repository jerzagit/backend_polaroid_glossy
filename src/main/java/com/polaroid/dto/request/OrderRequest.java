package com.polaroid.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
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

    @Pattern(regexp = "now|later")
    private String uploadMode = "now";

    @Pattern(regexp = "toyyibpay|bank_transfer")
    private String paymentMethod = "toyyibpay";

    @Pattern(regexp = "[a-zA-Z0-9-]{1,64}")
    private String checkoutKey;

    private String notes;

    /** Optional guard: if provided and the server-calculated total differs
     *  (e.g. a seller changed prices mid-journey), checkout is rejected with
     *  a PRICE_CHANGED conflict so the customer can reconfirm. */
    @DecimalMin(value = "0.0", inclusive = false, message = "expectedTotal must be positive")
    private BigDecimal expectedTotal;
    
    @Valid
    @NotNull(message = "Items are required")
    @jakarta.validation.constraints.Size(min = 1, max = 20)
    private List<OrderItemRequest> items;
    
    @Data
    public static class OrderItemRequest {
        private StripDesignRequest stripDesign;
        /** Legacy selection: size id + per-print price (still supported). */
        private String sizeId;

        /** Size -> Style -> Package selection. Preferred over sizeId.
         *  These are the database-generated ids returned by /api/products. */
        private Long productId;
        private Long variantId;
        private Long packageId;

        /** Number of packages purchased (defaults to 1). */
        @Min(value = 1, message = "packages must be at least 1")
        private Integer packages;

        /** Legacy: number of prints. For package selections this is derived. */
        @Positive(message = "Quantity must be positive")
        private Integer quantity;

        private Integer expectedImageCount;
        
        private List<String> imageUrls;
        private List<String> customTexts;
    }
}
