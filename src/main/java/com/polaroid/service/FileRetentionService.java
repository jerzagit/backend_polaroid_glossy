package com.polaroid.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaroid.model.Order;
import com.polaroid.model.OrderItem;
import com.polaroid.model.StripDesignState;
import com.polaroid.model.enums.OrderStatus;
import com.polaroid.repository.OrderRepository;
import com.polaroid.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Deletes order files from object storage after the retention window.
 * Database rows and metadata are deliberately retained; only R2/S3 objects
 * are removed. The job is idempotent: missing objects count as success and a
 * partially failed order is retried on the next run.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileRetentionService {

    private static final List<OrderStatus> DUE_STATUSES = List.of(
            OrderStatus.CANCELLED,
            OrderStatus.EXPIRED,
            OrderStatus.REFUNDED,
            OrderStatus.POSTED,
            OrderStatus.ON_DELIVERY,
            OrderStatus.DELIVERED);

    private final OrderRepository orderRepository;
    private final StorageService storageService;
    private final ObjectMapper objectMapper;

    @Value("${app.retention.enabled:true}")
    private boolean enabled;

    @Value("${app.retention.dry-run:false}")
    private boolean dryRun;

    @Value("${app.retention.batch-size:50}")
    private int batchSize;

    @Scheduled(cron = "${app.retention.cleanup-cron:0 15 * * * *}")
    @Transactional
    public void purgeDueOrderFiles() {
        if (!enabled) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        List<Order> due = orderRepository.findDueForFileDeletion(
                now, DUE_STATUSES, PageRequest.of(0, Math.max(1, batchSize)));
        if (due.isEmpty()) {
            return;
        }

        int deleted = 0;
        int failed = 0;
        int skipped = 0;

        for (Order candidate : due) {
            Order order = orderRepository.lockById(candidate.getId()).orElse(null);
            if (order == null || !isDue(order, now)) {
                skipped++;
                continue;
            }

            List<String> keys = collectKeys(order);
            if (dryRun) {
                log.info("[retention dry-run] order {} would delete {} file(s): {}",
                        order.getOrderNumber(), keys.size(), keys);
                skipped++;
                continue;
            }
            if (keys.isEmpty()) {
                markDeleted(order, now);
                deleted++;
                continue;
            }

            boolean allOk = true;
            String lastError = null;
            for (String key : keys) {
                try {
                    storageService.delete(key);
                } catch (Exception e) {
                    allOk = false;
                    lastError = key + ": " + e.getMessage();
                    log.warn("Failed to delete {} for order {}: {}", key, order.getOrderNumber(), e.getMessage());
                }
            }

            if (allOk) {
                markDeleted(order, now);
                deleted++;
            } else {
                int attempts = order.getFilesDeleteAttempts() == null ? 0 : order.getFilesDeleteAttempts();
                order.setFilesDeleteAttempts(attempts + 1);
                order.setFilesDeleteError(truncate(lastError));
                orderRepository.save(order);
                failed++;
            }
        }

        log.info("File retention run: {} deleted, {} failed, {} skipped", deleted, failed, skipped);
    }

    private boolean isDue(Order order, LocalDateTime now) {
        return order.getFilesDeletedAt() == null
                && order.getFilesDeleteAfter() != null
                && !order.getFilesDeleteAfter().isAfter(now)
                && DUE_STATUSES.contains(order.getStatus());
    }

    private void markDeleted(Order order, LocalDateTime now) {
        order.setFilesDeletedAt(now);
        order.setFilesDeleteError(null);
        orderRepository.save(order);
    }

    private List<String> collectKeys(Order order) {
        Set<String> keys = new LinkedHashSet<>();
        if (order.getItems() != null) {
            for (OrderItem item : order.getItems()) {
                keys.addAll(readStringList(item.getS3Keys()));
                keys.addAll(readStringList(item.getImages()));
                keys.addAll(stripAssetKeys(item));
            }
        }
        return keys.stream()
                .filter(key -> key != null && key.startsWith("orders/"))
                .toList();
    }

    private List<String> stripAssetKeys(OrderItem item) {
        if (item.getStripDesign() == null || !StripDesigns.isStrip(item.getSizeId())) {
            return List.of();
        }
        try {
            StripDesignState state = StripDesigns.read(item, objectMapper);
            if (state == null || state.getAssets() == null) {
                return List.of();
            }
            return state.getAssets().stream()
                    .map(StripDesignState.Asset::getStorageKey)
                    .filter(key -> key != null && !key.isBlank())
                    .toList();
        } catch (Exception e) {
            log.warn("Could not parse strip design for item {}: {}", item.getId(), e.getMessage());
            return List.of();
        }
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            // Older rows may contain a bare or comma-separated key list.
            if (!json.trim().startsWith("[")) {
                return Arrays.stream(json.split(","))
                        .map(String::trim)
                        .map(value -> value.replaceAll("^\"|\"$", ""))
                        .filter(value -> !value.isBlank())
                        .toList();
            }
            log.warn("Could not parse order file metadata: {}", e.getMessage());
            return List.of();
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 2000 ? value : value.substring(0, 2000);
    }
}
