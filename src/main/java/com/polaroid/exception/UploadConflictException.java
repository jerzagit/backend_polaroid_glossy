package com.polaroid.exception;

public class UploadConflictException extends RuntimeException {
    private final Integer uploadedImageCount;
    private final Integer expectedImageCount;

    public UploadConflictException(String message) {
        this(message, null, null);
    }

    public UploadConflictException(String message, Integer uploadedImageCount, Integer expectedImageCount) {
        super(message);
        this.uploadedImageCount = uploadedImageCount;
        this.expectedImageCount = expectedImageCount;
    }

    public Integer getUploadedImageCount() {
        return uploadedImageCount;
    }

    public Integer getExpectedImageCount() {
        return expectedImageCount;
    }

    public Integer getRemainingImageCount() {
        if (uploadedImageCount == null || expectedImageCount == null) {
            return null;
        }
        return Math.max(0, expectedImageCount - uploadedImageCount);
    }
}
