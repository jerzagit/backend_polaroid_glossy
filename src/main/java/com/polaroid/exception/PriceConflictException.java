package com.polaroid.exception;

import lombok.Getter;

import java.math.BigDecimal;

/**
 * Raised when a customer's selection is no longer purchasable or the
 * catalog price no longer matches what the customer saw. Mapped to HTTP 409
 * so the frontend can show a reconfirmation step before payment.
 */
@Getter
public class PriceConflictException extends RuntimeException {

    public static final String CODE_PRICE_CHANGED = "PRICE_CHANGED";
    public static final String CODE_UNAVAILABLE = "UNAVAILABLE";

    private final String code;
    private final BigDecimal currentTotal;

    public PriceConflictException(String code, String message, BigDecimal currentTotal) {
        super(message);
        this.code = code;
        this.currentTotal = currentTotal;
    }
}
