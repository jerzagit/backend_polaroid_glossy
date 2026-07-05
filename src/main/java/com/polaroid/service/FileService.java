package com.polaroid.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import com.polaroid.exception.BadRequestException;
import com.polaroid.exception.ForbiddenException;
import com.polaroid.exception.ResourceNotFoundException;
import com.polaroid.model.Order;
import com.polaroid.model.OrderItem;
import com.polaroid.model.User;
import com.polaroid.model.enums.OrderStatus;
import com.polaroid.model.enums.PaymentStatus;
import com.polaroid.model.enums.Role;
import com.polaroid.repository.OrderItemRepository;
import com.polaroid.repository.OrderRepository;
import com.polaroid.repository.UserRepository;
import com.polaroid.storage.StorageService;
import com.polaroid.storage.StorageFileInfo;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileService {

    private final StorageService storageService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    private static final long MAX_FILE_SIZE = 25 * 1024 * 1024L;
    private static final long MAX_DECODED_MEMORY = 100 * 1024 * 1024L;
    private static final int MAX_IMAGE_DIMENSION = 10000;
    private static final Set<String> ALLOWED_FORMATS = Set.of("jpeg", "png", "webp");
    private static final int MAX_ANONYMOUS_UPLOADS_PER_WINDOW = 10;
    private static final Duration ANONYMOUS_UPLOAD_WINDOW = Duration.ofMinutes(10);

    private final Map<String, UploadWindow> anonymousUploadWindows = new ConcurrentHashMap<>();

    @Transactional
    public Map<String, String> uploadFile(MultipartFile file, String orderId, String orderItemId, String userEmail) throws IOException {
        Order order = findAuthorizedOrder(orderId, userEmail);
        validateOrderAcceptsUpload(order);
        return storeFile(file, order, orderItemId);
    }

    @Transactional
    public Map<String, String> uploadFileForOrder(MultipartFile file, String orderId, String orderItemId, String customerEmail, String uploadToken, String clientIp) throws IOException {
        Order order = findOrder(orderId);
        validateAnonymousUploadAllowed(order, customerEmail, uploadToken, clientIp);
        return storeFile(file, order, orderItemId);
    }

    private Map<String, String> storeFile(MultipartFile file, Order order, String orderItemId) throws IOException {
        OrderItem item = resolveOrderItemForUpdate(order, orderItemId);
        enforceUploadCapacity(item);

        byte[] processedImage = validateAndProcessImage(file);

        String format = detectImageFormat(processedImage);
        boolean isJpeg = "jpeg".equals(format);
        String extension = isJpeg ? "jpg" : "png";
        String fileName = UUID.randomUUID().toString() + "." + extension;
        String folder = "orders/" + order.getOrderNumber() + "/original";
        String key = folder + "/" + fileName;

        try {
            String contentType = isJpeg ? "image/jpeg" : "image/png";
            storageService.upload(key, processedImage, contentType);
            String signedUrl = storageService.getSignedUrl(key, 3600);

            persistUploadedFile(item, key, signedUrl);
            invalidateUploadTokenIfComplete(order);

            Map<String, String> result = new HashMap<>();
            result.put("key", key);
            result.put("url", signedUrl);
            result.put("fileName", fileName);
            if (orderItemId != null && !orderItemId.isBlank()) {
                result.put("orderItemId", orderItemId);
            }

            return result;
        } catch (Exception e) {
            log.error("Failed to upload file: {}", e.getMessage());
            throw new IOException("Failed to upload file: " + e.getMessage());
        }
    }

    private void persistUploadedFile(OrderItem item, String key, String url) {
        item.setImages(appendJsonString(item.getImages(), url));
        item.setS3Keys(appendJsonString(item.getS3Keys(), key));
        orderItemRepository.save(item);
    }

    private OrderItem resolveOrderItemForUpdate(Order order, String orderItemId) {
        if (orderItemId != null && !orderItemId.isBlank()) {
            UUID itemId;
            try {
                itemId = UUID.fromString(orderItemId);
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid order item ID");
            }

            return orderItemRepository.findByOrderIdAndIdForUpdate(order.getId(), itemId)
                    .orElseThrow(() -> new ResourceNotFoundException("Order item not found"));
        }

        List<OrderItem> items = orderItemRepository.findByOrderIdForUpdate(order.getId());
        if (items.size() == 1) {
            return items.get(0);
        }

        throw new BadRequestException("orderItemId is required when an order has multiple items");
    }

    private void enforceUploadCapacity(OrderItem item) {
        int expectedImageCount = expectedImageCount(item);
        if (expectedImageCount <= 0) {
            throw new BadRequestException("Order item does not allow image uploads");
        }

        int uploadedCount = readJsonStringList(item.getS3Keys()).size();
        if (uploadedCount >= expectedImageCount) {
            throw new BadRequestException("Upload limit reached for this order item");
        }
    }

    private void validateAnonymousUploadAllowed(Order order, String customerEmail, String uploadToken, String clientIp) {
        validateOrderAcceptsUpload(order);
        if (customerEmail == null || customerEmail.isBlank()
                || !customerEmail.equalsIgnoreCase(order.getCustomerEmail())) {
            throw new ForbiddenException("Customer email does not match this order");
        }
        validateGuestUploadToken(order, uploadToken);

        enforceAnonymousUploadRateLimit(order, clientIp);
    }

    private void validateGuestUploadToken(Order order, String uploadToken) {
        if (order.getUploadTokenHash() == null || order.getUploadTokenHash().isBlank()) {
            throw new ForbiddenException("Upload token is required for this order");
        }
        if (uploadToken == null || uploadToken.isBlank()) {
            throw new ForbiddenException("Upload token is required for this order");
        }
        if (order.getUploadTokenExpiresAt() != null && order.getUploadTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ForbiddenException("Upload token has expired");
        }
        if (!MessageDigest.isEqual(
                hashUploadToken(uploadToken).getBytes(StandardCharsets.UTF_8),
                order.getUploadTokenHash().getBytes(StandardCharsets.UTF_8))) {
            throw new ForbiddenException("Invalid upload token");
        }
    }

    private void validateOrderAcceptsUpload(Order order) {
        if (order.getStatus() == OrderStatus.CANCELLED
                || order.getStatus() == OrderStatus.REFUNDED
                || order.getStatus() == OrderStatus.EXPIRED) {
            throw new BadRequestException("Cannot upload files to a closed order");
        }
        if (order.getStatus() == OrderStatus.POSTED
                || order.getStatus() == OrderStatus.ON_DELIVERY
                || order.getStatus() == OrderStatus.DELIVERED) {
            throw new BadRequestException("Cannot upload files after an order has been dispatched");
        }
        if (order.getPaymentStatus() == PaymentStatus.FAILED) {
            throw new BadRequestException("Cannot upload files to an order with failed payment");
        }
    }

    private void enforceAnonymousUploadRateLimit(Order order, String clientIp) {
        String normalizedIp = clientIp == null || clientIp.isBlank() ? "unknown" : clientIp.trim();
        String key = normalizedIp + ":" + order.getOrderNumber();
        LocalDateTime now = LocalDateTime.now();
        UploadWindow window = anonymousUploadWindows.computeIfAbsent(key, ignored -> new UploadWindow(now));

        synchronized (window) {
            if (window.windowStartedAt.plus(ANONYMOUS_UPLOAD_WINDOW).isBefore(now)) {
                window.windowStartedAt = now;
                window.count = 0;
            }
            if (window.count >= MAX_ANONYMOUS_UPLOADS_PER_WINDOW) {
                throw new BadRequestException("Too many upload attempts. Please try again later");
            }
            window.count++;
        }

        if (anonymousUploadWindows.size() > 10_000) {
            anonymousUploadWindows.entrySet().removeIf(entry ->
                    entry.getValue().windowStartedAt.plus(ANONYMOUS_UPLOAD_WINDOW).isBefore(now));
        }
    }

    private void invalidateUploadTokenIfComplete(Order order) {
        List<OrderItem> items = orderItemRepository.findByOrderIdForUpdate(order.getId());
        boolean complete = !items.isEmpty() && items.stream().allMatch(item -> {
            int expectedImageCount = expectedImageCount(item);
            return expectedImageCount > 0 && readJsonStringList(item.getS3Keys()).size() >= expectedImageCount;
        });

        if (complete && order.getUploadTokenHash() != null) {
            order.setUploadTokenHash(null);
            order.setUploadTokenExpiresAt(null);
            orderRepository.save(order);
        }
    }

    private String hashUploadToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash upload token", e);
        }
    }

    private String appendJsonString(String json, String value) {
        List<String> values;
        try {
            if (json == null || json.isBlank()) {
                values = new ArrayList<>();
            } else {
                values = objectMapper.readValue(json, new TypeReference<List<String>>() {});
            }
        } catch (Exception e) {
            values = new ArrayList<>();
        }

        values.add(value);

        try {
            return objectMapper.writeValueAsString(values);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to update uploaded file metadata", e);
        }
    }

    private int expectedImageCount(OrderItem item) {
        if (item.getExpectedImageCount() != null) {
            return item.getExpectedImageCount();
        }
        return item.getQuantity() != null ? item.getQuantity() : 0;
    }

    public void deleteFile(String key) throws IOException {
        try {
            storageService.delete(key);
        } catch (Exception e) {
            log.error("Failed to delete file: {}", e.getMessage());
            throw new IOException("Failed to delete file: " + e.getMessage());
        }
    }

    public byte[] downloadFiles(List<String> keys) throws IOException {
        ByteArrayOutputStream zipBuffer = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(zipBuffer)) {
            for (String key : keys) {
                try {
                    byte[] fileBytes = storageService.download(key);
                    if (fileBytes.length == 0) {
                        continue;
                    }

                    ZipEntry zipEntry = new ZipEntry(fileNameFromKey(key));
                    zipOutputStream.putNextEntry(zipEntry);
                    zipOutputStream.write(fileBytes);
                    zipOutputStream.closeEntry();
                } catch (Exception e) {
                    log.warn("Failed to add file {} to zip: {}", key, e.getMessage());
                }
            }
        }

        return zipBuffer.toByteArray();
    }

    public List<Map<String, String>> listFiles(String orderId, String userEmail) {
        Order order = findAuthorizedOrder(orderId, userEmail);
        return listFilesForOrder(order);
    }

    public List<Map<String, String>> listFilesForStaff(String orderId) {
        Order order = findOrder(orderId);
        return listFilesForOrder(order);
    }

    private List<Map<String, String>> listFilesForOrder(Order order) {
        List<Map<String, String>> files = new ArrayList<>();
        List<String> persistedKeys = uploadedFileKeys(order);

        if (!persistedKeys.isEmpty()) {
            for (String key : persistedKeys) {
                Map<String, String> fileInfo = new HashMap<>();
                fileInfo.put("name", fileNameFromKey(key));
                fileInfo.put("key", key);
                fileInfo.put("url", storageService.getSignedUrl(key, 3600));
                files.add(fileInfo);
            }
            return files;
        }

        String prefix = "orders/" + order.getOrderNumber() + "/original";
        try {
            List<StorageFileInfo> storageFiles = storageService.listFiles(prefix);
            for (StorageFileInfo sf : storageFiles) {
                Map<String, String> fileInfo = new HashMap<>();
                fileInfo.put("name", sf.name());
                fileInfo.put("key", sf.key());
                fileInfo.put("url", sf.url());
                files.add(fileInfo);
            }
        } catch (Exception e) {
            log.warn("Failed to list files for order {}: {}", order.getOrderNumber(), e.getMessage());
        }

        return files;
    }

    private List<String> uploadedFileKeys(Order order) {
        List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
        List<String> keys = new ArrayList<>();

        for (OrderItem item : items) {
            keys.addAll(readJsonStringList(item.getS3Keys()));
        }

        return keys.stream()
                .filter(key -> key != null && !key.isBlank())
                .distinct()
                .toList();
    }

    private List<String> readJsonStringList(String json) {
        try {
            if (json == null || json.isBlank()) {
                return List.of();
            }
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse uploaded file metadata: {}", e.getMessage());
            return List.of();
        }
    }

    private String fileNameFromKey(String key) {
        int index = key.lastIndexOf('/');
        return index >= 0 ? key.substring(index + 1) : key;
    }

    public long getStorageUsage() {
        return storageService.getStorageUsage();
    }

    private byte[] validateAndProcessImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is required");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BadRequestException("File exceeds 25MB limit");
        }

        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("Failed to read file");
        }

        String detectedFormat = detectImageFormat(fileBytes);
        if (detectedFormat == null || !ALLOWED_FORMATS.contains(detectedFormat)) {
            throw new BadRequestException(
                "Invalid image file: only JPEG, PNG, and WEBP are allowed (magic bytes mismatch)");
        }

        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(fileBytes));
        } catch (IOException e) {
            throw new BadRequestException("Failed to decode image: " + e.getMessage());
        }

        if (image == null) {
            throw new BadRequestException("Invalid image file: could not decode");
        }

        long decodedMemory = (long) image.getWidth() * image.getHeight() * 4L;
        if (decodedMemory > MAX_DECODED_MEMORY) {
            throw new BadRequestException(String.format(
                "Image decompression size (%dMB) exceeds allowed limit (100MB)",
                decodedMemory / (1024 * 1024)));
        }

        if (image.getWidth() > MAX_IMAGE_DIMENSION || image.getHeight() > MAX_IMAGE_DIMENSION) {
            throw new BadRequestException(String.format(
                "Image dimensions (%dx%d) exceed maximum allowed (%dx%d)",
                image.getWidth(), image.getHeight(),
                MAX_IMAGE_DIMENSION, MAX_IMAGE_DIMENSION));
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        String formatName = reencodeFormatName(detectedFormat, image);

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(formatName);
        if (!writers.hasNext()) {
            throw new BadRequestException("No image encoder is available for " + formatName);
        }

        ImageWriter writer = writers.next();
        try {
            BufferedImage outputImage = "jpg".equals(formatName) ? withoutAlpha(image) : image;
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(0.95f);
            }
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(outputImage, null, null), param);
            }
        } catch (IOException e) {
            throw new BadRequestException("Failed to re-encode image: " + e.getMessage());
        } finally {
            writer.dispose();
        }

        return baos.toByteArray();
    }

    private String reencodeFormatName(String detectedFormat, BufferedImage image) {
        if ("png".equals(detectedFormat) || hasAlpha(image)) {
            return "png";
        }
        return "jpg";
    }

    private boolean hasAlpha(BufferedImage image) {
        return image.getColorModel() != null && image.getColorModel().hasAlpha();
    }

    private BufferedImage withoutAlpha(BufferedImage image) {
        if (!hasAlpha(image) && image.getType() == BufferedImage.TYPE_INT_RGB) {
            return image;
        }

        BufferedImage rgbImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = rgbImage.createGraphics();
        try {
            graphics.drawImage(image, 0, 0, java.awt.Color.WHITE, null);
        } finally {
            graphics.dispose();
        }
        return rgbImage;
    }

    private String detectImageFormat(byte[] fileBytes) {
        if (fileBytes == null || fileBytes.length < 12) return null;

        if ((fileBytes[0] & 0xFF) == 0xFF
                && (fileBytes[1] & 0xFF) == 0xD8
                && (fileBytes[2] & 0xFF) == 0xFF) {
            return "jpeg";
        }

        if ((fileBytes[0] & 0xFF) == 0x89
                && fileBytes[1] == 0x50
                && fileBytes[2] == 0x4E
                && fileBytes[3] == 0x47
                && fileBytes[4] == 0x0D
                && fileBytes[5] == 0x0A
                && fileBytes[6] == 0x1A
                && fileBytes[7] == 0x0A) {
            return "png";
        }

        if (fileBytes[0] == 0x52
                && fileBytes[1] == 0x49
                && fileBytes[2] == 0x46
                && fileBytes[3] == 0x46
                && fileBytes[8] == 0x57
                && fileBytes[9] == 0x45
                && fileBytes[10] == 0x42
                && fileBytes[11] == 0x50) {
            return "webp";
        }

        return null;
    }

    private Order findAuthorizedOrder(String orderId, String userEmail) {
        Order order = findOrder(orderId);
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (isStaff(user.getRole()) || (order.getUserId() != null && order.getUserId().equals(user.getId()))) {
            return order;
        }

        throw new ForbiddenException("Not authorized to access files for this order");
    }

    private Order findOrder(String orderId) {
        try {
            return orderRepository.findById(UUID.fromString(orderId))
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        } catch (IllegalArgumentException ignored) {
            return orderRepository.findByOrderNumber(orderId)
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        }
    }

    private boolean isStaff(Role role) {
        return role == Role.ADMIN || role == Role.MARKETING || role == Role.PACKER;
    }

    private static class UploadWindow {
        private LocalDateTime windowStartedAt;
        private int count;

        private UploadWindow(LocalDateTime windowStartedAt) {
            this.windowStartedAt = windowStartedAt;
        }
    }
}
