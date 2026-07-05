package com.polaroid.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AddressRequest {
    @Size(max = 20)
    private String label;

    @NotBlank(message = "Name is required")
    @Size(max = 100)
    private String name;

    @Size(max = 15)
    private String phone;

    @Size(max = 50)
    private String houseUnitNo;

    @NotBlank(message = "Address line 1 is required")
    @Size(max = 200)
    private String addressLine1;

    @Size(max = 200)
    private String addressLine2;

    @NotBlank(message = "City is required")
    @Size(max = 100)
    private String city;

    @NotBlank(message = "State is required")
    @Size(max = 50)
    private String state;

    @NotBlank(message = "Postal code is required")
    @Size(max = 10)
    private String postalCode;

    @Size(max = 100)
    private String country;

    private Boolean isDefault;
}
