package com.polaroid.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaroid.exception.BadRequestException;
import com.polaroid.exception.ResourceNotFoundException;
import com.polaroid.model.*;
import com.polaroid.model.enums.*;
import com.polaroid.repository.OrderRepository;
import com.polaroid.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

@Service
@RequiredArgsConstructor
public class StripExportService {
    private static final long MAX_EXPORT_BYTES = 250L * 1024 * 1024;
    private final OrderRepository orders;
    private final StorageService storage;
    private final ObjectMapper mapper;

    private Order order(String id) {
        try { return orders.findById(UUID.fromString(id)).orElseThrow(() -> new ResourceNotFoundException("Order not found")); }
        catch (IllegalArgumentException e) { return orders.findByOrderNumber(id).orElseThrow(() -> new ResourceNotFoundException("Order not found")); }
    }
    @Transactional(readOnly = true)
    public boolean hasStrips(String id) { return order(id).getItems().stream().anyMatch(i -> StripDesigns.isStrip(i.getSizeId())); }

    private void eligible(Order order, OrderItem item) {
        if (order.getPaymentStatus() != PaymentStatus.PAID || !Set.of(OrderStatus.PENDING, OrderStatus.PROCESSING,
                OrderStatus.POSTED, OrderStatus.ON_DELIVERY, OrderStatus.DELIVERED).contains(order.getStatus()))
            throw new BadRequestException("Order is not eligible for production downloads");
        if (!StripDesigns.ready(item, mapper)) throw new BadRequestException("Strip assets are incomplete or legacy arrangement needs seller review");
    }

    @Transactional(readOnly = true)
    public ResponseEntity<InputStreamResource> download(String id, String itemId, String designId) throws IOException {
        Order order = order(id);
        List<OrderItem> items = order.getItems().stream().filter(i -> itemId == null || i.getId().toString().equals(itemId)).toList();
        if (items.isEmpty()) throw new ResourceNotFoundException("Order item not found");
        if (itemId == null && !Fulfillment.ready(order, mapper)) throw new BadRequestException("Payment and all required assets must be complete");
        Path zipPath = Files.createTempFile("strip-export-", ".zip");
        boolean foundDesign = designId == null;
        try {
            long total = 0;
            StringBuilder manifest = new StringBuilder("orderNumber,orderItemId,productCode,designId,designLabel,designRevision,copiesOfDesign,slotPosition,filename,assetId\r\n");
            StringBuilder summary = new StringBuilder("orderNumber,orderItemId,productCode,designId,designLabel,designRevision,physicalPrintCount\r\n");
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(zipPath))) {
                for (OrderItem item : items) {
                    String root = safe(order.getOrderNumber()) + "/" + safe(item.getSizeId()) + "__" + item.getId() + "/";
                    if (!StripDesigns.isStrip(item.getSizeId())) {
                        if (designId != null) continue;
                        List<String> keys = mapper.readValue(item.getS3Keys() == null ? "[]" : item.getS3Keys(), new TypeReference<List<String>>() {});
                        int index = 0;
                        for (String key : keys) {
                            byte[] bytes = storage.download(key); total = checkedSize(total, bytes);
                            entry(zip, root + (++index) + "-" + safe(key.substring(key.lastIndexOf('/') + 1)), bytes);
                        }
                        continue;
                    }
                    eligible(order, item);
                    var state = StripDesigns.read(item, mapper);
                    for (var design : state.getDesigns()) {
                        if (designId != null && !design.getId().equals(designId)) continue;
                        foundDesign = true;
                        String folder = root + "Design-" + safe(design.getLabel()) + "__" + design.getCopies() + "-copies/";
                        summary.append(csv(order.getOrderNumber(), item.getId(), item.getSizeId(), design.getId(), design.getLabel(), design.getRevision(), design.getCopies()));
                        for (var slot : design.getSlots().stream().sorted(Comparator.comparingInt(StripDesignState.Slot::getPosition)).toList()) {
                            var asset = state.getAssets().stream().filter(a -> a.getId().equals(slot.getAssetId())).findFirst().orElseThrow();
                            byte[] bytes = source(asset); total = checkedSize(total, bytes);
                            String filename = folder + "slot-" + slot.getPosition() + extension(asset);
                            entry(zip, filename, bytes);
                            manifest.append(csv(order.getOrderNumber(), item.getId(), item.getSizeId(), design.getId(), design.getLabel(),
                                    design.getRevision(), design.getCopies(), slot.getPosition(), filename, asset.getId()));
                        }
                    }
                }
                if (!foundDesign) throw new ResourceNotFoundException("Design not found in this order item");
                entry(zip, safe(order.getOrderNumber()) + "/print-instructions.csv", manifest.toString().getBytes(StandardCharsets.UTF_8));
                entry(zip, safe(order.getOrderNumber()) + "/design-summary.csv", summary.toString().getBytes(StandardCharsets.UTF_8));
            }
            long length = Files.size(zipPath);
            InputStream input = new FilterInputStream(Files.newInputStream(zipPath)) {
                @Override public void close() throws IOException { try { super.close(); } finally { Files.deleteIfExists(zipPath); } }
            };
            return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(safe(order.getOrderNumber()) + "_production.zip").build().toString())
                    .contentLength(length).body(new InputStreamResource(input));
        } catch (Exception e) {
            Files.deleteIfExists(zipPath);
            if (e instanceof RuntimeException runtime) throw runtime;
            throw new IOException("Could not export all source images", e);
        }
    }

    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> slot(String id, String itemId, String designId, int position) {
        var order = order(id);
        var item = order.getItems().stream().filter(i -> i.getId().toString().equals(itemId)).findFirst().orElseThrow(() -> new ResourceNotFoundException("Order item not found"));
        eligible(order, item);
        var state = StripDesigns.read(item, mapper);
        var design = state.getDesigns().stream().filter(d -> d.getId().equals(designId)).findFirst().orElseThrow(() -> new ResourceNotFoundException("Design not found"));
        var slot = design.getSlots().stream().filter(s -> s.getPosition() == position).findFirst().orElseThrow(() -> new ResourceNotFoundException("Slot not found"));
        var asset = state.getAssets().stream().filter(a -> a.getId().equals(slot.getAssetId())).findFirst().orElseThrow();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(asset.getMimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("Design-" + safe(design.getLabel()) + "-slot-" + position + extension(asset)).build().toString())
                .body(source(asset));
    }
    private byte[] source(StripDesignState.Asset asset) {
        byte[] bytes = storage.download(asset.getStorageKey());
        if (bytes == null || bytes.length == 0) throw new BadRequestException("A required source image is missing; export was stopped");
        try {
            if (!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(asset.getSha256()))
                throw new BadRequestException("A source image changed; export was stopped");
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        return bytes;
    }
    private long checkedSize(long total, byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw new BadRequestException("A required source image is missing; export was stopped");
        if (total + bytes.length > MAX_EXPORT_BYTES) throw new BadRequestException("Export exceeds 250 MB; download one design at a time");
        return total + bytes.length;
    }
    private static String extension(StripDesignState.Asset asset) {
        return switch (asset.getMimeType()) { case "image/jpeg" -> ".jpg"; case "image/png" -> ".png"; case "image/webp" -> ".webp"; default -> throw new BadRequestException("Unsupported stored image type"); };
    }
    private static String safe(String text) { return text.replaceAll("[^a-zA-Z0-9_-]", "_"); }
    private static String csv(Object... values) {
        return Arrays.stream(values).map(String::valueOf).map(v -> {
            if (v.matches("^[=+@\\-\\t\\r].*")) v = "'" + v;
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }).collect(java.util.stream.Collectors.joining(",")) + "\r\n";
    }
    private static void entry(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(name)); zip.write(bytes); zip.closeEntry();
    }
}
