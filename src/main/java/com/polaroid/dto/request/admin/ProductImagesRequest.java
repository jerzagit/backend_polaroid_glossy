package com.polaroid.dto.request.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

@Data
public class ProductImagesRequest {
    @NotNull
    @Size(max = 12)
    private List<@NotBlank @Size(max = 2048) String> images;
}
