package com.polaroid.dto.request;

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
    private String customerPostcode;
    private String customerCity;
    private String customerState;
    private String notes;
    private List<OrderRequest.OrderItemRequest> items;
}
