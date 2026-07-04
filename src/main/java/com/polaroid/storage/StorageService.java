package com.polaroid.storage;

import java.util.List;

public interface StorageService {
    String upload(String key, byte[] data, String contentType);
    byte[] download(String key);
    void delete(String key);
    List<StorageFileInfo> listFiles(String prefix);
    String getSignedUrl(String key, int expirationSeconds);
    long getStorageUsage();
    boolean isAvailable();
}
