package com.polaroid.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaroid.dto.request.StripDesignRequest;
import com.polaroid.exception.BadRequestException;
import com.polaroid.model.OrderItem;
import com.polaroid.model.StripDesignState;
import com.polaroid.storage.StorageService;
import java.util.*;

public final class StripDesigns {
    private StripDesigns() {}
    public static boolean isStrip(String code) { return "strip-3".equals(code) || "strip-4".equals(code); }
    public static StripDesignState reserve(String code, int quantity, StripDesignRequest request) {
        if (!isStrip(code)) throw new BadRequestException("stripDesign is supported only for strip-3 and strip-4");
        if (request == null || !Integer.valueOf(1).equals(request.getSchemaVersion()))
            throw new BadRequestException("Strip orders require stripDesign schemaVersion 1");
        if (request.getAssets() == null || request.getAssets().isEmpty() || request.getAssets().size() > 400
                || request.getDesigns() == null || request.getDesigns().isEmpty()
                || request.getDesigns().size() > Math.min(quantity, 100))
            throw new BadRequestException("Use 1–100 strip designs and 1–400 referenced assets within the purchased quantity");
        var state = new StripDesignState();
        state.setSlotCount("strip-3".equals(code) ? 3 : 4);
        Map<String, String> ids = new LinkedHashMap<>();
        for (var input : request.getAssets()) {
            if (input == null || !validReference(input.getClientAssetId()) || ids.containsKey(input.getClientAssetId()))
                throw new BadRequestException("Asset references must be unique, 1–64 letters, numbers, underscores or hyphens");
            var asset = new StripDesignState.Asset();
            asset.setId(UUID.randomUUID().toString()); asset.setClientAssetId(input.getClientAssetId());
            ids.put(asset.getClientAssetId(), asset.getId()); state.getAssets().add(asset);
        }
        Set<String> designs = new HashSet<>();
        Set<String> referenced = new HashSet<>();
        long copies = 0;
        for (var input : request.getDesigns()) {
            if (input == null || !validReference(input.getClientDesignId()) || !designs.add(input.getClientDesignId())
                    || input.getCopies() == null || input.getCopies() <= 0)
                throw new BadRequestException("Design references must be unique and copies must be positive integers");
            if (input.getSlots() == null || input.getSlots().size() != state.getSlotCount())
                throw new BadRequestException("Each design requires exactly " + state.getSlotCount() + " numbered slots");
            var design = new StripDesignState.Design();
            design.setId(UUID.randomUUID().toString()); design.setClientDesignId(input.getClientDesignId());
            design.setLabel(label(state.getDesigns().size())); design.setCopies(input.getCopies());
            Set<Integer> positions = new HashSet<>();
            for (var source : input.getSlots()) {
                if (source == null || source.getPosition() == null || source.getPosition() < 1
                        || source.getPosition() > state.getSlotCount() || !positions.add(source.getPosition())
                        || !ids.containsKey(source.getClientAssetId()))
                    throw new BadRequestException("Slots must have unique positions 1.." + state.getSlotCount() + " and reference declared assets in this item");
                var slot = new StripDesignState.Slot(); slot.setPosition(source.getPosition());
                slot.setAssetId(ids.get(source.getClientAssetId())); design.getSlots().add(slot);
                referenced.add(source.getClientAssetId());
            }
            design.getSlots().sort(Comparator.comparingInt(StripDesignState.Slot::getPosition));
            copies += input.getCopies(); state.getDesigns().add(design);
        }
        if (copies != quantity) throw new BadRequestException("Design copies must total exactly " + quantity + " physical prints");
        if (!referenced.equals(ids.keySet())) throw new BadRequestException("Every declared asset must be assigned to a slot");
        return state;
    }
    private static boolean validReference(String value) { return value != null && value.matches("[a-zA-Z0-9_-]{1,64}"); }
    private static String label(int index) {
        String result = "";
        for (int n = index + 1; n > 0; n = (n - 1) / 26) result = (char) ('A' + (n - 1) % 26) + result;
        return result;
    }
    public static StripDesignState read(OrderItem item, ObjectMapper mapper) {
        if (item.getStripDesign() == null) return null;
        try { return mapper.readValue(item.getStripDesign(), StripDesignState.class); }
        catch (Exception e) { throw new IllegalStateException("Invalid strip design metadata", e); }
    }
    public static void write(OrderItem item, StripDesignState state, ObjectMapper mapper) {
        try { item.setStripDesign(mapper.writeValueAsString(state)); }
        catch (Exception e) { throw new IllegalStateException("Could not save strip designs", e); }
    }
    public static int uploaded(StripDesignState state) {
        return (int) state.getAssets().stream().filter(a -> "uploaded".equals(a.getStatus()) && a.getStorageKey() != null).count();
    }
    public static boolean ready(OrderItem item, ObjectMapper mapper) {
        try {
            var state = read(item, mapper);
            if (state == null || state.getSchemaVersion() != 1 || state.getSlotCount() != ("strip-3".equals(item.getSizeId()) ? 3 : 4)
                    || state.getAssets().isEmpty() || state.getDesigns().isEmpty() || uploaded(state) != state.getAssets().size()) return false;
            Set<String> ids = new HashSet<>();
            for (var a : state.getAssets()) if (a.getId() == null || !ids.add(a.getId()) || a.getSha256() == null) return false;
            Set<String> referenced = new HashSet<>(); long copies = 0;
            for (var design : state.getDesigns()) {
                if (design.getCopies() <= 0 || design.getSlots().size() != state.getSlotCount()) return false;
                Set<Integer> positions = new HashSet<>();
                for (var slot : design.getSlots()) {
                    if (!ids.contains(slot.getAssetId()) || slot.getPosition() < 1 || slot.getPosition() > state.getSlotCount() || !positions.add(slot.getPosition())) return false;
                    referenced.add(slot.getAssetId());
                }
                copies += design.getCopies();
            }
            return copies == item.getQuantity() && referenced.equals(ids);
        } catch (Exception e) { return false; }
    }
    public static Map<String, Object> view(OrderItem item, ObjectMapper mapper, StorageService storage) {
        var state = read(item, mapper);
        if (state == null) return null;
        List<Map<String, Object>> assets = new ArrayList<>();
        for (var asset : state.getAssets()) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("id", asset.getId()); view.put("clientAssetId", asset.getClientAssetId()); view.put("status", asset.getStatus());
            String url = "uploaded".equals(asset.getStatus()) && asset.getStorageKey() != null ? storage.getSignedUrl(asset.getStorageKey(), 300) : null;
            view.put("previewUrl", url); view.put("downloadUrl", url); view.put("mimeType", asset.getMimeType());
            view.put("width", asset.getWidth()); view.put("height", asset.getHeight());
            view.put("orientationNormalization", asset.getOrientationNormalization()); view.put("originalFilename", asset.getOriginalFilename());
            assets.add(view);
        }
        return Map.of("schemaVersion", state.getSchemaVersion(), "slotCount", state.getSlotCount(), "assets", assets, "designs", state.getDesigns());
    }
}
