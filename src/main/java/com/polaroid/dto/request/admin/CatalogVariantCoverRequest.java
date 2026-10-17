package com.polaroid.dto.request.admin;

import lombok.Data;

/**
 * Sets or clears the cover image of one style variant.
 * A null {@code coverImageUrl} clears the style cover so the storefront
 * falls back to the product-level image.
 */
@Data
public class CatalogVariantCoverRequest {
    private String coverImageUrl;
}
