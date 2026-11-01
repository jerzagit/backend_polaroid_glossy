package com.polaroid.exception;

public class UploadRateLimitException extends RuntimeException {
    private final int retryAfter;
    public UploadRateLimitException(int retryAfter) {
        super("Upload attempt limit reached. Please wait before retrying.");
        this.retryAfter = retryAfter;
    }
    public int getRetryAfter() { return retryAfter; }
}
